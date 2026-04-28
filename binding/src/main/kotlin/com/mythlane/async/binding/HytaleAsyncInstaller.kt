package com.mythlane.async.binding

import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent
import com.hypixel.hytale.server.core.plugin.JavaPlugin
import com.mythlane.async.scope.PlayerScopes

/**
 * Wires Async's lifecycle hooks into a plugin. Call once from `start()`.
 *
 * What it installs (v0.1):
 * - On [PlayerDisconnectEvent]: cancels the player's scope via [PlayerScopes.cancel].
 *
 * What it does NOT install:
 * - **World unload cancellation.** The Hytale API does not currently expose a
 *   public world-unload event. Cancel [com.mythlane.async.scope.WorldScopes]
 *   manually from your shutdown / world-management code until that ships.
 *
 * Pair with [com.mythlane.async.Async.shutdown] in your plugin's
 * `shutdown()` to drain every remaining scope.
 */
fun JavaPlugin.installAsync() {
    eventRegistry.register(PlayerDisconnectEvent::class.java) { event ->
        PlayerScopes.cancel(event.playerRef.uuid)
    }
}
