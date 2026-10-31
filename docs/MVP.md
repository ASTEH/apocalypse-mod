# MVP checklist and acceptance tests

## M0 — Bootstrap

- [x] Java 21 / NeoForge 1.21.1 ModDevGradle project skeleton
- [x] MOD ID `aftermath`; no required gameplay dependencies
- [x] Server config and `/aftermath status` command
- [x] CI workflow intended to run `./gradlew build`
- [ ] Confirm CI build and launch in client + dedicated server

**Acceptance:** build passes; clean Minecraft 1.21.1 NeoForge starts with only Aftermath; status command works on integrated and dedicated servers; no client-only class loads on server.

## M1 — Character creation

- [x] Professions: medic, mechanic, scavenger, each with a distinct starting kit.
- [x] Three positive and three negative attribute traits.
- [x] Server-side point budget, profession allow-list, duplicate and conflict validation.
- [x] Persistent profile attachment, copied on death.
- [x] First-join screen with server-supplied JSON options.
- [x] RU/EN labels, profession descriptions and trait descriptions.
- [x] Manually verified in game by the maintainer.

## M2 — Weapon proficiency

- [x] Persistent server-owned weapon XP attachment, copied on death.
- [x] Melee class resolved from the datapack item tag.
- [x] XP only for direct player melee kills against hostile entities.
- [x] Levels 1–10, server-sent actionbar feedback and `/aftermath weapons` status.
- [x] Melee damage bonus at levels 2 (+2%), 5 (+5%) and 10 (+10%).
- [x] Duplicate victim reward guard and configurable per-victim XP cap.
- [x] Manually verified in game by the maintainer.
- [x] Optional TaCZ 1.21.1 NeoForge adapter resolves the actual server bullet owner and gun ID without a hard dependency.
- [x] Pistol, shotgun, rifle/SMG, and heavy weapon XP, status and level-based damage bonuses.
- [x] Unmapped TaCZ custom-gunpack IDs use best-effort class inference; explicit datapack mappings override it.
- [x] `/aftermath weapons held` reports the held gun ID and class-assignment source.
- [x] Gunshots emit the existing configurable noise event.

## M3 — Learning

- [x] Three registered and craftable training manuals and lesson definitions.
- [x] Registered training television with a server-world-time broadcast window at noon.
- [x] Reading and viewing sessions are timed by the server.
- [x] Persistent skill XP and completion counts in a player attachment.
- [x] Movement, damage, changing the active manual, or removing the TV cancels a session without XP.
- [x] The Quick Learner trait multiplies learning XP; status is available via `/aftermath learning`.
- [x] CI build and clean client/dedicated-server startup.
- [x] Manual acceptance tests in game.

**Acceptance:** each lesson/program grants its reward only within the configured completion limit; progress persists through relog and death; two players can study the same TV independently; cancelled sessions grant no XP.

## M4 — Noise and horde group

- [x] Data-driven gunshot noise definition and public server-side noise API.
- [x] `/aftermath noise test` for testing without a firearm-mod adapter.
- [x] Only already-loaded, tag-eligible mobs inside the radius are considered; no spawn calls or chunk forcing.
- [x] Temporary leader/follower groups, capped by server config and archetype JSON.
- [x] Movement goal added alongside existing goals; per-entity path cooldown and global path request budget.
- [x] CI build and clean client/dedicated-server startup.
- [x] Manual acceptance tests in game for the original noise lure and leader/follower MVP.
- [x] Optional player-aware ring formation for player-sourced noise; formation radius and group size remain server-bounded.
- [ ] In-game test: grouped zombies take distinct approach positions around a moving player; disabling/resetting the horde override restores ordinary noise investigation.

**Acceptance:** noise attracts existing zombies; no mobs appear because of noise; no chunk is loaded solely for migration; foreign AI goals remain.

## M5 — Day progression

- [x] Persistent world day counter in Overworld SavedData, shared by dimensions and saved across restart.
- [x] Reloadable and validated `day_progression/default.json` stage table.
- [x] Per-stage noise detection radius multiplier and maximum horde group size, still bounded by server caps.
- [x] `/aftermath day status` and admin-only `/aftermath day set <1..36500>` for diagnostics and tests.
- [x] Day progression can be disabled independently; other mods' AI goals are not modified.
- [x] CI build and clean client/dedicated-server startup.
- [x] Manual acceptance tests in game, including world restart persistence and performance.

**Safety scope:** no block breaking or door destruction is performed by M5. The stage schema retains a door-interaction flag for a later isolated feature, but all default stages set it to false and `enableBlockBreaking` stays disabled.

**Acceptance:** stage changes with world days and survives restart; config can disable Aftermath AI without disabling foreign AI; profiler confirms bounded work.

## M6 — TaCZ firearm integration

