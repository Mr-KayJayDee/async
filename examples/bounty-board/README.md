# bounty-board

The kitchen-sink example. A PvP bounty system that exercises every public
Async v0.1 primitive in one realistic plugin.

Players post bounties on each other (paying gold to put a price on a head).
On payout, the killer collects. A periodic public broadcast lists the top 5
active bounties; an optional Discord webhook fires on placements and
payouts; in-memory state writes to disk on shutdown (load on boot left to your plugin).

## What it shows

| Primitive | Where |
|---|---|
| `installAsync()` | `start()` |
| `Async.shutdown()` | `shutdown()` |
| `ComponentRegistry.register<T>(...)` | `start()`, twice |
| `playerScope(player)` | `registerJoinHook` |
| `pluginScope(this)` | chat handlers, broadcast loop, shutdown save |
| `worldScope(world)` | `startWorldDecayForKnownWorlds` |
| `WorldScopes.cancel(uuid)` | `onWorldUnload` |
| `Player.handle()` / `PlayerRef.toEntityHandle()` | every `modify` / `read` |
| `withContext(AsyncDispatchers.HytaleIO)` | webhook + persistence |
| `delay(...)` (HytaleScheduled) | broadcast and decay loops |
| `read<T, R>` strict | `handlePayout` |
| `readOrNull<T, R>` | join, broadcast, wallet query |
| `modify<T>` Unit | bounty append, decay, payout |
| `modify<T, R>` returning Boolean | atomic gold deduction |
| `withTimeout(...)` | guards the world-death race in the broadcast loop |
| `ComponentNotFoundException` handling | `handlePayout` |
| `WorldClosedException` handling | `notifyDiscord` |
| `pluginScope.future { }` | every chat command |
| Parallel `read` via `async`/`awaitAll` | `broadcastTop5` |

## Commands

| Chat | Effect |
|---|---|
| `!bounty?` | Show your wallet. |
| `!bounty <player> <amount>` | Pay gold to put a bounty on someone. |
| `!payout <player>` | Admin demo: collect bounties from a target (stand-in for a kill hook — Hytale's v0.1 SDK doesn't expose `PlayerDeathEvent`). |

The `!` prefix matters: Hytale's `CommandManager` swallows every `/`-prefixed
message before `PlayerChatEvent` fires, so this example uses `!` to flow
through chat normally.

## Build

```bash
./gradlew shadowJar
# → build/libs/bounty-board.jar
```

## Run

1. Drop the JAR in `mods/`.
2. Wire the SDK stubs in `BountyPlugin.kt`:
   - `onlinePlayerRefs()`, `onlinePlayerRefsIn(world)`, `knownWorlds()`,
     `broadcastToAllWorlds(text)` — your dev server's accessors.
   - `WalletBinding.componentType()`, `BountyStateBinding.componentType()` —
     register `Wallet` and `BountyState` on `EntityStore.REGISTRY` and
     return the result.
3. Optionally set `BOUNTY_WEBHOOK_URL` in the server's env for Discord
   notifications.
4. In game: `!bounty <other-player> 100` places, `!payout <other-player>`
   collects, the top-5 broadcast appears every 60s.

The persistence layer (`BountyRepo`) is intentionally a one-line JSON
serializer for the demo. Real plugins should use `kotlinx.serialization` or
a database — the load-bearing pattern is
`withContext(HytaleIO) { Files.writeString(...) }`, not the JSON shape.
