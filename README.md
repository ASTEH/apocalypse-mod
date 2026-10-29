# Aftermath: Survival Framework

Minecraft 1.21.1 · NeoForge · Java 21

**Aftermath** is a server-authoritative survival framework for a zombie-apocalypse modpack inspired by *Project Zomboid*. Its goal is to add character traits, weapon proficiency, learning through books and television, and configurable zombie meta-events without requiring Create, TaCZ, Farmer's Delight, or any other gameplay mod.

> **Status:** early development. The GitHub scaffold and MVP contracts are present; compilation and in-game verification are still pending. Features are implemented in small, testable increments.

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

- [ ] **M0 — Bootstrap:** NeoForge entry point, server config, command, CI build, empty-safe datapack loader.
- [ ] **M1 — Character profile:** profession + positive/negative traits, point budget, persisted profile, first-join screen, admin-configurable reopen.
- [ ] **M2 — Weapon proficiency:** tags for melee/pistol/shotgun/rifle/heavy, server-side kill XP, levels, anti-farm limits, synced profile.
- [ ] **M3 — Learning:** 3 manuals, server-timed reading sessions, 1 registered TV block/program, saved player progress and client display.
- [ ] **M4 — Zombie groups:** configurable members/leaders and one server-side noise event; no artificial spawns.
- [ ] **M5 — Day progression & performance:** progression stages, goal throttling, bounded searches, optional door interaction, profiling.
- [ ] **M6 — Optional integrations:** TaCZ first; then other gunpacks and cooking/industry integrations as isolated addons or safe adapters.

## Initial data layout

Custom definitions use the `data/aftermath/` namespace (not hard-coded Java IDs):

- `professions/*.json`
- `traits/*.json`
- `weapon_classes/*.json`
- `lessons/*.json`
- `tv_programs/*.json`
- `metagame_events/*.json`
- `zombie_archetypes/*.json`
- `day_progression/*.json`
- `tags/items/weapon_classes/*.json`
- `tags/entity_types/horde_members.json`

The exact schemas are documented in `docs/DATA_FORMATS.md` and versioned alongside code.

## Build

Import the repository as a Gradle project in IntelliJ IDEA. Use Java 21.

```bash
bash ./gradlew build
```

On Windows:

```powershell
.\gradlew.bat build
```

If a wrapper script is not present in a source checkout, run `gradle wrapper` once with Gradle installed, then use the generated wrapper.

## Development policy

- Keep client-only classes out of common/server-loaded classes.
- Avoid mixins into other mods unless no event/API approach works and the integration is isolated and optional.
- Do not add Create, TaCZ, Curios, Cold Sweat, Farmer's Delight, or other third-party mods as required dependencies.
- Every MVP step should pass the build, start a dedicated test server, and have explicit manual test criteria before the next system is added.
- Unknown NeoForge 1.21.1 APIs must be checked against the MDK sources and official 1.21.1 docs; no guessed signatures.

See [the architecture plan](docs/ARCHITECTURE.md), [compatibility matrix](docs/COMPATIBILITY.md), and [MVP checklist](docs/MVP.md).
