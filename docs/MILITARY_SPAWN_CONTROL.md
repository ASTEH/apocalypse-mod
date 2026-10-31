# TaCZ: Simple Enemy military event frequency

Aftermath reduces how often **TaCZ: Simple Enemy** creates Russian and US dynamic encounters. This integration is optional and does not add a compile-time or runtime dependency on Simple Enemy.

The modpack log confirms the installed mod ID is `simpleenemymod`. Its upstream 1.21.1 source defines these unit IDs:

- `simpleenemymod:ruunit` — Russian unit.
- `simpleenemymod:usunit` — US unit.
- `simpleenemymod:pmcunit` — recruitable PMC/allied unit; deliberately unaffected.

The separate `tacznpcs` mod and its generic NPC entity are also unaffected.

## What is controlled

Simple Enemy has three dynamic world events that create military groups:

- `military_patrol` — Russian or US patrol.
- `far_combat` — distant firefight between the two factions.
- `cave_extraction` — a military group appearing underground.

Aftermath uses reflection to access Simple Enemy's per-player `EventProbabilityData`. It clamps the current saved chance for these three event IDs to a fraction of each event's own configured base chance.

This changes the probability of the event before Simple Enemy creates its squad. It does not remove individual soldiers after they have spawned, so squads remain complete.

## Default balance

Server configuration:

```toml
[features]
enableMilitarySpawnControl = true
militaryEventChanceMultiplier = 0.15
```

`militaryEventChanceMultiplier` is the fraction of Simple Enemy's normal event chance to retain:

- `0.15` = 15% of the normal chance.
- `0.0` = dynamic military events disabled.
- `1.0` = unchanged Simple Enemy event chance.

For example, if the installed Simple Enemy config uses its upstream defaults, the effective maximum chances become approximately:

| Event | Simple Enemy base chance | Aftermath default |
|---|---:|---:|
| `military_patrol` | 10% | 1.5% |
| `far_combat` | 8% | 1.2% |
| `cave_extraction` | 2% | 0.3% |

When available, Aftermath reads the live base values from Simple Enemy's `EventSpawnConfig`, so custom values in that mod's server config are respected. If those fields are unavailable in a future version, Aftermath falls back to the known upstream defaults.

Simple Enemy increases its per-player event chance after unsuccessful checks. Aftermath re-applies the cap once per second, preventing the accumulated chance from growing above the configured fraction.

## What is not affected

- Recruited or otherwise manually created `simpleenemymod:pmcunit` allies.
- TacZ NPCs from the separate `tacznpcs` mod.
- Existing soldiers already saved in the world.
- Spawn eggs and command-created entities that do not use Simple Enemy's dynamic event probability data.
- Aftermath's zombie variants, hostile-spawn filtering, or spawner removal.

The integration is active only when the server has `simpleenemymod` installed and `features.enableMilitarySpawnControl` is enabled.

## Limitations

This stage reduces the frequency of the three dynamic Simple Enemy encounters. It does not move those encounters to military bases or add structure ownership/garrison logic. That would require a separate verified integration with the world structures or Simple Enemy's event-position logic.

If Simple Enemy changes its internal `EventProbabilityData` API, Aftermath logs a warning and disables this optional bridge rather than preventing the game from loading.
