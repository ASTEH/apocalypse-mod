# Player-aware horde formation

Stage22 adds an optional ring formation to the existing noise and horde system.

## How it works

When a noise event is emitted by a player (for example, a TaCZ gunshot or `/aftermath noise test`), Aftermath remembers that player's UUID for the temporary group order. If horde behavior is enabled and a matching leader archetype is loaded, the group receives evenly distributed approach positions around the player's current position.

The leader and followers therefore approach from different directions instead of all navigating to the exact same coordinate. Positions are recalculated using the existing `aiRecalcIntervalTicks` interval. The ring radius starts at `hordeSurroundRadius` (default 4 blocks) and scales gently with group size, capped at twice the configured base radius.

Once a zombie reaches its assigned position, its movement goal stops issuing paths. Vanilla and third-party attack/target goals remain installed and can take over when their own conditions are met. Aftermath does not replace another mod's goals.

If a noise event has no online player source, the previous behavior remains: a group leader and followers move toward the fixed noise position, with followers tracking the leader. A non-grouped zombie simply investigates the fixed noise position.

## Safety and performance

- Only already-loaded, alive entities matching the event's configured target tag are considered.
- Noise does not spawn zombies and does not force-load chunks.
- Group size is still bounded by the archetype's `max_followers`, the current day stage, and `maxHordeSize`.
- Path requests remain subject to the per-dimension `maxPathRequestsPerTick` budget.
- No existing goal selector is cleared or replaced.

## Enabling for a test

Horde behavior remains disabled by default until the formation has been tested in the target modpack.

An operator can temporarily enable it with:

```text
/aftermath horde enable
/aftermath horde status
```

Return to the server config value with:

```text
/aftermath horde reset
```

Or temporarily disable it with `/aftermath horde disable`.

Server setting:

```toml
[noise_and_hordes]
hordeSurroundRadius = 4.0
```

The base radius is clamped to 2–12 blocks. Changing `enableHordeBehavior` in the server config still controls the default after restart; the command override is in-memory only.
