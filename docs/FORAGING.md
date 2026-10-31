# Scavenging search (Stage 23)

Stage23 adds a non-blocking, Project-Zomboid-inspired search mode. The server owns discovery, loot rolls, pickup validation, skill XP and depleted spots. The client only renders the latest bounded snapshot and sends input requests.

## Controls

| Action | Default key |
| --- | --- |
| Toggle search mode | `END` |
| Cycle search category | `G` |
| Collect the nearest revealed find | `H` |
| Command alternative | `/aftermath search` |

All three keys are registered as rebindable controls in **Options → Controls → Key Binds → Aftermath**. The search display is a HUD layer rather than a blocking screen; it does not pause the game or consume movement input. The radar circle represents the current search radius. An eye marker is sent only when a point is within 6 blocks; its item and stack size are withheld until the player is within 3 blocks.

## Search radius

The server calculates the horizontal radius from the scavenging skill and character profile:

- Base radius: 3 blocks.
- Skill contribution: 0.7 blocks per level, with the skill-derived radius capped at 10.
- Scavenger profession: +2.5 blocks.
- Scavenger Keen Eye trait: +2.5 blocks.
- Absolute cap: 15 blocks.
- Full darkness (block and sky light both at or below 1): 1.5 blocks.

The values are server-configurable in the `foraging` section of the Aftermath server config. Skill XP and levels are read from the existing learning progression; this feature does not add a parallel skill store.

## Discovery and persistence

The search examines a bounded 5×5-block cell grid around an actively searching player. Each cell has a deterministic point location and a configurable chance to contain a find. A candidate is considered only if its chunk is already loaded. Ground lookup checks a short vertical range near the player's current floor, so the same mechanism can find resources in buildings and caves without scanning the entire column or requesting chunks.

The contents of a point are rolled once and persisted in the dimension's `aftermath_foraging` SavedData. Changing focus, moving away, or relogging does not reroll its contents. A collected point is marked depleted for seven in-game days; afterwards it can regenerate. The saved cache is bounded to 8,192 generated/depleted spots per dimension.

There are no hidden marker entities. The server keeps a short-lived per-player list of points that it has already considered. A collection request includes only the packed block position; the server verifies that the point belongs to the player's current visible search cache, is revealed, is in the same dimension, is loaded and is within pickup distance before giving the stored item. Client-supplied item IDs or counts are never accepted.

The scan interval defaults to 10 player ticks. The server returns at most 48 nearby points and at most 32 categories in a snapshot.

## Loot categories

Bundled data tables live at `data/aftermath/foraging/categories/<category>.json`:

| Category | Unlock | Context |
| --- | --- | --- |
| `food` | Skill 1 | Forest fruit, farm produce and urban food |
| `stones` | Skill 1 | Flint, stone, gravel, road-edge materials and wet clay |
| `wood` | Skill 1 | Sticks, saplings and logs; wood weight rises in cold/winter-like biomes |
| `trash` | Skill 1 | Paper, string, bottles, leather and small salvage; urban areas have better yields |
| `rare` | Entry-specific, mostly 3–6 | Low-weight useful items such as a compass, clock or metal |
| `herbs` | Skill 6 | Wild-herb bundle and mushrooms; rain/recent rain improves odds |
| `fruit` | Skill 7 | Berries, apples and farm fruit |

The selected category receives a configurable 1.8× weight multiplier by default. It is a preference, not a guarantee: entries from other eligible categories can still be rolled. A spot's loot is saved at first generation, so cycling focus does not reroll an existing spot.

### Entry schema

Each category is a JSON object with an `entries` array. Each entry supports:

- `item` — registered item ID.
- `weight` — relative integer weight (1–10,000).
- `min_skill` — minimum scavenging level (1–10; defaults to 1).
- `min_count`, `max_count` — inclusive stack-size range (1–64).
- `biomes` — optional list of biome path fragments.
- `requires_wet`, `night_only`, `winter_only`, `plant`.
- `requires_farmland`, `requires_road`, `requires_urban` — nearby-block context gates.
- `winter_bonus` — relative weight multiplier in winter-like conditions (1–20).
- `wet_bonus` — relative weight multiplier during rain/recent rain (1–10).

Malformed categories are logged and skipped independently. The reload publishes an immutable snapshot after parsing. Individual category files may be extended or overridden with datapacks; provide matching category translations for any new IDs.

## Environment rules and current limits

- Rain is sampled per dimension every 200 level ticks and remembered for 12,000 ticks after it stops. Rain-aware entries can require wet conditions or receive a weight bonus.
- Night is currently the world-time interval 13,000–22,999 ticks. The bundled night-only find is a rare glow-berry find in Lush Caves.
- Minecraft 1.21.1's biome base temperature is used as the current winter-like proxy. This is not a calendar-season system. No optional season-mod integration is enabled until a real 1.21.1 API has been verified.
- Forest/farm/road/urban context is inferred from nearby loaded vanilla blocks, not from third-party city-generation or seasonal APIs.
- Wild Herbs is a non-medical crafting ingredient. It does not heal, stop bleeding or replace First Aid New / Model Injuries.
- The initial ground probe searches only a bounded vertical range around the player's current floor. It does not survey distant floors or force chunks to load.

## Server config

The `foraging` config section exposes:

- `baseRadius`, `radiusPerSkillLevel`, `maxBaseRadius`
- `professionRadiusBonus`, `traitRadiusBonus`
- `spotChance`, `focusWeightMultiplier`, `scanIntervalTicks`

The `features.enableForaging` toggle disables the search mode without changing existing learning or medical progression.

## Acceptance tests still requiring an in-game run

1. Press/rebind END; verify the HUD appears/disappears and walking, jumping, attacking and inventory controls continue to work.
2. Verify eye markers appear as the player approaches, item icons/names appear inside 3 blocks, and H collects exactly one find.
3. Attempt to send a collection request for an unknown, unrevealed, distant or already-collected point; confirm no item is granted.
4. Switch categories with G; verify odds change for newly generated points while an already-generated point keeps its original contents.
5. Check profession/trait radius, skill progression, darkness, rain/recent rain, nighttime glow berries, cold-biome plant suppression, winter wood bonus and farm/road/urban context.
6. Relog and restart the world; verify saved loot, depletion timers and rain state remain valid.
7. Check server performance with several players searching at once and verify that distant/unloaded chunks are not requested.
8. Confirm clean NeoForge-only client and dedicated-server startup; optional integrations are not required.
