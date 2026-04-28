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
 * Per-player [CoroutineScope] registry. Scopes are created lazily on first lookup
 * and cancelled atomically when [cancel] is invoked (typically from the
 * `PlayerDisconnectEvent` listener wired by `Async.install`).
 *
 * Each scope uses a [SupervisorJob] so a single child failure does not cascade
 * to siblings, and defaults to [AsyncDispatchers.HytaleIO].
 *
 * @ThreadSafe
 */
object PlayerScopes {
    private val log = LoggerFactory.getLogger(PlayerScopes::class.java)
    private val scopes = ConcurrentHashMap<UUID, CoroutineScope>()

    private val handler = CoroutineExceptionHandler { ctx, throwable ->
        log.error("Unhandled exception in player scope ${ctx[kotlinx.coroutines.CoroutineName]?.name}", throwable)
    }

    /** Get-or-create the scope for [playerId]. */
    fun of(playerId: UUID): CoroutineScope =
        scopes.computeIfAbsent(playerId) {
            CoroutineScope(SupervisorJob() + AsyncDispatchers.HytaleIO + handler)
        }

    /** Cancel and remove the scope for [playerId]; safe no-op if absent. */
    fun cancel(playerId: UUID) {
        scopes.remove(playerId)?.cancel()
    }

    /** Cancel everything; intended for plugin shutdown. */
    fun cancelAll() {
        val snapshot = scopes.values.toList()
        scopes.clear()
        snapshot.forEach { it.cancel() }
    }

    internal fun activeCount(): Int = scopes.size
}
