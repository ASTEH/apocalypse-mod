# Zombie wooden-door opening (Stage 26)

Stage26 adds an optional, non-destructive way for Aftermath-tagged zombies to pass through wooden doors.

## Server config

In the server config:

```toml
[features]
enableZombieDoorOpening = false
```

Set it to `true` to enable the behavior. It is intentionally off by default. Restart the server/world after changing the setting so the goal is installed for newly loaded zombie entities.

## Eligible mobs

Only entities in the `aftermath:horde_members` entity type tag are considered. The default tag contains:

- `minecraft:zombie`
- `minecraft:husk`
- `minecraft:drowned`

Datapacks can extend the tag with compatible zombie-like mobs. An entity must use `GroundPathNavigation`; unsupported custom navigation types are left untouched.

## Behavior and safety

- Uses Minecraft's vanilla `OpenDoorGoal` and ground-navigation door flags.
- A tagged zombie can open a wooden door when its path takes it through that doorway.
- Doors are left open after the mob passes.
- No door is destroyed; no block drops, block breaking, explosions or replacement blocks are produced.
- Iron doors and doors outside vanilla's wooden-door interaction behavior are not intentionally bypassed.
- Existing vanilla and third-party goals are not cleared or replaced.
- No chunk is force-loaded and no world-wide per-tick entity scan is introduced.

This feature does not use `enableBlockBreaking` and does not activate the retained `can_break_doors` day-progression field. Destructive door breaking is a separate future feature and remains disabled.

## Acceptance tests

1. With the default config, a zombie does not receive Aftermath door-opening behavior.
2. Enable `enableZombieDoorOpening` and restart the world/server.
3. Lead a vanilla zombie, husk and drowned toward a closed wooden door with a valid path through it; each should open the door and continue through.
4. Confirm the door remains open after the mob passes.
5. Confirm an iron door stays closed.
6. Confirm skeletons and unrelated mobs do not gain the behavior.
7. Confirm no door is destroyed and no door item drops.
8. Confirm existing combat, horde and third-party AI goals still operate.
9. Confirm a dedicated server starts with the feature both disabled and enabled.
