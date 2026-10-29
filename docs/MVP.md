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
- [x] Gunshots emit the existing configurable noise event; unknown gun IDs fail closed for XP.

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
- [ ] CI build and clean client/dedicated-server startup for this milestone.
- [ ] Manual acceptance tests in a world with TaCZ installed and another world without TaCZ.
- [ ] Manually validate custom gunpack IDs and tune per-class balance after the fallback/diagnostic update.

**Acceptance:** firearm kills only award XP to the mapped gun class; swapping weapons before a bullet hits does not misattribute it; no crash occurs without TaCZ; firing attracts already-loaded zombies; melee proficiency remains unchanged.

## Release gate

A stage is not done until CI passes, client and dedicated server launch, clean NeoForge-only startup works and manual acceptance tests pass.
