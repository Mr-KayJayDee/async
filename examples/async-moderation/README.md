# async-moderation

Listens to `PlayerChatEvent` (an `IAsyncEvent`), runs a fake 200ms moderation
check off-thread, and on flagged messages bumps a `warnings` counter on the
world thread + cancels the event so it never reaches other players.

## What it shows

- `eventRegistry.registerAsyncGlobal(PlayerChatEvent::class.java) { future -> … }` —
  the global overload. `registerAsync` only matches `IAsyncEvent<Void>`, and
  `PlayerChatEvent` isn't `Void`-keyed; the wrong overload compiles silently
  and never fires.
- `scope.future { … }` from `kotlinx-coroutines-jdk8` — bridges
  `CompletableFuture` ↔ coroutines without `runBlocking`.
- `withContext(AsyncDispatchers.HytaleIO) { … }` — for the simulated HTTP call.
- Conditional `modify<T>(...)` + `event.isCancelled = true` — the canonical
  shape for any moderation/filter handler.

## Build

```bash
./gradlew shadowJar
# → build/libs/async-moderation.jar
```

## Run

1. Drop the JAR in `mods/`, wire `ModerationStatsBinding.componentType()` to
   your registered component.
2. Type a message containing `badword`, `spam`, or `scam` in chat.
3. The message is suppressed; the sender's `ModerationStats.warnings`
   increments on the world thread.

The example uses a private `CoroutineScope` to make the supervisor
relationship explicit. In a real plugin, `pluginScope(this)` is fine and
flows through `Async.shutdown()` cleanly.
