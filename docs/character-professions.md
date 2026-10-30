# Profession datapack schema

Professions are loaded from `data/aftermath/professions/*.json`. Add a JSON file to add a selectable profession; no Java switch statement is required.

```json
{
  "id": "aftermath:driver",
  "name": "profession.aftermath.driver",
  "description": "profession.aftermath.driver.description",
  "icon": "minecraft:compass",
  "difficulty": 3,
  "startPoints": 8,
  "allowed_traits": [],
  "grantedTraits": ["aftermath:speed_demon"],
  "uniqueTraits": ["aftermath:driver_road_instinct", "aftermath:driver_road_fatigue"],
  "startItems": [
    { "item": "minecraft:compass", "count": 1 },
    {
      "mod": "mts",
      "item": "mts:mts.key",
      "fallback": "minecraft:boat",
      "count": 1
    }
  ],
  "skillBonuses": {
    "driving": 1
  }
}
```

- `id` must match the file's resource location, e.g. `aftermath:driver`.
- `name` and `description` are translation keys in the `assets/aftermath/lang` files. Keep the description synchronized with the resolved `startItems` loadout and list modded equipment plus its fallback where relevant.
- `icon` is an item resource ID; its item icon is shown in the profession selector.
- `difficulty` is a hand-authored early-survival rating from 1 (easiest start) to 5 (hardest start). Rate the actual starting loadout, skill bonuses and granted/unique profession traits; it is not the world's difficulty.
- `startPoints` is the trait-selection budget (0–100).
- `allowed_traits` is an optional allow-list. An empty array allows every trait unless a trait itself requires a specific profession.
- `grantedTraits` are validated by the server, saved in the profile for free, shown as locked profession bonuses, and count toward incompatibility checks.
- `uniqueTraits` lists the profession-exclusive positive/negative traits added automatically. Every listed trait must set `professionOnly: true` and `requiresProfession` to this profession's ID.
- `startItems` accepts an item ID or an object with `item`, `count`, and optional `mod`, `fallback`, `fallback_count`, and `custom_data`. If the optional mod is absent or the target item cannot be resolved, the fallback item/count is granted. `custom_data` supports string, numeric and boolean values and is applied to the primary modded item only.
- `skillBonuses` maps a skill ID to a starting level (0–10). Positive levels are saved with the character profile.
- Legacy `starting_points` and `starting_items` remain supported for existing datapacks.
