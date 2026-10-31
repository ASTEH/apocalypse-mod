# Architecture

## Runtime boundaries

- **Core:** validated definitions, config, persistence, server events and networking. No client-only Minecraft classes.
- **Client:** screens, HUD and input. Client submits requests; server owns outcomes.
- **Optional compatibility:** third-party API imports live only in isolated adapters loaded after a mod-presence check.
- **Data:** custom JSON under `data/aftermath/`; reload builds immutable definitions and publishes them atomically.

## Persistence

- Character profile, weapon XP, and learning XP/completion history use persistent player attachments with explicit codecs and copy-on-death semantics.
- Medical anatomy, blood loss and treatment are owned by First Aid New / Model Injuries. Aftermath has no parallel bleeding attachment or bandage item.
- Global day progression and persistent world events: `SavedData`.
- Temporary noise signals and horde membership: level-scoped, expiring memory; never force-load chunks to keep events active.
- In-progress reading/TV sessions are transient; interruption grants no XP.

## Services

- `CharacterSelectionService`: validates profession/traits and applies effects.
- `WeaponProgressionService`: XP and level damage bonuses for direct tagged melee hits and verified TaCZ projectile kills, with anti-farm controls.
- `FirearmDefinitions`: reloads validated external gun-ID-to-class mappings from datapack JSON.
- `TaczCompatibility`: reflection-only optional adapter for TaCZ server bullets; resolves actual projectile owner and gun ID, and emits shot noise without loading TaCZ types when absent.
- `LearningDefinitions`: loads and validates lesson, TV, book, VHS and skill definitions. Skill metadata and progression sources are data-driven; weapon skills point to their existing weapon-class XP buckets instead of copying their thresholds.
- `SkillsMenuService`: builds a bounded server-authoritative snapshot by combining persistent `LearningProgress`, profession starting skill levels and `WeaponProgress`; it never mutates progression.
- `ForagingDefinitions`: reloads and validates bounded, data-driven loot categories; focus, biome, wetness, time, season-proxy and nearby-block requirements are evaluated by the server.
- `ForagingWorldData`: per-dimension `SavedData` for already-rolled resource contents, seven-day depletion and recent rain. Its cache is bounded and stores positions/items rather than hidden entities.
- `ForagingService`: derives deterministic search cells around active players, examines only already-loaded chunks and near-floor blocks, calculates skill/profession/trait radius, returns bounded snapshots, validates pickup requests and awards XP through the existing `LearningService`.
- `ForagingClientState`, `ForagingClientEvents` and `ForagingHud`: receive server snapshots, register the rebindable END/G/H controls and render the non-blocking search radar. Client input cannot choose item IDs, counts or arbitrary loot results.
- `ParkourService`: validates player motion server-side, samples player-sized collision boxes along a bounded vault/mantle/crouch traversal, requires loaded chunks and a sturdy landing, and accepts no client-supplied coordinates.
- `ZombieCamouflageService`: stores a timed player camouflage state, reduces zombie vision target acquisition through `LivingChangeTargetEvent`, keeps close detection and hearing active, and removes camouflage on attack, damage or loud player-originated noise.
- `NoiseDecoyBlockEntity`: persists radio/alarm active state, battery ticks and a stable noise source UUID; its server ticker periodically calls `NoiseService`. Devices never own a second entity-attraction or AI system.
- `ClothingPatchService`: adds a bounded per-item patch count in the garment's namespaced `CUSTOM_DATA`, applies a small `Attributes.ARMOR` modifier through `ItemAttributeModifierEvent`, and validates patching through `ItemStackedOnOtherEvent`. It only accepts normal humanoid armor slots, preserves the original garment stack and leaves durability/repair to the item's existing implementation.
- `SkillsScreen` / `SkillsClientEvents`: render the unified categorized skills dossier and provide the rebindable client key. The client only requests a snapshot; all XP, levels and thresholds are calculated on the server.
- `LearningService`: server-timed reading and scheduled TV sessions; persistent skill XP and per-material completion counts. All XP sources share a 120 XP per-skill in-game-day ceiling in addition to action-specific cooldowns and caps. Create and Farmer's Delight machine XP is credited to the nearest active player within 16 blocks.
- `CreateLearningHooks`: optional mixin bridge for Create 6.0.10 recipe application, basin, millstone, fan item processing, crushing wheels and saw cutting; no Create classes are linked from core Java.
- `FarmersDelightLearningHooks`: optional mixin bridge for completed cooking-pot and skillet recipes; output XP is credited to a nearby player with cooldown and daily cap.
- `ZombieAwarenessCompatibility`: reflection-only bridge forwards validated Aftermath noise events to Zombie Awareness' SOUND scent API; it fails closed when the mod or its API is unavailable.
- `MedicalCompatibilityService`: reports detected First Aid New / Model Injuries providers; the optional First Aid New Mixin bridges completed body-part treatments into Aftermath First Aid skill XP.
- `NoiseDefinitions`: validates data-driven noise events and horde archetypes on datapack reload.
- `NoiseService`: applies cooldowns and assigns only already-loaded, tagged mobs to a temporary noise destination, while optionally forwarding the event to Zombie Awareness. Firearms, footsteps and placed radio/alarm decoys all use this same entry point.
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

## Reload

Invalid definitions log a resource ID and error. Learning definitions currently reject individual malformed JSON files and publish the successfully loaded definitions; other systems should follow the same safe validation policy.
