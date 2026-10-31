# Stage 26 — Zombie camouflage and noise decoys

Player parkour is handled by the standalone [ParCool! mod for NeoForge 1.21.1](https://www.curseforge.com/minecraft/mc-mods/parcool/files/9205907), not by Aftermath. The 1.21.1-4.0.1.6 release requires [Curios API](https://www.curseforge.com/minecraft/mc-mods/curios/files/all). Install both mods on client and server. ParCool's skill tree can be disabled with `enable_skill_tree = false`; use its server config to disable actions that do not fit the apocalypse setting.

## Zombie camouflage

Craft **Zombie Camouflage Paste** using vanilla rotten flesh, bone meal and a slime ball. Right-click to consume it and gain **3 minutes** of camouflage.

The effect reduces visual target acquisition by eligible zombie-like mobs:

- At more than 5 blocks, a zombie that has line of sight has a 25% chance to detect the masked player on a target-acquisition attempt.
- Within 5 blocks, normal detection applies.
- Camouflage does not grant invisibility and does not suppress footsteps, weapon noise or other hearing-based attraction.
- Attacking, receiving damage or producing an accepted loud noise with effective radius at least 28 blocks removes the effect.
- Time is measured using the server's Overworld game time; clients cannot extend or activate the state.

The implementation reuses the existing zombie classifier and target-acquisition event. The existing variant sight range and hearing range remain authoritative.

## Rechargeable noise decoys

Two placeable devices are available:

| Device | Noise radius | Pulse interval |
|---|---:|---:|
| Radio | 72 blocks | 3 seconds |
| Alarm Clock | 44 blocks | 2 seconds |

Craft either device and place it in the world. Craft **Noise Decoy Batteries** from vanilla copper, iron nuggets and redstone. Each battery provides 3 minutes of runtime; a device stores up to 6 minutes.

Use a battery on the device to install charge. Right-click the device with an empty hand to turn it on or off. Devices continue to pulse until switched off or their stored charge runs out. Active state, battery time and a stable source identity are saved by the block entity.

Each pulse is sent to the existing `NoiseService.emitNoise` API through a reloadable event definition. The standard radius, target tag, cooldown, maximum affected entity count, zombie-specific hearing limits and `HordeDirector` path assignment remain in use. Only already-loaded entities carrying the existing `aftermath:horde_members` tag can be attracted. No entity is spawned and no chunk is force-loaded.

## Server configuration

These independent options are in the Aftermath server config:

```toml
[features]
enableZombieCamouflage = true
enableNoiseDecoys = true
```

Parkour is configured separately through ParCool. Disabling Aftermath noise events globally also stops the decoys from attracting mobs.

## Acceptance tests

1. Install ParCool! and Curios API on client and dedicated server. Verify vault, crawl, hang and wall-run actions; disable grapple/zipline or other actions not wanted in a survival apocalypse.
2. Optionally set `enable_skill_tree = false` in ParCool's server config and verify movement actions are immediately available.
3. Use Zombie Camouflage Paste; verify its tooltip and three-minute expiration.
4. Compare zombie detection at medium/long sight distance and within five blocks.
5. Verify attack, incoming damage and gunfire/loud noise end camouflage; footsteps and ordinary movement do not automatically remove it.
6. Place a radio and alarm clock, install batteries and toggle them on/off.
7. Verify radio/alarm noise profiles attract existing zombies only; no zombie is created at the decoy.
8. Let the charge run out, save/reload the world and verify remaining charge and active state persist.
9. Repeat with each Aftermath feature toggle disabled; ensure the disabled feature stops working.
10. Start a dedicated server with Aftermath alone (without ParCool) and verify no client-only classes are loaded.

## Current limits

- ParCool is maintained outside Aftermath. Its movement actions, stamina/skill-tree options and animation compatibility are configured by that mod; check its version-specific documentation when updating it.
- Camouflage modifies zombie target acquisition, not external mod AI that does not use the vanilla target event.
- The radio and alarm are vanilla-textured functional blocks; custom artwork and sound design can be refined separately.
- Decoys use the global NoiseService and its existing caps; very large numbers of devices remain constrained by the configured per-event target cap, but device tick load should still be profiled in a large server.
