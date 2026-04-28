package com.mythlane.async.dispatchers

import java.util.UUID

/**
 * Minimal abstraction over the bits of `com.hypixel.hytale.api.world.World` that
 * `Dispatchers.World` actually needs. Tests substitute a single-thread executor;
 * production wires this to `world.execute(Runnable)` and `world.isAlive()`.
 *
 * Keeping this as a SAM-friendly interface (not directly the Hytale World class)
 * means `:core` does NOT have a hard `compileOnly` dependency on the Hytale jar
 * for its core primitives — easier testing, cleaner module boundary.
 *
 * @see com.mythlane.async.dispatchers.AsyncDispatchers.World
 */
interface WorldExecutor {
    val worldId: UUID
    fun isAlive(): Boolean
    fun execute(task: Runnable)
}
