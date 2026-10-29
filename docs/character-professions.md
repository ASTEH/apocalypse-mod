# Profession datapack schema

Professions are loaded from `data/aftermath/professions/*.json`. Add a JSON file to add a selectable profession; no Java switch statement is required.

```json
{
  "id": "aftermath:driver",
  "name": "profession.aftermath.driver",
  "description": "profession.aftermath.driver.description",
  "icon": "minecraft:compass",
  "startPoints": 8,
  "allowed_traits": [],
  "grantedTraits": ["aftermath:speed_demon"],
  "startItems": [
    { "item": "minecraft:compass", "count": 1 },
    { "item": "minecraft:minecart", "count": 1 }
  ],
  "skillBonuses": {
    "driving": 1
  }
}
```

- `id` must match the file's resource location, e.g. `aftermath:driver`.
- `name` and `description` are translation keys in the `assets/aftermath/lang` files.
- `icon` is an item resource ID; its item icon is shown in the profession selector.
- `startPoints` is the trait-selection budget (0–100).
- `allowed_traits` is an optional allow-list. An empty array allows every trait unless a trait itself requires a specific profession.
- `grantedTraits` are validated by the server, saved in the profile for free, shown as locked profession bonuses, and count toward incompatibility checks.
- `startItems` contains item resource IDs and counts from 1 to 64. Unknown IDs are skipped at grant time with a warning.
- `skillBonuses` maps a skill ID to a starting level (0–10). Positive levels are saved with the character profile; `driving: 1` gives the Driver a persistent starting driving level for the Stage 5 vehicle skill system.
- Legacy `starting_points` and `starting_items` remain supported for existing datapacks.
