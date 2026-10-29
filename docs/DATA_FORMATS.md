# Data formats — draft v1

These are Aftermath-owned JSON contracts, not built-in NeoForge schemas. Profession, trait, lesson, TV, noise-event, and horde-archetype files are loaded by server-side reload listeners and validated before use.

## Rules

- IDs use `namespace:path`; owned IDs use `aftermath:`.
- Numeric values must be finite and bounded.
- Unknown effect types are rejected with a resource-specific error.
- Effects are allow-listed declarative operations. JSON never names Java classes or executable scripts.
- Parse into immutable models and swap atomically.

## Profession

```json
{
  "name": "profession.aftermath.medic",
  "description": "profession.aftermath.medic.description",
  "starting_points": 8,
  "allowed_traits": ["aftermath:fit", "aftermath:strong", "aftermath:tough", "aftermath:frail", "aftermath:clumsy", "aftermath:weak"],
  "starting_items": [
    {"item": "minecraft:bread", "count": 4},
    {"item": "minecraft:honey_bottle", "count": 2}
  ]
}
```

## Trait

```json
{
  "name": "trait.aftermath.fit",
  "description": "trait.aftermath.fit.description",
  "category": "positive",
  "point_cost": 3,
  "effects": [{"type": "attribute_multiplier", "target": "movement_speed", "value": 1.08}],
  "conflicts": ["aftermath:frail"]
}
```

Negative traits use `category: "negative"` and a positive `point_credit`. A trait cannot define both `point_cost` and `point_credit`. Server recomputes costs and validates conflicts.

## Weapon class

```json
{
  "item_tag": "aftermath:weapon_classes/shotgun",
  "max_level": 10,
  "base_xp_per_kill": 10,
  "levels": [
    {"level": 2, "damage_multiplier": 1.02},
    {"level": 5, "damage_multiplier": 1.05},
    {"level": 10, "damage_multiplier": 1.10}
  ]
}
```

Tags classify held item classes; they do not prove which weapon caused a projectile. Aftermath resolves projectile owner and class before awarding XP.

## TaCZ gun ID mappings

The optional TaCZ adapter reads the server bullet's public gun ID without importing TaCZ classes. Mappings are loaded from `data/aftermath/firearm_classes/*.json`:

```json
{
  "guns": {
    "tacz:glock_17": "pistol",
    "tacz:m870": "shotgun",
    "tacz:ak47": "rifle",
    "tacz:m249": "heavy"
  }
}
```

Only `pistol`, `shotgun`, `rifle`, and `heavy` are accepted. Explicit mappings always take priority. For unmapped TaCZ custom-gunpack IDs, Aftermath now guesses the class from common words in the ID (for example `pistol`, `shotgun`, `m249`, `rpg`, or `smg`); an ambiguous name defaults to `rifle` so it still earns XP. This is a best-effort fallback, not a guarantee of correct class balance.

Hold the gun and run `/aftermath weapons held` to display the exact TaCZ gun ID and how its class was chosen. If the inferred class is wrong, add an explicit mapping in another JSON file under `data/<namespace>/firearm_classes/`, for example:

```json
{
  "guns": {
    "addon:example_handgun": "pistol",
    "addon:example_shotgun": "shotgun"
  }
}
```

Weapon experience is stored separately for melee, pistol, shotgun, rifle, and heavy classes.

## Lesson

```json
{
  "title": "lesson.aftermath.first_aid",
  "skill": "aftermath:first_aid",
  "item": "aftermath:training_manual_first_aid",
  "duration_ticks": 1200,
  "xp_reward": 20,
  "max_completions": 1,
  "cancel_on_movement": true,
  "cancel_on_damage": true
}
```

The manuals are `aftermath:training_manual_first_aid`, `aftermath:training_manual_firearms_safety`, and `aftermath:training_manual_mechanics`. They have crafting recipes. The server cancels reading if the player moves, takes damage, or switches away from the matching manual. Session completion, skill XP, and completion counts are saved on the player.

## TV program

```json
{
  "title": "tv_program.aftermath.field_medicine",
  "skill": "aftermath:first_aid",
  "start_time": 6000,
  "duration_ticks": 1200,
  "xp_reward": 15,
  "repeat_after_days": 1,
  "max_completions_per_player": 1
}
```

The training television's Field Medicine broadcast starts at world time 6000 (noon) and accepts viewers during the following 1200 ticks. It can be watched once per player. Use Minecraft server world time, not client wall-clock time.

## Noise event

```json
{
  "type": "noise",
  "noise_radius": 64,
  "lure_duration_ticks": 1200,
  "cooldown_ticks": 20,
  "target_tag": "aftermath:horde_members",
  "affects_loaded_entities_only": true,
  "max_affected_entities": 32
}
```

Noise affects existing entities only; it never spawns entities in response to player noise.

## Horde archetype

```json
{
  "entity_tag": "aftermath:horde_members",
  "leader": true,
  "follower_radius": 20,
  "max_followers": 12,
  "speed_multiplier": 1.1
}
```

## Day progression

```json
{
  "stages": [
    {"min_day": 1, "max_day": 3, "detection_multiplier": 1.0, "group_size": 4, "can_break_doors": false},
    {"min_day": 4, "max_day": 7, "detection_multiplier": 1.15, "group_size": 8, "can_break_doors": false},
    {"min_day": 8, "max_day": 14, "detection_multiplier": 1.3, "group_size": 12, "can_break_doors": false}
  ]
}
```

Stages must start on day 1, be sorted into contiguous non-overlapping ranges, and use bounded multipliers/group sizes. After the last `max_day`, the last stage remains active. The world day is stored in Overworld SavedData and advances with server game ticks (24,000 ticks per progression day); it survives restarts. Test commands: `/aftermath day status` and admin-only `/aftermath day set 8`.

The `detection_multiplier` scales noise-event radius, while `group_size` caps the total leader-plus-followers count (still subject to global server caps). The `can_break_doors` flag is reserved for future block-interaction behavior and does not itself enable block breaking. Block breaking remains disabled by default.
