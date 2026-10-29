# Architecture

## Runtime boundaries

- **Core:** validated definitions, config, persistence, server events and networking. No client-only Minecraft classes.
- **Client:** screens, HUD and input. Client submits requests; server owns outcomes.
- **Optional compatibility:** third-party API imports live only in isolated adapters loaded after a mod-presence check.
- **Data:** custom JSON under `data/aftermath/`; reload builds immutable definitions and publishes them atomically.

## Persistence

- Character profile, weapon XP, and learning XP/completion history use persistent player attachments with explicit codecs and copy-on-death semantics.
- Global day progression and persistent world events: `SavedData`.
- Temporary noise signals and horde membership: level-scoped, expiring memory; never force-load chunks to keep events active.
- In-progress reading/TV sessions are transient; interruption grants no XP.

## Services

- `CharacterSelectionService`: validates profession/traits and applies effects.
- `WeaponProgressionService`: XP from verified server-side direct melee kills, with anti-farm controls.
- `LearningDefinitions`: loads and validates lesson and TV JSON definitions.
- `LearningService`: server-timed reading and scheduled TV sessions; persistent skill XP and per-material completion counts.
- `WorldEventDirector`: events and world-day stages.
- `NoiseService`: typed noise events for already-existing eligible entities.
- `HordeDirector`: capped groups, leaders and scheduled follower/path updates.

## AI performance invariants

1. Never scan all loaded entities every tick.
2. Use bounded candidates per level/event and a global per-tick work budget.
3. Apply per-entity path request cooldowns; distant entities get coarse updates.
4. Never keep chunks loaded solely for migrating zombies.
5. Block breaking is disabled by default, uses explicit block allow-lists, and excludes block entities/protected blocks.
6. Never clear or replace another mod's goal selector.

## Reload

Invalid definitions log a resource ID and error. Learning definitions currently reject individual malformed JSON files and publish the successfully loaded definitions; other systems should follow the same safe validation policy.
