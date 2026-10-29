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
- [ ] CI compilation plus clean client and dedicated-server launch.
- [ ] Manual acceptance tests in a fresh world.

**Acceptance:** forged client packet cannot grant traits; insufficient budget and conflicting traits are rejected; selected effects and starting kit apply once; the profile persists after relog and death; disabled feature skips the screen.

## M2 — Weapon proficiency

- Classes: melee, pistol, shotgun, rifle, heavy.
- Item tags determine class.
- XP only for verified server-side kills attributable to a tagged weapon.
- Thresholds, levels, server-to-client sync and feedback.
- Anti-farm: victim identity/time window, exclude non-hostile targets, configurable cap.

**Acceptance:** one death gives no duplicate XP; unsupported gun APIs grant no accidental XP; vanilla melee works without other mods.

## M3 — Learning

- Three lesson definitions and one registered TV block.
- Reading/viewing are server-timed sessions.
- Progress persists and syncs.
- Movement/damage can cancel.
- TV schedule uses server time.

**Acceptance:** reconnecting cannot duplicate rewards; two players get individual progress from the same TV; cancelled session gives no XP.

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
