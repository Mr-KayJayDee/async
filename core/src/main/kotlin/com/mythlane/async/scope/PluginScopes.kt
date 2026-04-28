package com.mythlane.async.scope

import com.mythlane.async.dispatchers.AsyncDispatchers
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.slf4j.LoggerFactory
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Per-plugin [CoroutineScope] registry. Identity-keyed by the plugin instance —
 * two plugins with equal `equals` (rare for plugins) still get distinct scopes,
 * which matches the actual lifecycle ownership.
 *
 * Cancelled in the plugin's `shutdown()`, typically via [com.mythlane.async.Async.shutdown].
 *
 * @ThreadSafe
 */
object PluginScopes {
    private val log = LoggerFactory.getLogger(PluginScopes::class.java)

    // IdentityHashMap because plugins are reference-identity entities; sync wrapper
    // since IdentityHashMap is not concurrent.
    private val scopes: MutableMap<Any, CoroutineScope> =
        Collections.synchronizedMap(IdentityHashMap())

    private val handler = CoroutineExceptionHandler { _, throwable ->
        log.error("Unhandled exception in plugin scope", throwable)
    }

    fun of(plugin: Any): CoroutineScope = synchronized(scopes) {
        scopes.getOrPut(plugin) {
            CoroutineScope(SupervisorJob() + AsyncDispatchers.HytaleIO + handler)
        }
    }

    fun cancel(plugin: Any) {
        val scope = synchronized(scopes) { scopes.remove(plugin) }
        scope?.cancel()
    }

    fun cancelAll() {
        val snapshot = synchronized(scopes) {
            val copy = scopes.values.toList()
            scopes.clear()
            copy
        }
        snapshot.forEach { it.cancel() }
    }

    internal fun activeCount(): Int = synchronized(scopes) { scopes.size }
}
