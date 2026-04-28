# `:ecs`

The component DSL. Suspending functions that take an `EntityHandle`, switch
to the entity's world thread, and return cleanly. No Hytale imports.

## Setup

Once per component type, at plugin start:

```kotlin
// Register each component type once, with whatever ComponentType<EntityStore, T>
// your EntityStore.REGISTRY.register(...) call returned at SDK setup time.
ComponentRegistry.register<PlayerStats>(yourPlayerStatsComponentType)
```

The registry is a `ConcurrentHashMap<KClass<*>, Any>` — lookup on the hot
path is one read, no reflection. The opaque `Any` key is whatever Hytale's
`ComponentType<EntityStore, T>` resolves to at runtime.

## DSL

```kotlin
read<T, R>(entity)        { … }   // strict — throws ComponentNotFoundException if missing
readOrNull<T, R>(entity)  { … }   // returns null instead

modify<T>(entity)         { … }   // mutate, no return
modify<T, R>(entity)      { … }   // mutate, return a value (Boolean for "did it work?", etc.)
```

Every entry switches dispatcher to `AsyncDispatchers.World(entity.world)`,
runs the block on the world thread, suspends until done, returns to the
caller's dispatcher.

## Mutate-in-place

Hytale's `Store` exposes no public `setComponent(ref, value)`. The component
returned by `getComponent` is the live in-store instance — mutating it is
the persistence step. There's no copy and no rollback. If a `modify` block
throws after a partial mutation, the partial state stays.

The practical rule: validate first, mutate second. Don't treat `modify` as a
transaction; treat it as "best-effort atomic chunk on the world thread".

## EntityHandle

The DSL takes an `EntityHandle` interface — single method, returns the live
component for an opaque type key:

```kotlin
interface EntityHandle {
    val world: WorldExecutor
    fun <T : Any> get(typeKey: Any): T?
}
```

Production wiring lives in `:binding` (`PlayerRef.toEntityHandle()`). Tests
use a one-line `MapEntity` backed by a `ConcurrentHashMap`.

## Tests

`ComponentDslTest` covers the strict/lenient variants, the mutate-and-persist
contract, and the registration error paths. Runs against an in-memory stub
entity — no Hytale dep.
