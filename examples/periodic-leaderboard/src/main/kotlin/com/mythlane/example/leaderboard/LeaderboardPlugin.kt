package com.mythlane.example.leaderboard

import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.mythlane.async.Async
import com.mythlane.async.ecs.ComponentRegistry
import com.mythlane.async.ecs.read
import com.mythlane.async.binding.handle
import com.mythlane.async.binding.installAsync
import com.mythlane.async.binding.pluginScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Demonstrates: `pluginScope(this).launch { while (isActive) { delay(…); … } }`
 * for a periodic task, plus parallel `read<T>` via `async { … }.awaitAll()`.
 *
 * SDK-side wiring of `onlinePlayers()` and the component type is left as `TODO`.
 */
class LeaderboardPlugin(init: JavaPluginInit) : JavaPlugin(init) {

    override fun start() {
        installAsync()
        ComponentRegistry.register<PlayerStats>(PlayerStatsBinding.componentType())

        pluginScope(this).launch {
            while (isActive) {
                delay(60.seconds)
                runCatching { recompute() }
            }
        }
    }

    override fun shutdown() {
        Async.shutdown()
    }

    private suspend fun recompute() = coroutineScope {
        val players: List<Player> = onlinePlayers()
        players.map { p ->
            async {
                p to read<PlayerStats, Int>(p.handle()) { level }
            }
        }.awaitAll()
            .sortedByDescending { it.second }
            .take(5)
            // .also { broadcast(...) } — wire to your messaging API
    }

    private fun onlinePlayers(): List<Player> = TODO("Return Players from your dev server (e.g. HytaleServer.get().worlds...).")
}

/** Stub component shape — replace with your real `Component<EntityStore>` subclass. */
class PlayerStats { var level: Int = 1 }

object PlayerStatsBinding {
    fun componentType(): Any = TODO("Return EntityStore.REGISTRY.register(PlayerStats::class.java, ...) result here.")
}
