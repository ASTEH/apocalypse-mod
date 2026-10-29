# Architecture

## Runtime boundaries

- **Core:** validated definitions, config, persistence, server events and networking. No client-only Minecraft classes.
- **Client:** screens, HUD and input. Client submits requests; server owns outcomes.
- **Optional compatibility:** third-party API imports live only in isolated adapters loaded after a mod-presence check.
- **Data:** custom JSON under `data/aftermath/`; reload builds immutable definitions and publishes them atomically.

## Persistence

- Character profile, weapon XP, and learning XP/completion history use persistent player attachments with explicit codecs and copy-on-death semantics.
- Bleeding severity and its countdown use a persistent attachment without copy-on-death, so wounds survive relogs but a new life does not inherit them.
- Global day progression and persistent world events: `SavedData`.
- Temporary noise signals and horde membership: level-scoped, expiring memory; never force-load chunks to keep events active.
- In-progress reading/TV sessions are transient; interruption grants no XP.

## Services

- `CharacterSelectionService`: validates profession/traits and applies effects.
- `WeaponProgressionService`: XP and level damage bonuses for direct tagged melee hits and verified TaCZ projectile kills, with anti-farm controls.
- `FirearmDefinitions`: reloads validated external gun-ID-to-class mappings from datapack JSON.
- `TaczCompatibility`: reflection-only optional adapter for TaCZ bullets and runtime gun caches; resolves actual projectile owner/gun ID, scales spread/recoil, adjusts reload clocks, and remains safe if TaCZ is absent.
- `LearningDefinitions`: loads and validates lesson and TV JSON definitions.
- `LearningService`: server-timed reading and scheduled TV sessions; persistent skill XP and per-material completion counts.
- `FirstAidService`: rolls configurable bleeding chance for damaging hostile hits, applies timed blood-loss ticks, and handles bandage treatment; Medic characters stop all bleeding with one bandage.
- `BloodScentService`: leaves time-limited blood trail spots after player damage and while bleeding; spots periodically alert nearby eligible mobs through the shared attraction director, with per-dimension spot and pulse caps.
- `LightSourceAwarenessService`: indexes block-emitted light when placed, removes destroyed/disabled sources, discovers existing lights around active players in a throttled scan, and alerts eligible mobs only when a ray check gives them line of sight.
- `NoiseDefinitions`: validates data-driven noise events and horde archetypes on datapack reload.
- `NoiseService`: translates server sound events, non-command chat, block interactions, movement, blood scent, and light sightings into data-driven attraction profiles; per-source cooldowns, filtered line-of-sight queries, and target caps keep scans bounded. It assigns only already-loaded eligible mobs to a temporary destination.
- `ZombieEntityClassifier`: accepts datapack-tagged horde members plus common hostile zombie/infected/walker variants from modpacks.
- `HordeDirector`: adds a movement goal without replacing other AI goals, groups nearby mobs, and enforces a per-dimension path request budget.
- `DayProgressionDefinitions`: validates and reloads stages that scale noise radius and group size.
- `WorldProgressData`: persists the global apocalypse day in Overworld `SavedData`, shared across dimensions.
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
7. Scent trails and light indexes are bounded; light discovery and attraction pulses have explicit budgets and must not force-load chunks.

## Reload

Invalid definitions log a resource ID and error. Learning definitions currently reject individual malformed JSON files and publish the successfully loaded definitions; other systems should follow the same safe validation policy.
