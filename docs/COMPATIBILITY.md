# Compatibility matrix

The core requires only Minecraft/NeoForge. TaCZ compatibility is isolated and uses reflection, so TaCZ is never a hard dependency. Trait audio/fog effects use client-only NeoForge events and are loaded through a client-only reflective bootstrap; no client class is referenced by dedicated-server gameplay code. The M7 injury system uses vanilla/NeoForge damage and item events only; it does not require a health, fracture, infection, or wound mod.

| Mod/group in the target pack | Planned integration | Fallback |
|---|---|---|
| TaCZ 1.21.1 NeoForge port | Implemented optional adapter: identifies server bullets, shooter, gun ID, shot noise, kill XP and class damage bonuses; known default gun IDs are mapped in `firearm_classes/tacz.json` | No TaCZ references are loaded when absent; unmapped custom gun IDs use best-effort name inference (explicit datapack mappings take priority). |
| TaCZ Additions, Magazines, Guns Lights, Elite X Quality Guns, Fallout Gunpack and other gun packs | Custom-gunpack XP inference is implemented, but per-pack balance still needs validation; exact mappings can be added with datapack JSON | Core remains independent; ambiguous IDs default to rifle. |
| Other firearms mods | Not yet individually integrated | No firearm XP until an isolated adapter is added. |
| Farmer's Delight 1.21.x | Optional mixins award Cooking XP only after a cooking-pot recipe or skillet output succeeds; cooking item tags cover confirmed meal and cooked-food IDs. The nearest active player within 16 blocks receives 5 XP, with a 1-second cooldown and 80 XP/day cap. | Farmer's Delight is optional; string-targeted mixins load only when its mod ID is installed. |
| Create 6.0.10 (1.21.1 NeoForge) | Optional Mixin hooks cover item recipe application, basin recipes, millstone, successful fan item-entity processing, crushing-wheel output and saw cutting recipes. The nearest active player within 16 blocks receives 3 Mechanics XP, subject to a 5-second per-player cooldown and 80 XP/day cap. | Create remains optional; targets are string-based and isolated behind the Create-only mixin declaration. Local build and in-game testing are still required. |
| Cold Sweat / Serene Seasons | No Aftermath integration | Temperature-based Moodles were removed; these mods can still be installed independently. |
| Nutritional / FFFoodSpoilageAndPreservation / Realism Tweaks Reworked / Fracture Point / Hematic | Do not duplicate food, spoilage, wound or infection systems | Existing mechanics stay independent. |
| Zombie Awareness 1.21.x | Optional reflection bridge forwards Aftermath's verified server-side noise events into Zombie Awareness' public SOUND-scent API, so its enhanced mobs can investigate the same gunfire. | No Zombie Awareness classes are linked at compile time. If the optional API cannot be resolved, its bridge disables itself and Aftermath noise/hordes continue independently. |
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
- Earlier logs showed a Mixin redirect warning between Navaronee CoreLib and Lithium. That is a pack-level warning, not proof of an Aftermath issue. Core gameplay avoids mixins; the narrow Create adapter is isolated in a config that loads only when Create is present.


## TaCZ 1.21.1 firearm handling

TaCZ handling penalties are an optional integration and activate only when the `tacz` mod is present. No TaCZ classes are referenced from Aftermath's common Java code.

Firearm proficiency is 0–10. Level 0 is untrained; handling penalties decrease linearly and reach zero at level 10. The existing damage bonus thresholds remain unchanged: level 2 at 50 XP, level 5 at 425 XP, and level 10 at 2050 XP. The level-1 threshold is 25 XP so level 0 is a real starting state.

Verified hooks cover server projectile spread, server reload progress through TaCZ's `ModernKineticGunScriptAPI.getReloadTime()`, server/client aim transition speed, and one additional client camera kick per successful trigger action. Eagle Eye reduces the spread/aim handling penalty; Quick Reload reduces the reload-duration multiplier. TaCZ's authored recoil curves and weapon JSON values are not overwritten.

Custom gunpack reload scripts that bypass `getReloadTime()`, weapon sway, and universal draw/switch speed are not altered. See `API_VERIFICATION.md` for the exact inspected methods and limitations.
