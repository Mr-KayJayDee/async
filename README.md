# Async

Coroutines for Hytale's per-world ECS. Replaces the noisy
`CompletableFuture.runAsync { world.execute { store.getComponent(…) } }`
pattern with one suspending call.

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.2-7f52ff" alt="Kotlin 2.2"/>
  <img src="https://img.shields.io/badge/JVM-25-orange" alt="JDK 25"/>
  <img src="https://img.shields.io/badge/Gradle-Shadow-green" alt="Gradle Shadow"/>
  <img src="https://img.shields.io/badge/Hytale-2026.03.26-blueviolet" alt="Hytale 2026.03.26"/>
  <img src="https://img.shields.io/badge/v0.1-prerelease-orange" alt="v0.1 prerelease"/>
  <img src="https://img.shields.io/badge/license-MIT-lightgrey" alt="MIT"/>
</p>

---

## The pain it solves

Each Hytale world runs on its own thread. Touch a component from anywhere
else and you get `IllegalStateException: Assert not in thread!`. Touch the
database from the world thread and you freeze every player connected to it.
Plugin code lives wedged between those two failure modes, and the workaround
is the same boilerplate everywhere:

```java
CompletableFuture.runAsync(() -> {
    PlayerData data = database.load(uuid);
    world.execute(() -> {
        Ref<EntityStore> ref = player.getRef();
        Store<EntityStore> store = ref.getStore();
        PlayerStats stats = store.getComponent(ref, PlayerStats.getComponentType());
        stats.setLevel(data.level);
    });
}).exceptionally(t -> { logger.error("load failed", t); return null; });
```

Async collapses both halves into one suspending function:

```kotlin
playerScope(player).launch {
    val data = withContext(AsyncDispatchers.HytaleIO) { database.load(uuid) }
    modify<PlayerStats>(player.handle()) { level = data.level }
}
```

Same thread choreography. Cancellation on disconnect comes for free.

---

## Setup

```kotlin
class MyPlugin(init: JavaPluginInit) : JavaPlugin(init) {
    override fun start() {
        installAsync()
        // Register each component type once, with whatever ComponentType<EntityStore, T>
        // your EntityStore.REGISTRY.register(...) call returned at SDK setup time.
        ComponentRegistry.register<PlayerStats>(yourPlayerStatsComponentType)
    }

    override fun shutdown() {
        Async.shutdown()
    }
}
```

Two lines in `start()`, one in `shutdown()`. From there, `playerScope(player)`,
`worldScope(world)`, `pluginScope(this)` are all fair game from any thread.

---

## API by module

### `:core` — dispatchers and scopes

```kotlin
AsyncDispatchers.World(world)         // world's main thread
AsyncDispatchers.HytaleIO             // bounded pool for blocking I/O
AsyncDispatchers.HytaleScheduled      // backs delay() and withTimeout()

PlayerScopes.of(uuid)
PlayerScopes.cancel(uuid)
PlayerScopes.cancelAll()

WorldScopes.of(worldUuid)             // same shape
PluginScopes.of(plugin)               // identity-keyed

Async.shutdown()                      // cancels everything; idempotent
```

Sealed `AsyncException` hierarchy: `WorldClosedException`,
`NoWorldInContextException`, `ComponentNotFoundException`,
`ComponentTypeNotRegisteredException`.

### `:ecs` — component DSL

```kotlin
// Register each component type once, with the ComponentType your EntityStore
// registered at SDK setup time:
ComponentRegistry.register<PlayerStats>(yourPlayerStatsComponentType)

read<T, R>(entity)       { … }    // throws if component missing
readOrNull<T, R>(entity) { … }    // returns null instead

modify<T>(entity)        { … }    // Unit
modify<T, R>(entity)     { … }    // returns the block's value
```

Every entry switches to the entity's world dispatcher, runs your block on
the world thread, returns to the caller's dispatcher. Mutations on the
component persist in place — there's no `setComponent` and no rollback (see
the threading section).

### `:binding` — Hytale glue

The only module that imports anything from the Hytale SDK.

```kotlin
World.asExecutor(): WorldExecutor

Ref<EntityStore>.toEntityHandle(world: World): EntityHandle
PlayerRef.toEntityHandle(): EntityHandle
Player.handle(): EntityHandle

JavaPlugin.installAsync()             // wires PlayerDisconnectEvent → PlayerScopes.cancel

playerScope(player): CoroutineScope
worldScope(world):  CoroutineScope
pluginScope(plugin): CoroutineScope
```

### `:dist` — what you ship

No code. Bundles `:core + :ecs + :binding` into a single shaded JAR via
`com.gradleup.shadow`. The only artifact a consumer ever drops in `mods/`.

---

## Three real patterns

**Load on join.** `PlayerReadyEvent` → fetch off-thread → mutate on world thread.

```kotlin
eventRegistry.registerGlobal(PlayerReadyEvent::class.java) { event ->
    val player = event.player
    playerScope(player).launch {
        val data = withContext(AsyncDispatchers.HytaleIO) { loadFromDisk(player.uuid) }
        modify<PlayerStats>(player.handle()) {
            level = data.level
            clan = data.clan
        }
    }
}
```

**Async chat moderation.** `IAsyncEvent` bridge via `pluginScope.future { … }`,
HTTP off-thread, mutate-and-cancel inline.

```kotlin
eventRegistry.registerAsyncGlobal(PlayerChatEvent::class.java) { future ->
    future.thenCompose { event ->
        pluginScope(this).future {
            val flagged = withContext(AsyncDispatchers.HytaleIO) {
                moderationApi.check(event.content)
            }
            if (flagged) {
                modify<ModerationStats>(event.sender.toEntityHandle()) { warnings += 1 }
                event.isCancelled = true
            }
            event
        }
    }
}
```

