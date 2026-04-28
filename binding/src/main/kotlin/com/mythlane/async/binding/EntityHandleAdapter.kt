package com.mythlane.async.binding

import com.hypixel.hytale.component.ComponentType
import com.hypixel.hytale.component.Ref
import com.hypixel.hytale.server.core.universe.PlayerRef
import com.hypixel.hytale.server.core.universe.Universe
import com.hypixel.hytale.server.core.universe.world.World
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore
import com.mythlane.async.dispatchers.WorldExecutor
import com.mythlane.async.ecs.EntityHandle

/**
 * Adapts a Hytale `Ref<EntityStore>` to the [EntityHandle] abstraction.
 *
 * The [typeKey] passed to [get] is the opaque `Any` token registered via
 * `ComponentRegistry.register<T>(componentType)` — at runtime it must be a
 * `ComponentType<EntityStore, T>`.
 *
 * Resolving the owning [World] from a bare `Ref<EntityStore>` is not possible
 * without context (the runtime store does not expose a typed `getWorld()`).
 * Use [PlayerRef.toEntityHandle] for the player case (we resolve the world via
 * `playerRef.worldUuid` + `HytaleServer.get().getWorld(uuid)`); for arbitrary
 * refs, pass the [World] explicitly via [toEntityHandle].
 */
internal class HytaleEntityHandle(
    private val ref: Ref<EntityStore>,
    override val world: WorldExecutor,
) : EntityHandle {
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> get(typeKey: Any): T? {
        val componentType = typeKey as ComponentType<EntityStore, *>
        return ref.store.getComponent(ref, componentType as ComponentType<EntityStore, Nothing>) as T?
    }
}

/** Wraps this `Ref<EntityStore>` as an [EntityHandle] using the given [world]. */
fun Ref<EntityStore>.toEntityHandle(world: World): EntityHandle =
    HytaleEntityHandle(this, world.asExecutor())

/**
 * Convenience: wraps this player's reference as an [EntityHandle], resolving
 * the owning world via `playerRef.worldUuid`.
 *
 * @throws IllegalStateException if the player is not currently in a world
 *         (e.g. mid-handshake) or the world UUID can't be resolved server-side.
 */
fun PlayerRef.toEntityHandle(): EntityHandle {
    val worldUuid = checkNotNull(worldUuid) { "PlayerRef has no worldUuid (player not in a world?)" }
    val world = checkNotNull(Universe.get().getWorld(worldUuid)) { "World $worldUuid not found" }
    return reference!!.toEntityHandle(world)
}
