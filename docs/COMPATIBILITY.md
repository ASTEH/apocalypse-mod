# Compatibility matrix

The core requires only Minecraft/NeoForge. TaCZ compatibility is isolated and uses reflection, so TaCZ is never a hard dependency. Trait audio/fog effects use client-only NeoForge events and are loaded through a client-only reflective bootstrap; no client class is referenced by dedicated-server gameplay code. The M7 injury system uses vanilla/NeoForge damage and item events only; it does not require a health, fracture, infection, or wound mod.

| Mod/group in the target pack | Planned integration | Fallback |
|---|---|---|
| TaCZ 1.21.1 NeoForge port | Implemented optional adapter: identifies server bullets, shooter, gun ID, shot noise, kill XP and class damage bonuses; known default gun IDs are mapped in `firearm_classes/tacz.json` | No TaCZ references are loaded when absent; unmapped custom gun IDs use best-effort name inference (explicit datapack mappings take priority). |
| TaCZ Additions, Magazines, Guns Lights, Elite X Quality Guns, Fallout Gunpack and other gun packs | Custom-gunpack XP inference is implemented, but per-pack balance still needs validation; exact mappings can be added with datapack JSON | Core remains independent; ambiguous IDs default to rifle. |
| Other firearms mods | Not yet individually integrated | No firearm XP until an isolated adapter is added. |
| Farmer's Delight | Optional cooking adapter and food tags | Vanilla cooking/crafting remain learning sources. |
| Create family | Optional production-completion adapter only if needed | No Create imports or dependency. |
| Cold Sweat / Serene Seasons | Optional environmental integration after API verification | Base traits work without temperature mods. |
| Nutritional / FFFoodSpoilageAndPreservation / Realism Tweaks Reworked / Fracture Point / Hematic | Do not duplicate food, spoilage, wound or infection systems | Existing mechanics stay independent. |
| Zombie Awareness / Zombie Apocalypse Addon / Simple Enemy Mod / Weather2 | Configurable AI participation and explicit noise hooks; no blanket removal of goals | Disable Aftermath AI without changing foreign AI. |
| LostCities / Big Lost City / Abandoned City Buildings / TNAS | Optional tags for event weighting, if stable | No worldgen replacement or hard dependency. |
| SecurityCraft / OutpostZero / Curios / DayZ Inventory / DayZ Hotbar | Future API/tag integration only after verification | Core works without them. |
| ModernFix / Ferrite Core / EntityCulling / ImmediatelyFast / Sodium | No required integration; profile server AI separately from client rendering | Core works without optimizers. |
| Sound Physics Remastered / AmbientSounds | Deafness cancels every sound whose SoundSource is not MUSIC (including modded ambience, UI, mobs and weapons), independent of sound ID; hearing traits scale relevant SoundInstances while delegating sound ID, source, coordinates and resolution. Short-sightedness changes standard viewport fog distances without a custom shader. | With Deaf, all non-MUSIC categories are muted even for unknown sound IDs; AmbientSounds and Sound Physics remain optional and no mixin is installed. Iris-specific shaders are not modified. |

## Mod-list review

- `Ragdollified` appears twice. If these are the same JAR/version, remove one copy; `Ragdollified Reactions` is a separate add-on and should be version-matched.
- `Immersive Vehicles` with official vehicle/content packs is expected, not necessarily a duplicate.
- EMI and JEI overlap as recipe viewers. Since JEI previously crashed in this pack, keep Aftermath independent of both and test one viewer at a time.
- Many gun packs are listed. Do not assume a shared firing API across TaCZ packs and Timeless & Classics; use isolated adapters.
- LostCities, Big Lost City, Abandoned City Buildings and TNAS need testing together in a fresh world. Aftermath won't replace worldgen.
- Create and its add-ons overlap in production systems; that matters only for future optional adapters.
- Do not remove GeckoLib, Curios, CoroUtil, GlitchCore, Kotlin for Forge, Moonlight Lib, Fzzy Config, YACL, Cloth Config, Atlas Lib, OctoLib, MRU or Navaronee Core just because they look like libraries; other installed mods may require them.
- Earlier logs showed a Mixin redirect warning between Navaronee CoreLib and Lithium. That is a pack-level warning, not proof of an Aftermath issue. Core will avoid mixins by default.
