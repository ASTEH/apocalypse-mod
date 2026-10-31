# Zombie variants

Aftermath loads server-side variant definitions from
`data/<namespace>/zombie_variants/<name>.json`.

Each definition gets a relative weight among the variants whose entity tag,
spawn type, dimension, biome, time-of-day and light conditions match the
same spawn. Ranges are rolled separately for each spawned entity.

| Profile | Weight | Spawn condition | Gameplay |
|---|---:|---|---|
| Slow | 75 | Day or night | Slow movement, above-average durability and shorter detection |
| Fast | 20 | Day or night | High movement speed, less health, longer sight/hearing and more damage |
| Volatile | 5 | Night | Fast, durable and high damage; longest sight/hearing |
| Brute | 3 | Night | Very durable, slow, high damage and strong knockback resistance |

The data fields are defined in `docs/zombie-variant.schema.json`. Numeric
fields accept either a fixed value or an object with `min` and `max`.

- `attributes.max_health`: maximum health.
- `attributes.movement_speed`: base movement speed.
- `attributes.attack_damage`: base melee damage.
- `attributes.follow_range`: fallback target range.
- `attributes.knockback_resistance`: 0–1.
- `senses.vision_range`: sets the entity's Follow Range attribute.
- `senses.hearing_range`: limits the entity's range for Aftermath's native
  noise targeting. Other mods which independently emit sound events can use
  their own awareness logic.
- `spawn.time_of_day`: `any`, `day` or `night`.
- `spawn.spawn_types`: lowercase `MobSpawnType` names. Defaults to
  `natural` and `chunk_generation`.
- Empty `dimensions` and `biomes` arrays mean all dimensions/biomes.

New zombie-like hostiles in the Overworld are blocked during daytime by the independent `enableZombieNightOnlySpawning` server feature (enabled by default). Natural, chunk-generation, spawner, structure and event-style spawns are blocked; commands, spawn eggs and conversions remain available. Night is ticks 13000–22999 of the 24000-tick Minecraft day.

Only vanilla zombies are included in the built-in
`aftermath:zombie_variant_targets` tag. Add other entity IDs through a
datapack tag to use these stat profiles. The separate global hostile spawn
policy must also allow those hostile entity types.

Aftermath changes stats and native sound hearing; it does not replace
entity models or add special attack animations. A dedicated zombie content
mod should provide visual/special-attack variants instead of duplicating
those parts inside Aftermath.
