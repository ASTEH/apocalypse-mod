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
- [x] Manual acceptance tests in game.

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

## M7 — Injuries and first aid

- [x] Persistent bleeding status with severity and countdown; saved across relogs and cleared on death.
- [x] Configurable chance of bleeding from a damaging hostile hit and configurable severity/damage cadence.
- [x] Craftable bandage item and Russian/English translations.
- [x] Bandage reduces bleeding severity; one bandage stops a mild wound.
- [x] Medic profession fully treats bleeding with one bandage and starts with three bandages.
- [x] `/aftermath wounds` displays injury status; `/aftermath status` includes the bleeding toggle.
- [ ] CI build and clean client/dedicated-server startup.
- [ ] Manual test: chance 100% causes bleeding, severity stacks only to the configured cap, periodic damage occurs, and bandages stop/reduce bleeding.
- [ ] Manual test: relog preserves a wound timer; death clears it; Medic bonus works; disabling bleeding prevents damage.

**Acceptance:** only hostile damage can trigger bleeding; the server owns all wound state and damage; failed bandage use does not consume an item; injury status persists safely without TaCZ or other optional mods.

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

## User roadmap — Stage 4: firearm traits and weapon mastery

This stage adds the missing runtime behavior for the existing `Eagle Eye` and `Quick Reload` traits while keeping TaCZ optional.

- [x] Eagle Eye reduces TaCZ's active inaccuracy cache on client and server; the effective multiplier is computed server-side from the trait JSON and server config, then sent to the client.
- [x] Quick Reload accelerates TaCZ's active reload clock rather than changing only a client animation.
- [x] Firearm proficiency adds a modest reload-duration bonus at levels 2, 5 and 10, layered with Quick Reload when both are active.
- [x] Add a server-config toggle and strength scales for the new firearm trait effects.
- [x] Keep all TaCZ calls reflective so clean NeoForge can load without TaCZ or another firearm mod.
- [ ] Manual test: Eagle Eye affects spread only when selected and restores default spread when absent or disabled.
- [ ] Manual test: Quick Reload completes faster, works on empty and tactical reloads, and doesn't duplicate ammo or bypass reload cancellation.
- [ ] Manual test: level 2 / 5 / 10 reload bonuses use the currently active firearm class; swapping guns does not apply the previous gun's class.
- [ ] Manual test: custom TaCZ gunpack IDs retain explicit datapack mappings and inference behavior.
- [ ] Manual test: clean NeoForge starts without TaCZ; TaCZ server and client both work with effects enabled and disabled.
- [ ] Verify no regression to melee XP/damage, noise events, character creation, or the SDM UI Lib screen.

**Acceptance:** CI passes; normal reload remains unchanged when trait effects and firearm proficiency are disabled; server timing is authoritative; missing/changed TaCZ reflection methods fall back to vanilla TaCZ timing without crashing.

## Release gate

A stage is not done until CI passes, client and dedicated server launch, clean NeoForge-only startup works and manual acceptance tests pass.
