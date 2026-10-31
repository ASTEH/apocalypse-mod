# Military NPC spawn control

Aftermath can reduce the spawn frequency of selected military NPCs without making the third-party mod that adds them a required dependency.

Policy file: `data/aftermath/world_rules/military_spawn_control.json`

## How entities are selected

The policy matches mobs by one or more selectors:

- `entity_types`: exact registry IDs, recommended once the actual IDs from the installed NPC mods are known.
- `entity_namespaces`: exact mod namespaces. This affects every non-zombie Mob from the listed namespace, so use it carefully.
- `path_keywords`: whole tokens in the entity ID path, such as `soldier`, `russian`, or `usa`.

Zombie-like entities are explicitly excluded from this military filter, even if an entity ID contains one of these words.

The default keyword selectors are intentionally generic because the repository does not currently contain the modpack's exact Russian/US military entity IDs. Add exact IDs after checking the installed mods. If an entity uses a generic ID such as `npc`, add its exact ID or namespace; do not broaden the keyword list unnecessarily.

## Spawn frequency

- With no configured zones, matching entities have an 8% chance to pass the filter in configured dimensions and spawn types.
- When at least one zone is configured, entities outside every zone use `outside_zone_spawn_chance` (1% by default).
- A matching zone uses its `spawn_chance`. The default zone chance is 100%, unless explicitly set otherwise.
- If zones overlap, the highest matching zone chance is used.

This makes military NPCs rare in the general world while allowing normal or elevated rates around specified military bases.

## Example military base zone

Replace the empty `zones` array with an entry like this, changing the coordinates to the centre of a real base in your world:

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

Coordinates are world coordinates. The radius is horizontal, with optional vertical bounds. The example coordinates are illustrative only and are not preconfigured.

## Spawn types

The default policy targets natural, chunk-generation, patrol, structure, event, triggered, mob-summoned, spawner, and reinforcement spawn attempts. Command, spawn-egg, breeding, and conversion actions are not included by default.

The server config option `features.enableMilitarySpawnControl` can disable this filter without changing the datapack.

## Limitations

The repository does not identify the third-party mods or exact entity IDs used for the Russian and American units in the target modpack. The default keyword matching covers common IDs that include a military term, but entities with generic IDs require an explicit selector. The rule filters spawn attempts; it does not delete units that already exist in loaded or saved chunks.