- [x] Optional reflection-only adapter for the TaCZ 1.21.1 NeoForge port; core has no TaCZ dependency.
- [x] Server bullet attribution verifies projectile owner and reads actual gun ID.
- [x] Data-driven mappings for default TaCZ pistol, shotgun, rifle/SMG and heavy weapon IDs.
- [x] Unmapped TaCZ custom-gunpack IDs receive a name-based class inference with rifle fallback; explicit datapack mappings override it.
- [x] `/aftermath weapons held` displays the held gun's TaCZ ID and the source of its class assignment.
- [x] Firearm kill XP, level damage bonuses, and all weapon classes shown by `/aftermath weapons`.
- [x] Server-side TaCZ bullets emit the existing gunshot noise event.
- [x] CI build passes for the TaCZ and custom-gunpack XP implementation.
- [x] Manual acceptance with a TaCZ add-on gunpack confirmed by maintainer.
- [ ] Confirm clean client/dedicated-server startup without TaCZ.
- [ ] Validate more custom gunpack IDs and tune per-class balance.

**Acceptance:** firearm kills award XP to the mapped or inferred class; exact mappings override inference; swapping weapons before a bullet hits does not misattribute it; no crash occurs without TaCZ; firing attracts already-loaded zombies; melee proficiency remains unchanged.

## M7 — Medical integration (replaces the retired standalone system)

- [x] First Aid New + Model Injuries selected as the authoritative injury, bleeding and treatment stack.
- [x] Removed Aftermath's standalone bleeding attachment/timer, bleeding Moodle, bandage item, recipe and configuration.
- [x] Removed the Aftermath bandage from the Medic profession's starter kit.
- [x] Added an optional First Aid New treatment-completion bridge that awards First Aid skill XP through the existing anti-farm limits.
- [x] Preserved panic/stress Moodles on hostile damage and left limb/pain/movement penalties to the medical mods.
- [ ] CI build and clean dedicated-server startup with and without First Aid New.
- [ ] In-game test: completed body-part treatment awards First Aid XP; interrupted treatment does not.
- [ ] In-game test: Medic Triage XP bonus applies and daily/action caps still prevent farming.
- [ ] In-game test: Model Injuries limb restrictions and Aftermath stress/firearm-spread effects work together without duplicated injury penalties.

**Acceptance:** Aftermath does not register its own anatomy, wound timer, blood loss or bandage. First Aid New / Model Injuries remain authoritative for injuries; Aftermath adds skill progression and stress/Moodle systems without overriding medical mechanics.

## User roadmap — Stage 22: Unified skills menu

- [x] Audited the existing learning and weapon progression sources; retained both persistent data stores and all existing XP award paths.
- [x] Extended the reloadable skill catalog with display name, description, category and progression-source metadata.
- [x] Registered all eight existing learning skills and six separate weapon-class specializations in one dynamic catalog.
- [x] Added a server-authoritative snapshot payload that combines learning XP, profession starting levels and weapon-class XP without trusting client values.
- [x] Added a Project-Zomboid-inspired screen with category filters, scrollable skill list, current level, XP, progress bars, next-level XP and descriptions.
- [x] Added the rebindable `K` key under Options → Controls → Aftermath and `/aftermath skills`.
- [x] Preserved `/aftermath learning`, `/aftermath weapons` and existing diagnostic commands.
- [x] Added RU/EN labels and descriptions for all 14 entries.
- [x] Java 21 / NeoForge CI build passes on the Stage22 commit.
- [x] In-game acceptance: unified skills menu verified by maintainer.

**Acceptance:** all registered skill definitions appear without hard-coded screen entries; opening the menu requests a fresh server snapshot; the client cannot award or edit XP; existing progression storage and award sources remain unchanged.

## User roadmap — Stage 1: Trait expansion

This is distinct from the historical M1–M7 milestones above. It extends the existing character flow instead of replacing it.

- [x] Backwards-compatible trait loader accepts legacy fields and the new `id/name/description/type/pointCost/effects/incompatibleWith/requiresProfession` schema.
- [x] Existing positive and negative traits migrated to the new schema; 18 additional traits are defined in individual JSON files.
- [x] Server-side points, profession requirements and incompatibility checks remain authoritative; empty profession allow-lists act as data-driven "allow all".
- [x] Attribute modifiers, Night Vision, damage mitigation, reading speed, healing reduction, fear, food exhaustion and clumsy-footstep hooks are implemented.
- [x] Server-to-client trait-state payload gates deafness, hearing volume and short-sighted fog effects; Deaf mutes all SoundSource categories except MUSIC; uses no mixins or custom shader.
- [x] Trait selection list supports paging so all JSON-defined traits remain selectable in the existing screen.
- [ ] Manual test all three current professions; point-cost validation; incompatible traits; Fast Learner; Illiterate/Slow Reader; sound/fog traits; Cat's Eyes; Thick-Skinned; Clumsy horde noise.
- [ ] TODO: Eagle Eye and Quick Reload become functional with Stage 4 firearm control; vehicle-specific Speed Demon effects are Stage 5; Sleepyhead awaits a dedicated rest/fatigue mechanic.

