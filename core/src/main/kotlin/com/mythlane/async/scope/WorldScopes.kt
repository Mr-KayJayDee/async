package com.mythlane.async.scope

import com.mythlane.async.dispatchers.AsyncDispatchers
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-world [CoroutineScope] registry, keyed by world UUID. Cancelled by the
 * `:hytale` binding on world unload (or manually until the unload event ships).
 *
 * Scopes use [SupervisorJob] so a single child failure does not cascade,
 * default to [AsyncDispatchers.HytaleIO], and log unhandled exceptions.
 *
 * @ThreadSafe
 */
object WorldScopes {
    private val log = LoggerFactory.getLogger(WorldScopes::class.java)
    private val scopes = ConcurrentHashMap<UUID, CoroutineScope>()

    private val handler = CoroutineExceptionHandler { _, throwable ->
        log.error("Unhandled exception in world scope", throwable)
    }

    fun of(worldId: UUID): CoroutineScope =
        scopes.computeIfAbsent(worldId) {
            CoroutineScope(SupervisorJob() + AsyncDispatchers.HytaleIO + handler)
        }

    fun cancel(worldId: UUID) {
        scopes.remove(worldId)?.cancel()
    }

    fun cancelAll() {
        val snapshot = scopes.values.toList()
        scopes.clear()
        snapshot.forEach { it.cancel() }
    }

    internal fun activeCount(): Int = scopes.size
}
