# Architecture

## Runtime boundaries

- **Core:** validated definitions, config, persistence, server events and networking. No client-only Minecraft classes.
- **Client:** screens, HUD and input. Client submits requests; server owns outcomes.
- **Optional compatibility:** third-party API imports live only in isolated adapters loaded after a mod-presence check.
- **Data:** custom JSON under `data/aftermath/`; reload builds immutable definitions and publishes them atomically.

## Persistence

- Player profile, weapon XP and learning history: persistent player attachment with explicit serializer/codec and deliberate copy-on-death policy.
- Global day progression and persistent world events: `SavedData`.
- Temporary noise signals and horde membership: level-scoped, expiring memory; never force-load chunks to keep events active.

## Services

- `CharacterSelectionService`: validates profession/traits and applies effects.
- `WeaponClassResolver`: classifies items using data-driven item tags.
- `WeaponXpService`: XP from verified server-side kill attribution, with anti-farm controls.
- `LearningService`: server-timed reading and TV sessions.
- `WorldEventDirector`: events and world-day stages.
- `NoiseService`: typed noise events for already-existing eligible entities.
- `HordeDirector`: capped groups, leaders and scheduled follower/path updates.
- `AftermathDataManager`: immutable snapshot of validated definitions.

## AI performance invariants

1. Never scan all loaded entities every tick.
2. Use bounded candidates per level/event and a global per-tick work budget.
3. Apply per-entity path request cooldowns; distant entities get coarse updates.
4. Never keep chunks loaded solely for migrating zombies.
5. Block breaking is disabled by default, uses explicit block allow-lists, and excludes block entities/protected blocks.
6. Never clear or replace another mod's goal selector.

## Reload

Invalid definitions log a resource ID and error; they do not partially replace active data. Publish a complete validated snapshot or keep the previous snapshot.
