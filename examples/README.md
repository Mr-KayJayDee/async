# Examples

Four runnable plugin sketches built as Gradle composite builds — they
consume the in-development library directly via `includeBuild("../..")`,
so editing the lib and rebuilding an example picks up the change without
publishing.

| Example | Pattern |
|---|---|
| [`player-load/`](player-load/) | Load player JSON on `PlayerReadyEvent`, mutate a component on the world thread. |
| [`async-moderation/`](async-moderation/) | `IAsyncEvent` (`PlayerChatEvent`) bridged to coroutines, off-thread check, conditional `modify` + event cancel. |
| [`periodic-leaderboard/`](periodic-leaderboard/) | Plugin-scoped periodic loop with parallel `read` over players. |
| [`bounty-board/`](bounty-board/) | The kitchen sink — exercises every public Async primitive. |

## Build any of them

```bash
cd examples/<name>
./gradlew shadowJar
# → build/libs/<name>.jar
```

Drop the JAR in your dev server's `mods/`.

## How the composite wiring works

Each example's `settings.gradle.kts` does:

```kotlin
includeBuild("../..") {
    dependencySubstitution {
        substitute(module("com.mythlane:async"))
            .using(project(":dist"))
    }
}
```

So `implementation("com.mythlane:async:0.1.0-SNAPSHOT")` resolves to the
local `:dist` project — no Maven publication required during dev.

## What's stubbed

Each example contains `TODO()` markers in two places:

1. **Custom `Component<EntityStore>` registration.** Declaring `Wallet`,
   `PlayerStats`, etc. on `EntityStore.REGISTRY` is plugin-specific and not
   part of what Async does. Async only consumes the resulting `ComponentType`
   via `ComponentRegistry.register<T>(...)`.

2. **Online-player accessors** like `onlinePlayerRefs()` and
   `broadcastToAllWorlds(...)`. These iterate `Universe.get()` /
   `world.players` in whatever shape your plugin needs.

Wire those to your dev server and the examples become fully runnable. The
Async-side calls (`installAsync()`, `playerScope`, `modify<T>`,
`PlayerRef.toEntityHandle()`, etc.) are accurate as-shipped.
