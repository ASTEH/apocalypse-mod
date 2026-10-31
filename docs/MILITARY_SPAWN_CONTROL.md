# Military NPC spawn control

Aftermath reduces the frequency of Russian and US soldier squads from **TaCZ: Simple Enemy** without requiring that mod as a dependency.

Policy file: `data/aftermath/world_rules/military_spawn_control.json`

## Verified target entities

The current target modpack log contains the NeoForge 1.21.1 `simpleenemymod` mod. The upstream Simple Enemy registry uses these entity IDs:

- `simpleenemymod:ruunit` — Russian unit.
- `simpleenemymod:usunit` — US unit.

The PMC/allied unit is deliberately not included, so player-recruited allies are not reduced by this policy. The separate `tacznpcs:npc` entity is not targeted either.

The upstream Simple Enemy event implementation creates squads directly and calls `finalizeSpawn` with `MobSpawnType.EVENT` before adding the entities to the level. For this reason Aftermath filters the event/finalize + entity-join path instead of relying only on natural-spawn position checks.

## Group-level filtering

The policy makes one probability decision for the first matching unit finalized in a level during a game tick. The decision is reused for the rest of that tick's event group. A squad is therefore allowed or rejected as a group instead of being reduced to one or two random survivors.

Only `MobSpawnType.EVENT` is targeted by the default policy. Spawn eggs and command/recruitment paths that use another spawn type remain unaffected.

The default policy does not touch:

- Aftermath's zombie variants.
- PMC/allied units.
- TacZ NPCs from the separate `tacznpcs` mod.
- Existing soldiers already saved in the world.
- Spawn eggs and manually placed units.

## Spawn frequency

- With no configured zones, matching event squads have an **8% chance** to pass the filter.
- If one or more zones are configured, event squads outside all zones use `outside_zone_spawn_chance` (1% by default).
- A squad whose first unit is inside a configured zone uses that zone's `spawn_chance` (100% by default).
- If zones overlap, the highest matching zone chance is used.

This makes squads rare across the general world while allowing normal or elevated rates around specified military facilities.

## Example military base zone

Replace the empty `zones` array with an entry like this, changing coordinates to the centre of a real military base in your world:

```json
"zones": [
  {
    "name": "north_military_base",
    "dimension": "minecraft:overworld",
    "x": 1200,
    "z": -640,
    "radius": 160,
    "min_y": 40,
    "max_y": 120,
    "spawn_chance": 1.0
  }
]
```

Coordinates are world coordinates. Radius is horizontal; vertical bounds are optional. The example coordinates are illustrative only and are not preconfigured. Simple Enemy chooses event locations near players, so the player must be exploring near the configured base for these events to land inside its zone.

## Configuration

- `features.enableMilitarySpawnControl` toggles the Aftermath filter.
- `default_spawn_chance` controls global squad frequency when no zones are configured.
- `outside_zone_spawn_chance` controls frequency outside all zones when at least one zone exists.
- `entity_types` is an exact registry-ID allow-list for targets. Prefer exact IDs over namespaces or path keywords.

## Limitations

This controls Simple Enemy's dynamic event-created squads. It does not relocate event generation to a base, alter the Simple Enemy event cooldowns, delete already spawned units, or modify TacZ NPC spawn tables. Coordinates for actual military facilities must be configured for each world; Aftermath does not guess where a base is located.
