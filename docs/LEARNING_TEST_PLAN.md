# Learning and compatibility test plan

This checklist is for the Aftermath branch targeting Minecraft 1.21.1, NeoForge 21.1.264 and Java 21. It is a manual in-game plan; the code changes have not been built or runtime-tested in this repository session.

## Startup and optional dependencies

- Start a dedicated server with Aftermath only. Confirm that the game loads, /aftermath learning works, and no Create, Farmer's Delight, LRTactical or Zombie Awareness class is required.
- Start once with each optional mod installed. Confirm that the matching integration activates without changing behavior when the other optional mods are absent.
- Reload datapacks and check the log for successful learning definitions: eight skills, the configured books, TV programmes and VHS tapes.

## Skills and daily balance

- Gain XP from at least two different sources for the same skill during one in-game day. Confirm that their combined reward does not exceed 120 XP for that skill.
- Apply a skill book and a profession XP trait, then repeat the test. Multipliers should increase rewards only until the shared 120 XP cap is reached.
- Advance the in-game day and confirm that the global daily budget resets.
- Confirm that action-specific cooldowns and caps still apply, even when the global skill budget has room.

## Scavenging

- Open a fresh loot-table chest in a Lost Cities area. Merely opening it must not award XP.
- Transfer an item into the player inventory, including by shift-click and by cursor placement. Confirm that the first successful transfer awards 5 Scavenging XP.
- Let a second player open the same generated loot chest later and transfer a remaining item. The saved block-entity marker should still identify it as a loot container.
- Restart the server, then repeat the second-player transfer test with remaining contents. The marker should survive the world save.
- Open an ordinary player-placed chest or barrel. Opening it and moving its contents must not award Scavenging XP.
- Test a second interaction with the off-hand. It must not start or cancel the main-hand loot tracking session.
- Confirm that closing the menu, changing dimension or logging out clears the temporary tracking session.

## Create 6.0.10

With an active learner within 16 blocks, process valid recipes through a mechanical press on a belt, basin mixer/press, millstone, fan, crushing wheels and mechanical saw. Confirm Mechanics XP is awarded only after a successful output and respects the action cooldown and 80 XP/day action cap. Also check that a player outside the 16-block radius receives nothing.

## Farmer's Delight

- Complete a valid cooking-pot recipe and remove the resulting meal.
- Complete a valid skillet recipe and collect the spawned output.
- Confirm each completed machine recipe can grant Cooking XP, while invalid recipes, unheated machines and idle ticks do not.

## Model Injuries and LRTactical

- Complete a valid Model Injuries TreatmentItem procedure on a selected body region. Confirm XP is awarded only if Model Injuries accepts the treatment; an invalid treatment must not grant XP.
- Complete a valid Model Injuries MedicineItem use. Confirm the medicine is accepted and XP is awarded once.
- Try an already-active/rejected medicine. Confirm it grants no XP.
- Confirm one successful procedure produces one XP award, never two, and the status callback/XP counters each increase by one.
- Repeat with the Medic's Triage trait and confirm its 25% skill XP multiplier still applies under the 80 XP action cap and shared 120 XP skill cap.
- Use a LRTactical consumable whose configured data heals or removes a negative effect. Confirm First Aid XP is awarded after its server-side consumable event.
- Test a non-medical LRTactical consumable to confirm it grants no First Aid XP.
- Test without Model Injuries; its optional mixins must not load, and Aftermath must not reintroduce a standalone bandage/bleeding system.

## Zombie Awareness

Fire a supported TaCZ weapon while Zombie Awareness is installed. Confirm that the optional SOUND-scent API is called and that Zombie Awareness mobs can react to the sound. Repeat without Zombie Awareness; Aftermath's own noise/horde behavior must continue.

## Vehicles and study materials

- Drive an MTS vehicle and an Immersive Vehicles vehicle for at least 16 blocks during a 200-tick sample window. Confirm Driving XP is awarded with cooldown and daily cap.
- Read each of the eight skill manuals at its required skill level. Confirm the relevant temporary multiplier and reading interruption behavior.
- Test scheduled TV playback and all eight VHS tapes: field medicine, cooking basics, farmer's corner, mechanic workshop, range safety, driving basics, urban scavenging and close-quarters survival. Confirm successful playback grants the correct skill XP and VHS consumption behavior.