**Periodic leaderboard.** Plugin-scoped loop, parallel reads across players.

```kotlin
pluginScope(this).launch {
    while (isActive) {
        delay(60.seconds)
        val top = onlinePlayers().map { p ->
            async { p to read<PlayerStats, Int>(p.handle()) { level } }
        }.awaitAll().sortedByDescending { it.second }.take(5)
        broadcast(top)
    }
}
```

Each `read` switches to its player's world dispatcher independently — reads
on different worlds happen in parallel, reads on the same world serialize
naturally. Fully runnable versions in [`examples/`](examples/).

---

## Modules

| Module | Hytale SDK dep | Purpose |
|---|---|---|
| `core` | none | Dispatchers, scopes, exceptions. Testable without a server. |
| `ecs` | none | The `read` / `readOrNull` / `modify` DSL. |
| `binding` | `compileOnly` | Hytale-specific glue. The only module that imports `com.hypixel.*`. |
| `dist` | aggregator | Single shaded JAR. |

The split exists so `:core` and `:ecs` stay testable against in-memory stubs
in milliseconds, and so a future Hytale API change only ripples through
`:binding`. **For deployment, always ship `:dist`** — the per-module split
is for code hygiene, not cherry-picking.

---

## Threading model, briefly

Three dispatchers map to three concerns:

| Dispatcher | Backed by | Use for |
|---|---|---|
| `AsyncDispatchers.World(world)` | `world.execute(Runnable)` | component reads, writes |
| `AsyncDispatchers.HytaleIO` | bounded pool, `Runtime.availableProcessors() × 2` | blocking I/O — DB, HTTP, file |
| `AsyncDispatchers.HytaleScheduled` | `ScheduledExecutorService` | backs `delay()` and `withTimeout()` |

**Mutate in place.** Hytale's `Store` exposes no public
`setComponent(ref, value)`. The component returned by `getComponent` is the
live in-store instance, and mutating it is how you persist. There's no
copy-on-read and no rollback if a `modify` block throws halfway through.
Validate before mutating.

**Cancellation.** Coroutines on a player/world/plugin scope are cancelled
atomically when the scope is. `Dispatchers.World` honors cancellation
*before* dispatch — a job cancelled while still in the queue is dropped.
Once a runnable starts on the world thread, it runs to completion. The
world is the single writer; ripping a thread mid-mutation would leave the
store inconsistent.

KDoc on every public symbol carries one of three plain-text tags:
`@ThreadSafe`, `@WorldThreadOnly`, `@AnyThread`. Conventions, not
annotations — zero runtime cost.

---

## Coexistence with Kytale

[Kytale](https://github.com/briarss/Kytale) ships a full Kotlin framework
for Hytale plugins (`KotlinPlugin` base class, Event / Command / Config DSLs).
Async solves a narrower problem — thread-safe ECS access — and works
alongside Kytale or standalone. If you're already on Kytale, drop Async in
just for the component DSL and keep the rest of your stack.

---

## Installing

```bash
./gradlew :dist:shadowJar
cp dist/build/libs/async-*.jar <HytaleServer>/Server/mods/
# Restart the server
```

Requires JDK 25, Gradle 9.4+, Hytale Server `2026.03.26-89796e57b` or newer.

---

## Building from source

```bash
./gradlew build               # all modules + tests
./gradlew :dist:shadowJar     # the shaded jar
```

Tests for `:core` and `:ecs` run against in-memory stubs and don't need a
Hytale server.

Layout for contributors:

- New dispatcher → `core/dispatchers/AsyncDispatchers.kt`
- New scope kind → mirror `PlayerScopes` in `core/scope/`
- New DSL primitive → `ecs/ComponentDsl.kt`. Stay suspending and dispatcher-aware.
- New SDK adapter → `binding/`. Don't import `com.hypixel.*` from `:core` or `:ecs`.

---

## How a call flows

```
       (any thread)
            │
            ▼
   playerScope(player).launch
            │
            │   suspend
            ▼
   withContext(HytaleIO)  ──── blocking I/O, off-thread
            │
            │   suspend
            ▼
   modify<T>(player.handle())  ──── switches to world thread
            │
            ▼
   live component mutation
            │
            ▼
       (returns to caller's dispatcher)
```

---

## Status and known limits

v0.1 ships dispatchers, the three scope registries, the suspending DSL, the
Hytale binding, the shaded JAR, and four example sketches.

Things to know:

- **No public `WorldUnloadEvent`** in the current SDK. Cancel
  `WorldScopes.cancel(uuid)` manually from your world-management code until
  it ships.
- **`PlayerDisconnectEvent` fires twice** on world unload. `PlayerScopes.cancel`
  is idempotent so this is benign — just be aware if you wire your own listener.
- **World-death race**: a small window exists between `world.isAlive()` and
  `world.execute()` where a task can be silently dropped. Wrap long
  world-thread work in `withTimeout(...)` if it matters.

---

## Stack

- Kotlin 2.2.20, target JVM 24, toolchain JDK 25 (will move to JVM 25 once
  Kotlin 2.3 ships).
- kotlinx-coroutines 1.8 (core + jdk8 bridge).
- Gradle 9.4 with version catalog and Kotlin DSL.
- JUnit 5 + Kotest assertions + MockK for tests.
- `com.gradleup.shadow` 9.3 for the fat JAR.
- Hytale Plugin API (`https://maven.hytale.com/release`), `compileOnly` only
  in `:binding`.

---

## Credits

By [Mythlane](https://mythlane.com). Module layout influenced by [Kytale](https://github.com/briarss/Kytale).

---

## License

MIT — see [LICENSE](LICENSE). Free to fork, modify, and use commercially.
