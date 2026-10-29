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
- [ ] Optional firearm adapters; unsupported projectile APIs deliberately receive no XP.

## M3 — Learning

- [x] Three registered and craftable training manuals and lesson definitions.
- [x] Registered training television with a server-world-time broadcast window at noon.
- [x] Reading and viewing sessions are timed by the server.
- [x] Persistent skill XP and completion counts in a player attachment.
- [x] Movement, damage, changing the active manual, or removing the TV cancels a session without XP.
- [x] The Quick Learner trait multiplies learning XP; status is available via `/aftermath learning`.
- [ ] CI build and clean client/dedicated-server startup.
- [ ] Manual acceptance tests in game.

**Acceptance:** each lesson/program grants its reward only within the configured completion limit; progress persists through relog and death; two players can study the same TV independently; cancelled sessions grant no XP.

## M4 — Noise and horde group

- One data-driven gunshot noise event.
- Existing tagged zombies in bounded range can investigate.
- No entity spawn call from the noise handler.
- One leader with bounded followers.
- Per-entity path cooldown and global pathfinding budget.

**Acceptance:** noise attracts existing zombies; no mobs appear because of noise; no chunk is loaded solely for migration; foreign AI goals remain.

## M5 — Day progression

- Persistent world day/stages.
- Configurable stages and archetypes.
- Per-stage sensing/grouping and optional door interaction.
- Block breaking disabled by default; allow-list only; no block entities.

**Acceptance:** stage changes with world days and survives restart; config can disable Aftermath AI without disabling foreign AI; profiler confirms bounded work.

## Release gate

A stage is not done until CI passes, client and dedicated server launch, clean NeoForge-only startup works and manual acceptance tests pass.
