package com.mythlane.example.bounty

import com.hypixel.hytale.server.core.Message
import com.hypixel.hytale.server.core.event.events.player.PlayerChatEvent
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.world.World
import com.mythlane.async.Async
import com.mythlane.async.dispatchers.AsyncDispatchers
import com.mythlane.async.ecs.ComponentRegistry
import com.mythlane.async.ecs.EntityHandle
import com.mythlane.async.ecs.modify
import com.mythlane.async.ecs.read
import com.mythlane.async.ecs.readOrNull
import com.mythlane.async.exception.ComponentNotFoundException
import com.mythlane.async.exception.WorldClosedException
import com.mythlane.async.binding.handle
import com.mythlane.async.binding.installAsync
import com.mythlane.async.binding.playerScope
import com.mythlane.async.binding.pluginScope
import com.mythlane.async.binding.toEntityHandle
import com.mythlane.async.binding.worldScope
import com.mythlane.async.scope.PlayerScopes
import com.mythlane.async.scope.WorldScopes
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * Kitchen-sink example exercising every public Async v0.1 primitive in one
 * realistic plugin. See `examples/bounty-board/README.md` for the
 * feature-by-feature mapping.
 *
 * Chat commands (the `!` prefix avoids Hytale's CommandManager, which
 * intercepts every `/`-prefixed message before PlayerChatEvent fires):
 *  - `!bounty?`                — show your wallet.
 *  - `!bounty <player> <amt>`  — pay `<amt>` gold to put a price on `<player>`.
 *  - `!payout <player>`        — admin demo: collect bounties on `<player>`
 *                                (stand-in for a PvP-kill hook; no public
 *                                PlayerDeathEvent in v0.1 SDK).
 */
class BountyPlugin(init: JavaPluginInit) : JavaPlugin(init) {

    private val webhookUrl: String get() = System.getenv("BOUNTY_WEBHOOK_URL").orEmpty()
    private val stateFile get() = dataDirectory.resolve("bounties.json")
    private val httpClient: HttpClient by lazy { HttpClient.newHttpClient() }

    override fun start() {
        installAsync()
        ComponentRegistry.register<Wallet>(WalletBinding.componentType())
        ComponentRegistry.register<BountyState>(BountyStateBinding.componentType())

        Files.createDirectories(dataDirectory)
        registerJoinHook()
        registerChatCommands()
        startBroadcastLoop()
        startWorldDecayForKnownWorlds()
    }

    override fun shutdown() {
        // Drain the save synchronously before Async.shutdown nukes scopes.
        val saveJob = pluginScope(this).launch {
            withContext(AsyncDispatchers.HytaleIO) {
                Files.writeString(stateFile, BountyRepo.serialize())
            }
        }
        runCatching { runBlocking { saveJob.join() } }
        Async.shutdown()
    }

    // ── (9) playerScope: per-player work, auto-cancelled on disconnect ──────────
    private fun registerJoinHook() {
        eventRegistry.registerGlobal(PlayerReadyEvent::class.java) { event ->
            val player = event.player
            playerScope(player).launch {
                val gold = readOrNull<Wallet, Int>(player.handle()) { gold } ?: 0
                player.sendMessage(text("Welcome. Wallet: $gold gold."))
            }
        }
    }

    // ── (16) IAsyncEvent → coroutine bridge via pluginScope.future ──────────────
    private fun registerChatCommands() {
        eventRegistry.registerAsyncGlobal(PlayerChatEvent::class.java) { future ->
            future.thenCompose { event ->
                pluginScope(this).future {
                    handleChat(event)
                    event
                }
            }
        }
    }

