# Unified skills menu

Stage22 adds a single presentation layer over the existing Aftermath progression systems. It does not replace or migrate the underlying player attachments.

## Opening the menu

- Default key: `K`.
- Rebind it in **Options → Controls → Key Binds → Aftermath → Open skills**.
- Command: `/aftermath skills`.

The key sends an empty request payload. The server builds the current snapshot from the player's saved data and responds with the display values. Reopen the menu to refresh the snapshot; the client does not submit XP or level values.

## Current catalog

The bundled catalog contains 14 entries:

| Category | Skills |
| --- | --- |
| Survival | Farming, Scavenging |
| Crafting | Cooking, Mechanics |
| Combat | Firearms, Melee, Melee weapons, Pistols, Shotguns, Rifles/SMGs, Sniper rifles, Heavy weapons |
| Medical | First Aid |
| Vehicles | Driving |

The general **Firearms** and **Melee** skills are distinct from the six weapon-class specializations. The general skills collect their existing learning XP; weapon-class entries read the dedicated weapon XP attachment and use its existing progression curve.

## Data-driven skill definition

Files live under `data/aftermath/skills/<id>.json`.

Learning-backed skill example:

```json
{
  "name": "skill.aftermath.cooking",
  "description": "skill.aftermath.cooking.description",
  "category": "crafting",
  "max_level": 10,
  "xp_thresholds": [0, 40, 100, 180, 280, 400, 540, 700, 880, 1080]
}
```

Supported fields:

- `name`: translation key for the skill title.
- `description`: translation key explaining what the existing skill currently affects.
- `category`: stable category ID used to build filter tabs. Current IDs: `survival`, `crafting`, `combat`, `medical`, `vehicles`.
- `max_level`: maximum displayed/progression level (1–10).
- `xp_thresholds`: cumulative XP threshold for each level, including level 1 at index 0. Required for learning-backed skills and must be strictly increasing.

Weapon specialization example:

```json
{
  "name": "weapon_class.aftermath.pistol",
  "description": "skill.aftermath.weapon_pistol.description",
  "category": "combat",
  "progression_source": "weapon",
  "progression_key": "pistol",
  "max_level": 10
}
```

For weapon-backed skills, `progression_source` must be `weapon` and `progression_key` must match the existing weapon class ID (`melee`, `pistol`, `shotgun`, `rifle`, `sniper`, `heavy`). Do not duplicate `xp_thresholds` in JSON: the menu reads the authoritative curve from `WeaponProgress.thresholdForLevel`.

Omitting `progression_source` uses the default `learning` source. New definitions are discovered on datapack reload and rendered from catalog data; the screen does not need a new hard-coded row for each skill. Provide the referenced name and description translations in both `ru_ru.json` and `en_us.json`.

## Progression semantics

- Learning-backed levels use the skill JSON thresholds and existing profession starting-level bonuses.
- Weapon-backed levels and XP come from `WeaponProgress` and `WeaponProgressionService`.
- The menu calculates current XP, XP needed for the next level and progress within the current level on the server.
- Descriptions are informational. They should accurately describe current implemented effects and must not promise unimplemented bonuses.
- The screen is a non-pausing overlay. It reads a snapshot when opened; it does not stream updates every tick.

## Compatibility

Existing `/aftermath learning`, `/aftermath weapons` and `/aftermath weapons held` commands remain available. Medical progress is shown only as the existing First Aid skill XP; the medical mods remain responsible for anatomy, wounds, bleeding and treatment.
