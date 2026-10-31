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

New-format definitions live at `data/<namespace>/traits/<id>.json`; the filename determines the resource ID and the optional `id` property must match it. Legacy `category`, `point_cost`, `point_credit`, and `conflicts` fields remain supported for existing datapacks.

```json
{
  "id": "aftermath:keen_hearing",
  "name": "trait.aftermath.keen_hearing",
  "description": "trait.aftermath.keen_hearing.description",
  "type": "POSITIVE",
  "pointCost": 3,
  "effects": [
    { "type": "custom_hook", "target": "keen_hearing", "value": 1.0 }
  ],
  "incompatibleWith": ["aftermath:deaf", "aftermath:hard_of_hearing"]
}
```

- `type`: `POSITIVE`, `NEGATIVE`, or `NEUTRAL`.
- `pointCost`: positive number for a positive trait, negative number for a negative trait (credit added to the budget), and 0 for neutral.
- `effects`: `attribute_multiplier` / `attribute_modifier`, `mob_effect` / `vanilla_mob_effect`, `learning_xp_multiplier`, `weapon_xp_multiplier`, `reading_speed_multiplier`, `damage_multiplier`, or an allow-listed `custom_hook`.
- `incompatibleWith`: IDs that cannot be selected together. Server validation checks both sides of every conflict.
- `requiresProfession` (optional): a profession ID; the existing selection UI hides this trait for other professions and the server independently rejects it.
- A profession with an empty `allowed_traits` array accepts every trait unless `requiresProfession` says otherwise. A non-empty array remains an explicit allow-list. Thus, adding a new general-purpose trait normally requires only its JSON file and translations.
- Attribute modifiers currently support movement speed, max health, attack damage, attack speed and armor. Mob effects specify a registry ID, amplifier in `value`, and optional `duration_ticks`; active effects are refreshed server-side.
- Custom hooks are allow-listed Java behavior, not executable JSON. Unsupported hook IDs make only the individual trait definition fail validation.
- The server recomputes the point balance; a client cannot submit a negative balance or bypass trait constraints.

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

## Medical integration (M7)

Aftermath does not register a local bleeding timer, severity attachment, bandage item, bandage recipe or bleeding Moodle. First Aid New owns body-part health and treatment; Model Injuries extends it with structural injuries, blood loss and recovery. Use `/aftermath medical status` or `/aftermath wounds` to inspect provider detection.

The optional `aftermath.firstaid.mixins.json` integration is loaded only when mod ID `firstaid` is present. It observes the First Aid New body-part treatment completion callback and awards the Aftermath `first_aid` skill using the existing daily/action anti-farm controls. The Medic's Triage trait multiplies this XP as usual. Aftermath Moodles still respond to hostile `damage_taken` events; anatomical injury penalties remain owned by First Aid New / Model Injuries.

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