    private suspend fun handleChat(event: PlayerChatEvent) {
        val sender: PlayerRef = event.sender
        val handle = sender.toEntityHandle()
        val msg = event.content.trim()

        when {
            msg == "!bounty?" -> {
                val gold = readOrNull<Wallet, Int>(handle) { gold } ?: 0
                // PlayerRef.sendMessage(Message) confirmed present in Hytale 2026.03.26 SDK.
                sender.sendMessage(text("Wallet: $gold gold."))
                event.isCancelled = true
            }

            msg.startsWith("!bounty ") -> {
                val parts = msg.removePrefix("!bounty ").trim().split(" ", limit = 2)
                val targetName = parts.getOrNull(0).orEmpty()
                val amount = parts.getOrNull(1)?.toIntOrNull() ?: 0
                if (amount <= 0 || targetName.isEmpty()) {
                    sender.sendMessage(text("Usage: !bounty <player> <amount>"))
                    event.isCancelled = true; return
                }
                val target = onlinePlayerRefs().firstOrNull { it.username == targetName }
                if (target == null) {
                    sender.sendMessage(text("No such player online: $targetName"))
                    event.isCancelled = true; return
                }

                // modify with Boolean return — atomic deduct on world thread.
                val deducted = modify<Wallet, Boolean>(handle) {
                    if (gold >= amount) { gold -= amount; true } else false
                }
                if (!deducted) {
                    sender.sendMessage(text("Not enough gold."))
                    event.isCancelled = true; return
                }

                // Unit overload — append the bounty entry on the target.
                modify<BountyState>(target.toEntityHandle()) {
                    bountiesOnMe = bountiesOnMe + Bounty(payerUuid = sender.uuid, amount = amount)
                }

                BountyRepo.recordPlacement(sender.uuid, target.uuid, amount)
                notifyDiscord("${sender.username} placed $amount on ${target.username}")
                sender.sendMessage(text("Bounty placed."))
                event.isCancelled = true
            }

            msg.startsWith("!payout ") -> {
                val targetName = msg.removePrefix("!payout ").trim()
                val victim = onlinePlayerRefs().firstOrNull { it.username == targetName }
                if (victim == null) {
                    sender.sendMessage(text("No such player.")); event.isCancelled = true; return
                }
                val victimHandle = victim.toEntityHandle()

                // strict read with ComponentNotFoundException handling.
                val payout = try {
                    read<BountyState, Int>(victimHandle) { bountiesOnMe.sumOf { it.amount } }
                } catch (_: ComponentNotFoundException) { 0 }

                if (payout == 0) {
                    sender.sendMessage(text("No bounty on ${victim.username}."))
                    event.isCancelled = true; return
                }

                // two atomic mutations on potentially two world threads.
                modify<Wallet>(handle) { gold += payout }
                modify<BountyState>(victimHandle) { bountiesOnMe = emptyList() }
                BountyRepo.recordPayout(victim.uuid, sender.uuid, payout)

                notifyDiscord("${sender.username} collected $payout from ${victim.username}")
                sender.sendMessage(text("Collected $payout gold."))
                event.isCancelled = true
            }
        }
    }

    // ── (10) pluginScope periodic loop + (17) parallel reads ────────────────────
    private fun startBroadcastLoop() {
        pluginScope(this).launch {
            while (isActive) {
                delay(60.seconds)
                runCatching { broadcastTop5() }
            }
        }
    }

    private suspend fun broadcastTop5() = coroutineScope {
        val refs = onlinePlayerRefs()
        val ranked = refs.map { ref ->
            async {
                val total = withTimeout(5.seconds) {  // race mitigation
                    readOrNull<BountyState, Int>(ref.toEntityHandle()) {
                        bountiesOnMe.sumOf { it.amount }
                    } ?: 0
                }
                ref to total
            }
        }.awaitAll()
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(5)

        if (ranked.isNotEmpty()) {
            broadcastToAllWorlds(
                "=== Top bounties ===\n" + ranked.joinToString("\n") { "${it.first.username} — ${it.second}g" }
            )
        }
    }

    // ── (11) worldScope: per-world maintenance task ─────────────────────────────
    private fun startWorldDecayForKnownWorlds() {
        knownWorlds().forEach { world ->
            worldScope(world).launch {
                while (isActive) {
                    delay(60.seconds)
                    if (!world.isAlive) break
                    try {
                        onlinePlayerRefsIn(world).forEach { ref ->
                            runCatching {
                                modify<BountyState>(ref.toEntityHandle()) {
                                    bountiesOnMe = bountiesOnMe
                                        .map { it.copy(amount = (it.amount - 1).coerceAtLeast(0)) }
                                        .filter { it.amount > 0 }
                                }
                            }
                        }
                    } catch (_: WorldClosedException) {
                        // Race: world died between isAlive() check and execute().
                        break
                    }
                }
            }
        }
    }

    /** Manual unload hook — call when your world-management code unloads a world. */
    fun onWorldUnload(worldUuid: UUID) {
        WorldScopes.cancel(worldUuid)
        // PlayerScopes for departed players are handled by installAsync's PlayerDisconnect hook,
        // but if you have orphan UUIDs to clean up explicitly, PlayerScopes.cancel(uuid) works too:
        // PlayerScopes.cancel(some uuid)
    }

    private suspend fun notifyDiscord(message: String) {
        if (webhookUrl.isEmpty()) return
        try {
            withContext(AsyncDispatchers.HytaleIO) {
                httpClient.send(
                    HttpRequest.newBuilder(URI(webhookUrl))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("""{"content":"$message"}"""))
                        .build(),
                    HttpResponse.BodyHandlers.discarding(),
                )
            }
        } catch (_: WorldClosedException) {
            // Server shutting down mid-send.
        } catch (_: Exception) {
            // Webhook failures shouldn't crash gameplay.
        }
    }

    private fun text(s: String): Message = Message.empty().insert(s)

    // ── SDK-side stubs (orthogonal to the Async demo) ─────────────────────
    private fun onlinePlayerRefs(): List<PlayerRef> =
        TODO("Wire to your dev server's online accessor (e.g. iterate HytaleServer.get() worlds → world.players.values).")
    private fun onlinePlayerRefsIn(world: World): List<PlayerRef> =
        TODO("Wire to world.players.values.")
    private fun knownWorlds(): List<World> =
        TODO("Wire to the worlds your plugin cares about.")
    private fun broadcastToAllWorlds(text: String): Unit =
        TODO("Wire to your messaging API (per-world iterate + sendMessage).")
}
