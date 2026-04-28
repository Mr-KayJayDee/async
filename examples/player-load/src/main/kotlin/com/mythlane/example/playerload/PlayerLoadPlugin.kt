package com.mythlane.example.playerload

import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.hypixel.hytale.server.core.plugin.JavaPluginInit
import com.mythlane.async.Async
import com.mythlane.async.dispatchers.AsyncDispatchers
import com.mythlane.async.ecs.ComponentRegistry
import com.mythlane.async.ecs.modify
import com.mythlane.async.binding.handle
import com.mythlane.async.binding.installAsync
import com.mythlane.async.binding.playerScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.util.UUID

/**
 * Demonstrates: `playerScope(...).launch { withContext(HytaleIO) { … }; modify<T> { … } }`.
 *
 * SDK-side wiring (component type registration, the actual `PlayerStats` class)
 * is left as `TODO` — those pieces depend on your dev server's component layout
 * and are orthogonal to what this example demonstrates.
 */
class PlayerLoadPlugin(init: JavaPluginInit) : JavaPlugin(init) {

    override fun start() {
        installAsync()
        ComponentRegistry.register<PlayerStats>(PlayerStatsBinding.componentType())

        val playersDir = dataDirectory.resolve("players").also { Files.createDirectories(it) }

        eventRegistry.registerGlobal(PlayerReadyEvent::class.java) { event ->
            val player = event.player
            playerScope(player).launch {
                val data = withContext(AsyncDispatchers.HytaleIO) {
                    loadFromDisk(playersDir, player.uuid!!)
                }
                modify<PlayerStats>(player.handle()) {
                    level = data.level
                    clan = data.clan
                }
            }
        }
    }

    override fun shutdown() {
        Async.shutdown()
    }

    // Demo-grade JSON parser — use kotlinx.serialization in real plugins.
    private fun loadFromDisk(dir: java.nio.file.Path, uuid: UUID): PlayerData {
        val file = dir.resolve("$uuid.json")
        if (!Files.exists(file)) return PlayerData(level = 1, clan = "")
        val raw = Files.readString(file).trim().removePrefix("{").removeSuffix("}")
        val map = raw.split(",").associate {
            val (k, v) = it.split(":", limit = 2)
            k.trim().trim('"') to v.trim().trim('"')
        }
        return PlayerData(map["level"]?.toIntOrNull() ?: 1, map["clan"].orEmpty())
    }
}

data class PlayerData(val level: Int, val clan: String)

/** Stub component shape — replace with your real `Component<EntityStore>` subclass. */
class PlayerStats {
    var level: Int = 1
    var clan: String = ""
}

/** Stub: returns the registered `ComponentType<EntityStore, PlayerStats>` token from your setup. */
object PlayerStatsBinding {
    fun componentType(): Any = TODO("Return EntityStore.REGISTRY.register(PlayerStats::class.java, ...) result here.")
}
