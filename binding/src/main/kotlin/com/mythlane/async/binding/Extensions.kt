package com.mythlane.async.binding

import com.hypixel.hytale.server.core.entity.entities.Player
import com.hypixel.hytale.server.core.universe.world.World
import com.mythlane.async.ecs.EntityHandle
import com.mythlane.async.scope.PlayerScopes
import com.mythlane.async.scope.PluginScopes
import com.mythlane.async.scope.WorldScopes
import kotlinx.coroutines.CoroutineScope

/**
 * Get-or-create the [CoroutineScope] for [player].
 *
 * **Readiness contract.** Call only after the player has reached `PlayerReadyEvent`.
 * Earlier in the connection lifecycle, [Player.uuid] may still be null and this
 * function will NPE. If you need to schedule work before ready, key off the
 * raw UUID via [PlayerScopes.of] once you have one.
 */
fun playerScope(player: Player): CoroutineScope = PlayerScopes.of(player.uuid!!)

/** Get-or-create the [CoroutineScope] for [world]. */
fun worldScope(world: World): CoroutineScope = WorldScopes.of(world.worldConfig.uuid)

/** Get-or-create the [CoroutineScope] for [plugin]. */
fun pluginScope(plugin: Any): CoroutineScope = PluginScopes.of(plugin)

/**
 * Convenience: returns this player's [EntityHandle], suitable for the component DSL.
 *
 * Resolves the owning world via `playerRef.worldUuid` lookup. Same readiness
 * contract as [playerScope] — call only after `PlayerReadyEvent`.
 */
fun Player.handle(): EntityHandle = playerRef!!.toEntityHandle()
