package com.mythlane.async

import com.mythlane.async.scope.PlayerScopes
import com.mythlane.async.scope.PluginScopes
import com.mythlane.async.scope.WorldScopes

/**
 * Library-wide entry point. Currently exposes a single shutdown helper that
 * cancels every coroutine scope tracked by Async — call from your
 * plugin's `shutdown()`.
 *
 * Disconnect / world-unload wireup lives in the `:hytale` binding module
 * (`installAsync()`), not here, so `:core` stays free of Hytale imports.
 *
 * @ThreadSafe
 */
object Async {
    /** Cancels all player, world, and plugin scopes. Idempotent. */
    fun shutdown() {
        PlayerScopes.cancelAll()
        WorldScopes.cancelAll()
        PluginScopes.cancelAll()
    }
}
