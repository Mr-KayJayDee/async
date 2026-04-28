# `:core`

Dispatchers and coroutine scopes. No knowledge of the Hytale SDK — the only
runtime deps are `kotlinx-coroutines` and SLF4J.

## Dispatchers

```kotlin
AsyncDispatchers.World(world)         // confines to a specific world's main thread
AsyncDispatchers.HytaleIO             // bounded pool for blocking I/O
AsyncDispatchers.HytaleScheduled      // backs delay() and withTimeout()

AsyncDispatchers.configureIo(parallelism = 16)
AsyncDispatchers.configureScheduled(myExecutor)
```

`World` takes a `WorldExecutor` interface, not the Hytale `World` class
directly. The `:binding` module wires the real `World` in via
`World.asExecutor()`. Tests substitute single-thread executors keyed by UUID.

## Scopes

Three registries, all `SupervisorJob`-backed (a child failure doesn't kill
siblings) and all defaulting to `HytaleIO`:

```kotlin
PlayerScopes.of(uuid)        // get-or-create
PlayerScopes.cancel(uuid)    // idempotent
PlayerScopes.cancelAll()
PlayerScopes.activeCount()

WorldScopes.of(worldUuid)    // same shape
PluginScopes.of(plugin)      // identity-keyed (IdentityHashMap), not equality
```

`Async.shutdown()` cancels all three registries and is idempotent — call it
from your plugin's `shutdown()`.

## Exceptions

A sealed hierarchy under `AsyncException` so consumers can catch one type if
they want a uniform error path:

- `WorldClosedException` — dispatching to a dead world
- `NoWorldInContextException` — `MainHere` invoked off-context
- `ComponentNotFoundException` — strict `read`/`modify` on a missing component
- `ComponentTypeNotRegisteredException` — DSL used on an unregistered class

## Tests

`SmokeTest` and `ScopesTest` run against stub `WorldExecutor` implementations
backed by `Executors.newSingleThreadExecutor`. No Hytale jar required.
