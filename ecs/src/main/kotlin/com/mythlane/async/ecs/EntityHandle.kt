package com.mythlane.async.ecs

import com.mythlane.async.dispatchers.WorldExecutor

/**
 * Thin abstraction over `Ref<EntityStore>` + its owning `Store<EntityStore>`.
 *
 * Hytale's ECS exposes no public `Store.setComponent(ref, value)`. Components
 * returned by `Store.getComponent` are the **live** in-store instances; mutating
 * one is the persist operation. There is no copy-on-read, and no rollback if
 * the mutation throws after a partial write.
 *
 * That single-method shape is therefore deliberate: read the live component,
 * mutate it on the world thread, done.
 *
 * Production wiring: a one-liner adapter in `:hytale` builds an instance from a
 * `Ref<EntityStore>` and delegates [get] to `store.getComponent(ref, typeKey as ComponentType)`,
 * resolving [world] via `store.getWorld()` (or whichever accessor ships).
 *
 * Tests substitute a `Map<Any, Any>` keyed by opaque component-type tokens.
 *
 * @WorldThreadOnly [get]
 * @AnyThread [world]
 */
interface EntityHandle {
    val world: WorldExecutor

    /** Returns the live component for [typeKey] or null if absent. */
    fun <T : Any> get(typeKey: Any): T?
}
