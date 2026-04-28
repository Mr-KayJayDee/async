# `:binding`

The Hytale-specific glue. This is the only module that imports anything from
`com.hypixel.hytale.*` — everything else in the project is plain Kotlin you
can compile without the SDK on the classpath.

## What's in here

```kotlin
World.asExecutor(): WorldExecutor
Ref<EntityStore>.toEntityHandle(world: World): EntityHandle
PlayerRef.toEntityHandle(): EntityHandle
Player.handle(): EntityHandle

JavaPlugin.installAsync()    // wires PlayerDisconnectEvent → PlayerScopes.cancel

playerScope(player): CoroutineScope
worldScope(world):  CoroutineScope
pluginScope(plugin): CoroutineScope
```

Each is a thin delegating wrapper. The interesting code lives in `:core` and
`:ecs`.

## No unit tests

`World` and `WorldConfig` break MockK's bytecode retransformer on JDK 25
(`InternalError: class redefinition failed`). Adding `mockk-agent` plus a JVM
arg flag would let us mock them, but they'd be testing five lines of method
delegation. Not worth the maintenance.

The actual behaviour — dispatcher confinement, scope cancellation, the DSL —
is covered by the `:core` and `:ecs` unit tests, which run in milliseconds
against in-memory stubs.

If you want to verify the binding against a live server, build the shaded
JAR (`./gradlew :dist:shadowJar`), drop it in `mods/`, and call
`installAsync()` from a plugin's `start()`. If `playerScope(player).launch { modify<T>(...) { ... } }`
runs without throwing `Assert not in thread!`, the binding works.

## Known SDK gotchas

- **No `WorldUnloadEvent`.** Until it ships, call `WorldScopes.cancel(uuid)`
  yourself when you unload a world.
- **Race on world death.** If a world dies between an `isAlive()` check and
  the actual `execute()` call, the task is silently dropped by Hytale's task
  queue and the awaiting coroutine never completes. Wrap long world-bound
  work in `withTimeout(...)` if it matters.
