# player-load

Loads `<plugin data dir>/players/<uuid>.json` when a player joins, hydrates a
`PlayerStats` component on the world thread.

## What it shows

- `eventRegistry.registerGlobal(PlayerReadyEvent::class.java) { … }` — the right
  overload for events with a non-`Void` key type.
- `playerScope(player).launch { … }` — coroutine bound to the player. Cancels
  on disconnect, so a slow disk read doesn't outlive the session.
- `withContext(AsyncDispatchers.HytaleIO) { … }` — file I/O off the world thread.
- `modify<PlayerStats>(player.handle()) { … }` — thread-safe component
  mutation. The dispatcher switches happen automatically.

## Build

```bash
./gradlew shadowJar
# → build/libs/player-load.jar
```

## Run

1. Drop the JAR in your dev server's `mods/`.
2. Wire `PlayerStatsBinding.componentType()` — replace the `TODO()` with
   whatever `EntityStore.REGISTRY.register(PlayerStats::class.java, …)` returns
   in your setup.
3. Seed `<server>/data/Mythlane.PlayerLoad/players/<your-uuid>.json`:
   ```json
   {"level":42,"clan":"red"}
   ```
4. Connect. The values land on the live `PlayerStats` component.

The JSON parser in the example is intentionally minimal — use
`kotlinx.serialization` in real plugins.
