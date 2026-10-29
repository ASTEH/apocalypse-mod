# Data formats — draft v1

These are Aftermath-owned JSON contracts, not built-in NeoForge schemas. M0 includes examples for review; the loader and validators must be implemented before definitions drive gameplay.

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

Tags classify items; they do not prove which weapon caused a projectile. Unsupported shots must not receive guessed XP.

## Lesson

```json
{
  "title": "lesson.aftermath.first_aid",
  "skill": "aftermath:first_aid",
  "item": "aftermath:training_manual",
  "duration_ticks": 1200,
  "xp_reward": 20,
  "max_completions": 1,
  "cancel_on_movement": true,
  "cancel_on_damage": true
}
```

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

Use Minecraft server world time, not client wall-clock time.

## Noise event

```json
{
  "type": "noise",
  "noise_radius": 64,
  "lure_duration_ticks": 1200,
  "cooldown_ticks": 200,
  "target_tag": "aftermath:horde_members",
  "affects_loaded_entities_only": true
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
  "health_multiplier": 1.5,
  "speed_multiplier": 1.1,
  "aura_radius": 8
}
```

## Day progression

```json
{
  "stages": [
    {"min_day": 1, "max_day": 3, "detection_multiplier": 1.0, "group_size": 4, "can_break_doors": false},
    {"min_day": 4, "max_day": 7, "detection_multiplier": 1.15, "group_size": 8, "can_break_doors": false},
    {"min_day": 8, "max_day": 14, "detection_multiplier": 1.3, "group_size": 12, "can_break_doors": true}
  ]
}
```

Values are initial balance examples, not final commitments. Config hard-caps groups and disables block breaking by default.
