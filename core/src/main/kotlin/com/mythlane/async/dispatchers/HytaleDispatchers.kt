package com.mythlane.async.dispatchers

import com.mythlane.async.exception.WorldClosedException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

/**
 * Coroutine dispatchers tailored for the Hytale threading model.
 *
 * - [World]: confines work to a specific world's main thread.
 * - [HytaleIO]: bounded pool for blocking I/O (DB, HTTP, file).
 * - [HytaleScheduled]: backs `delay()` and timed tasks.
 *
 * @ThreadSafe All members may be accessed from any thread.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
object AsyncDispatchers {

    private val worldDispatchers = ConcurrentHashMap<java.util.UUID, CoroutineDispatcher>()

    /** Default IO parallelism; override via [configureIo]. */
    private const val DEFAULT_IO_PARALLELISM_MULTIPLIER = 2

    @Volatile
    private var ioDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(
        Runtime.getRuntime().availableProcessors() * DEFAULT_IO_PARALLELISM_MULTIPLIER
    )

    @Volatile
    private var scheduled: ScheduledExecutorService = Executors.newScheduledThreadPool(2) { r ->
        Thread(r, "Async-Scheduled-${schedulerCounter.incrementAndGet()}").apply { isDaemon = true }
    }
    private val schedulerCounter = AtomicInteger(0)

    /**
     * Returns a dispatcher that confines coroutines to [world]'s main thread.
     *
     * Each dispatched task is wrapped to honor coroutine cancellation: if the
     * job is cancelled before the world thread picks the task up, it is skipped.
     *
     * @throws WorldClosedException if [world] is no longer alive at dispatch time.
     */
    fun World(world: WorldExecutor): CoroutineDispatcher =
        worldDispatchers.computeIfAbsent(world.worldId) { WorldCoroutineDispatcher(world) }

    /** Bounded IO dispatcher for blocking work. */
    val HytaleIO: CoroutineDispatcher get() = ioDispatcher

    /** Scheduled dispatcher; backs `delay()` and `withTimeout`. */
    val HytaleScheduled: CoroutineDispatcher get() = scheduled.asCoroutineDispatcher()

    /**
     * Override the IO pool. Call once during plugin init if defaults aren't right.
     */
    fun configureIo(parallelism: Int) {
        require(parallelism > 0) { "parallelism must be > 0" }
        ioDispatcher = Dispatchers.IO.limitedParallelism(parallelism)
    }

    /**
     * Override the scheduled pool. Call once during plugin init.
     * Production wiring should pass `HytaleServer.SCHEDULED_EXECUTOR`.
     */
    fun configureScheduled(executor: ScheduledExecutorService) {
        scheduled = executor
    }

    /** Test hook: drop the dispatcher cached for [worldId]. */
    internal fun evictWorld(worldId: java.util.UUID) {
        worldDispatchers.remove(worldId)
    }

    private class WorldCoroutineDispatcher(private val world: WorldExecutor) : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (!world.isAlive()) throw WorldClosedException(world.worldId.toString())
            world.execute(Runnable {
                // Skip if the coroutine was cancelled before the world thread woke up.
                val job = context[kotlinx.coroutines.Job]
                if (job != null && !job.isActive) return@Runnable
                block.run()
            })
        }

        override fun toString(): String = "Dispatchers.World(${world.worldId})"
    }
}
