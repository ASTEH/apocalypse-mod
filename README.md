# Aftermath: Survival Framework

Minecraft 1.21.1 · NeoForge · Java 21

**Aftermath** is a server-authoritative survival framework for a zombie-apocalypse modpack inspired by *Project Zomboid*. Its goal is to add character traits, weapon proficiency, learning through books and television, and configurable zombie meta-events without requiring Create, TaCZ, Farmer's Delight, or any other gameplay mod.

> **Status:** early development. The bootstrap build passed in GitHub Actions, and the maintainer confirmed the mod can start Minecraft and load a world after removing an unrelated failing test mod from the pack. The M1 profile-selection increment is pending its own CI build and in-game tests.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.x (initial development target: 21.1.264)
- Java 21
- No required gameplay mods

## Design principles

1. **Server authority.** Clients render UI and submit requests. The server validates choices, progress, XP, and events.
2. **Optional integrations.** The core must load with NeoForge alone. Integrations are isolated and guarded by mod-presence checks.
3. **Data-driven content.** Professions, traits, weapon classes, lessons, TV schedules, noise events, archetypes, and day progression are resource/datapack JSON definitions.
4. **No artificial hordes near players.** Noise attracts eligible, already-existing zombies; it does not spawn mobs beside a player.
5. **Bounded AI work.** Throttle target/path recalculation, avoid forced chunk loads, cap group sizes, and keep block breaking disabled by default.
6. **Compatibility first.** Never clear another mod's AI goals or overwrite its state wholesale. Integrations must have safe fallbacks.
7. **Validate before applying.** Invalid JSON definitions are reported and rejected; a single broken definition should not corrupt all loaded data.

## MVP roadmap

- [x] **M0 — Bootstrap foundation:** NeoForge entry point, server config, /aftermath status, and CI build.
- [ ] **M0 release checks:** clean NeoForge-only startup and dedicated-server verification.
- [ ] **M1 — Character profile:** profession + positive/negative traits, point budget, persisted profile, first-join screen, admin-configurable reopen. The current increment adds server-side profile storage, JSON definitions, validation, login hint, and command-based selection; the UI and trait effects remain to be implemented.
- [ ] **M2 — Weapon proficiency:** tags for melee/pistol/shotgun/rifle/heavy, server-side kill XP, levels, anti-farm limits, synced profile.
- [ ] **M3 — Learning:** 3 manuals, server-timed reading sessions, 1 registered TV block/program, saved player progress and client display.
- [ ] **M4 — Zombie groups:** configurable members/leaders and one server-side noise event; no artificial spawns.
- [ ] **M5 — Day progression & performance:** progression stages, goal throttling, bounded searches, optional door interaction, profiling.
- [ ] **M6 — Optional integrations:** TaCZ first; then other gunpacks and cooking/industry integrations as isolated addons or safe adapters.

## Initial data layout

Custom definitions use the aftermath namespace and standard datapack paths:

- data/aftermath/professions/*.json
- data/aftermath/traits/*.json
- data/aftermath/weapon_classes/*.json
- data/aftermath/lessons/*.json
- data/aftermath/tv_programs/*.json
- data/aftermath/metagame_events/*.json
- data/aftermath/zombie_archetypes/*.json
- data/aftermath/day_progression/*.json
- data/aftermath/tags/item/weapon_classes/*.json
- data/aftermath/tags/entity_type/horde_members.json

The exact schemas are documented in docs/DATA_FORMATS.md and versioned alongside code. The character-definition loader currently reads only the profession and trait folders.

## Build

Import the repository as a Gradle project in IntelliJ IDEA. Use Java 21.

    bash ./gradlew build

On Windows:

    .\gradlew.bat build

The bootstrap wrapper downloads the official Gradle Wrapper JAR on first launch if it is absent. See docs/BUILDING.md for details.

## Development policy

- Keep client-only classes out of common/server-loaded classes.
- Avoid mixins into other mods unless no event/API approach works and the integration is isolated and optional.
- Do not add Create, TaCZ, Curios, Cold Sweat, Farmer's Delight, or other third-party mods as required dependencies.
- Every MVP step should pass the build, start a dedicated test server, and have explicit manual test criteria before the next system is added.
- Unknown NeoForge 1.21.1 APIs must be checked against the MDK sources and official 1.21.1 docs; no guessed signatures.

See docs/BUILDING.md, docs/ARCHITECTURE.md, docs/COMPATIBILITY.md, and docs/MVP.md.
