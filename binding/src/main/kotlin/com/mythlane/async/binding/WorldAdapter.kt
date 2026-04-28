package com.mythlane.async.binding

import com.hypixel.hytale.server.core.universe.world.World
import com.mythlane.async.dispatchers.WorldExecutor
import java.util.UUID

/**
 * Adapts a Hytale [World] to the Hytale-free [WorldExecutor] abstraction used
 * by `:core` and `:ecs`.
 *
 * Note: Hytale's `World` does not expose a direct `getUuid()`. The world UUID
 * lives on its config — `world.worldConfig.uuid`. We resolve it once at construction.
 *
 * @ThreadSafe
 */
internal class HytaleWorldExecutor(private val world: World) : WorldExecutor {
    override val worldId: UUID = world.worldConfig.uuid
    override fun isAlive(): Boolean = world.isAlive
    override fun execute(task: Runnable) = world.execute(task)
    override fun toString(): String =
        "HytaleWorldExecutor(${runCatching { world.name }.getOrDefault("?")}, $worldId)"
}

/** Wraps this Hytale [World] as a [WorldExecutor]. */
fun World.asExecutor(): WorldExecutor = HytaleWorldExecutor(this)
