# Stage 26 — Parkour, zombie camouflage and noise decoys

## Parkour controls

The default key is **V** (Options → Controls → Aftermath → Parkour). It is rebindable.

- Move while sprinting and press V to request a vault or mantle.
- The server derives direction from actual server-side movement. The packet contains no coordinates.
- Short vaults can clear low obstacles; mantles can land on a sturdy surface up to approximately one block higher.
- Hold crouch and press V to traverse an opening that fits the crouching player box but not the standing box.
- The player must be on the ground and moving; parkour is not a flight or wall-clipping ability.

The server samples the complete player bounding box along the route and checks collision at every sample. The destination must be in an already-loaded chunk and have a sturdy landing surface. Solid walls, unsupported ledges, arbitrary positions and unloaded chunks are rejected. A short server cooldown prevents repeated packet use.

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

These independent options are in the server config:

```toml
[features]
enableParkour = true
enableZombieCamouflage = true
enableNoiseDecoys = true
```

All three features can be disabled independently. Disabling noise events globally also stops the decoys from attracting mobs.

## Acceptance tests

1. Sprint over low obstacles and press V; test fence/rail vaulting and short ledge mantles.
2. Try V against a full solid wall and toward an unsupported edge; no movement must occur.
3. Crouch and press V at suitable openings; verify the player cannot use it to pass through solid block collision.
4. Test network latency/multiplayer: the server, not the client, determines the destination.
5. Use Zombie Camouflage Paste; verify its tooltip and three-minute expiration.
6. Compare zombie detection at medium/long sight distance and within five blocks.
7. Verify attack, incoming damage and gunfire/loud noise end camouflage; footsteps and ordinary movement do not automatically remove it.
8. Place a radio and alarm clock, install batteries and toggle them on/off.
9. Verify radio/alarm noise profiles attract existing zombies only; no zombie is created at the decoy.
10. Let the charge run out, save/reload the world and verify remaining charge and active state persist.
11. Repeat with each server feature toggle disabled; ensure no unsupported behavior remains active.
12. Start a dedicated server with Aftermath alone and verify no client-only classes are loaded.

## Current limits

- Parkour is a geometry-validated baseline, not a full animation or stamina system. It intentionally does not attempt arbitrary climbing or collision bypass.
- Camouflage modifies zombie target acquisition, not external mod AI that does not use the vanilla target event.
- The radio and alarm are vanilla-textured functional blocks; custom artwork and sound design can be refined separately.
- Decoys use the global NoiseService and its existing caps; very large numbers of devices remain constrained by the configured per-event target cap, but device tick load should still be profiled in a large server.
