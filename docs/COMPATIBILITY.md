# Compatibility matrix

The core requires only Minecraft/NeoForge. Entries below are planned optional integrations, not hard dependencies.

| Mod/group in the target pack | Planned integration | Fallback |
|---|---|---|
| TaCZ, TaCZ Additions, Magazines, Guns Lights, Elite X Quality Guns, Fallout Gunpack | First optional weapon adapter for reliable shot attribution; tags still determine class | Unsupported shots grant no guessed weapon XP. |
| Timeless & Classics Guns: Zero | Separate adapter if server-side attribution is reliable | Core stays independent. |
| Farmer's Delight | Optional cooking adapter and food tags | Vanilla cooking/crafting remain learning sources. |
| Create family | Optional production-completion adapter only if needed | No Create imports or dependency. |
| Cold Sweat / Serene Seasons | Optional environmental integration after API verification | Base traits work without temperature mods. |
| Nutritional / FFFoodSpoilageAndPreservation / Realism Tweaks Reworked / Fracture Point / Hematic | Do not duplicate food, spoilage, wound or infection systems | Existing mechanics stay independent. |
| Zombie Awareness / Zombie Apocalypse Addon / Simple Enemy Mod / Weather2 | Configurable AI participation and explicit noise hooks; no blanket removal of goals | Disable Aftermath AI without changing foreign AI. |
| LostCities / Big Lost City / Abandoned City Buildings / TNAS | Optional tags for event weighting, if stable | No worldgen replacement or hard dependency. |
| SecurityCraft / OutpostZero / Curios / DayZ Inventory / DayZ Hotbar | Future API/tag integration only after verification | Core works without them. |
| ModernFix / Ferrite Core / EntityCulling / ImmediatelyFast / Sodium | No required integration; profile server AI separately from client rendering | Core works without optimizers. |

## Mod-list review

- `Ragdollified` appears twice. If these are the same JAR/version, remove one copy; `Ragdollified Reactions` is a separate add-on and should be version-matched.
- `Immersive Vehicles` with official vehicle/content packs is expected, not necessarily a duplicate.
- EMI and JEI overlap as recipe viewers. Since JEI previously crashed in this pack, keep Aftermath independent of both and test one viewer at a time.
- Many gun packs are listed. Do not assume a shared firing API across TaCZ packs and Timeless & Classics; use isolated adapters.
- LostCities, Big Lost City, Abandoned City Buildings and TNAS need testing together in a fresh world. Aftermath won't replace worldgen.
- Create and its add-ons overlap in production systems; that matters only for future optional adapters.
- Do not remove GeckoLib, Curios, CoroUtil, GlitchCore, Kotlin for Forge, Moonlight Lib, Fzzy Config, YACL, Cloth Config, Atlas Lib, OctoLib, MRU or Navaronee Core just because they look like libraries; other installed mods may require them.
- Earlier logs showed a Mixin redirect warning between Navaronee CoreLib and Lithium. That is a pack-level warning, not proof of an Aftermath issue. Core will avoid mixins by default.
