# periodic-leaderboard

Recomputes a top-5 leaderboard every minute by reading `PlayerStats.level`
from every online player in parallel, sorting, and broadcasting.

## What it shows

- `pluginScope(this).launch { while (isActive) { delay(60.seconds); … } }` —
  a plugin-lifetime periodic task. Cancels cleanly on shutdown via
  `Async.shutdown()`.
- Parallel `read` over players via `async { … }.awaitAll()`. Each `read`
  switches to its player's owning world dispatcher independently — reads on
  different worlds run concurrently, reads on the same world serialize.
- `runCatching { … }` around the body so a single bad read doesn't kill the
  loop.

This is the shape for any plugin-wide background recompute: leaderboards,
periodic backups of derived state, quest sync, etc.

## Build

```bash
./gradlew shadowJar
# → build/libs/periodic-leaderboard.jar
```

## Run

1. Drop in `mods/`, wire `PlayerStatsBinding.componentType()` and
   `onlinePlayers()`.
2. For a faster demo while testing, change `delay(60.seconds)` to
   `delay(5.seconds)` and rebuild.
3. Connect 2+ players. The top-5 logs every loop tick.

If you want **per-world** leaderboards instead, swap `pluginScope(this)` for
`worldScope(world)` and run one loop per world. Cancellation flows through
`WorldScopes.cancel(uuid)`.