**Acceptance:** all active trait definitions load without errors; the UI and server agree on points and compatibility; relog restores selected client-side sound/visual behavior; standalone NeoForge works with optional mods absent.

## User roadmap — Stage 23: Scavenging search

- [x] Retained and extended the existing `ForagingDefinitions` and `ForagingWorldData` preparation instead of replacing it.
- [x] Added validated datapack loot categories for food, stones, wood, trash, rare finds, skill-6 herbs and skill-7 fruit.
- [x] Added contextual weight/filter data for wet weather, recent rain, night, cold-biome winter proxy, forest/farm/road/urban surroundings and focused-category odds.
- [x] Added per-dimension persistence for first-rolled loot and depleted spots with seven in-game-day regrowth and a bounded cache.
- [x] Added server-authoritative deterministic search around active players; candidates are considered only in already-loaded chunks and no marker entities are created.
- [x] Added END search toggle, G category cycling, H collection and `/aftermath search`; all keybinds are rebindable.
- [x] Added a non-blocking radar HUD with a search-radius ring, approach eye markers and item reveal at close range.
- [x] Added server validation for collection requests and awarded scavenging XP through the existing `LearningService` anti-farm/cap logic.
- [x] Added the Wild Herbs crafting ingredient and RU/EN labels. It has no medical effects and does not replace the medical mods.
- [x] Documented controls, datapack schema, config and the current season/biome-context limitations in `docs/FORAGING.md`.
- [x] Java 21 / NeoForge GitHub Actions build passes on the Stage23 implementation commit.
- [x] In-game acceptance: END/G/H, search HUD, discovery and pickup verified by maintainer.
- [x] In-game acceptance: search behavior and persistence verified by maintainer.

**Known limits:** winter is a biome-temperature proxy rather than a calendar season; there is no optional season-mod adapter. Urban/farm/road recognition currently uses nearby vanilla block patterns. Ground detection is bounded around the player's current floor; it does not survey remote floors or load chunks.

**Acceptance:** only the server reveals and grants finds; clients cannot supply loot; resource points remain stable through focus changes/relog; collection depletes the point; unloaded chunks are never requested; the player can keep moving while searching.

## User roadmap — Stage 24: Body condition panel

<skipped> Intentionally skipped in Aftermath. The maintainer confirmed that another installed mod already provides the body-condition panel, so Aftermath will not create a duplicate anatomy/medical UI or another integration for the same display.

## User roadmap — Stage 25: Clothing patches

Journal items (HottieZ/HunkZ), stress and boredom mechanics are explicitly excluded from this stage.

- [x] Added a craftable Clothing Patch kit using only vanilla materials: white wool + 2 string yields 2 patches.
- [x] Added inventory right-click application onto items recognized in humanoid armor slots, including modded clothing that uses standard equipment slots.
- [x] Stored patch count in the garment's own namespaced `CUSTOM_DATA`, preserving the original item, enchantments and other components.
- [x] Limited each garment to three patches; a fourth application is rejected server-side without consuming the patch.
- [x] Added +0.5 armor per patch, capped at +1.5 armor for a garment, through the NeoForge `ItemAttributeModifierEvent`.
- [x] Added RU/EN item name, tooltip and feedback messages.
- [x] Durability remains controlled by the item's original vanilla/modded system; broken items do not grant patch armor, and normal repair of the original item retains patch metadata.
- [x] No journals, moodles, stress/boredom, injuries, medical treatment or bleeding logic were added.
- [x] Added `docs/CLOTHING_PATCHES.md` with recipe, controls, storage, durability and acceptance tests.
- [ ] Confirm Java 21 / NeoForge GitHub Actions build on the Stage25 head.
- [ ] In-game acceptance: recipe, application, three-patch cap, attribute bonus and multiplayer validation.
- [ ] In-game acceptance: durability, repair persistence and third-party clothing/armor compatibility.

**Known limits:** patch integration can only modify armor attributes that are exposed through the normal item attribute system. Clothing that is equipped or protected entirely through a third-party custom system may not honor this bonus and must be tested individually. Aftermath will not rewrite or replace that mod's armor behavior.

**Acceptance:** one original clothing item can receive at most three persistent patches; each adds +0.5 armor while worn; normal item data and durability stay owned by the original item; patching and item consumption are server-authoritative.

## Release gate

A stage is not done until CI passes, client and dedicated server launch, clean NeoForge-only startup works and manual acceptance tests pass.
