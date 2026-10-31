# Night-only zombie spawning

Aftermath blocks new zombie-like hostile spawns in the Overworld during daytime. The feature is enabled by default and is independent of the weighted zombie-variant feature.

## Server config

```toml
[features]
enableZombieNightOnlySpawning = true
```

Set the option to `false` to restore daytime spawning without disabling zombie variants, the hostile-spawn policy, or spawner removal.

## Night window

The guard uses the same vanilla day-time window as zombie archetype selection:

- Night: world time 13000 through 22999 (inclusive).
- Day: world time 23000 through 12999, including dawn, daytime and dusk before tick 13000.

The check uses the Overworld's world time. A paused daylight cycle remains paused: if the clock is fixed to daytime, the guard continues to reject new world-generated zombie spawns.

## Spawn paths blocked during daytime

The server cancels spawn finalization for zombie-like hostile mobs with these spawn reasons:

- Natural spawning and chunk generation.
- Monster spawners and trial spawners.
- Structure, event, patrol and triggered spawning.
- Mob-summoned, reinforcement and jockey spawns.

The check uses Aftermath's existing zombie classifier, including the configured horde-member entity tag and supported zombie-like registry names. It does not block unrelated hostile mobs such as skeletons or creepers.

## Spawn paths intentionally preserved

- `/summon` and other command-created entities.
- Spawn eggs and dispenser/manual placement.
- Entity conversions, such as villagers converting into zombie villagers.
- Zombies already present in the world when daytime begins.

The feature prevents new spawn attempts; it does not kill, despawn or teleport existing zombies at sunrise. Existing zombies remain governed by Minecraft and other installed mods.

## Variant system

The existing slow, fast and volatile variants are retained. Stage 21 adds a rare night-only Brute profile with higher health, damage and knockback resistance, but lower movement speed. Its weight is 3 relative to the existing night-eligible profiles; it uses the same JSON data-driven variant loader and does not add a new entity type or model.
