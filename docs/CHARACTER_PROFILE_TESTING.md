# Character profile — manual test plan

These tests apply to the incremental M1 server-side profile implementation. The character-selection screen and gameplay effects for traits are not part of this increment yet.

## Install and inspect

1. Build using Java 21 with .\gradlew.bat build.
2. Copy the newly built build/libs/aftermath-0.1.0.jar into the test instance's mods folder, replacing the older jar.
3. Start a world with Aftermath enabled and inspect logs/latest.log for a message similar to Loaded 3 professions and 6 character traits.
4. Run /aftermath character list. It should list medic, mechanic, scavenger, and six traits with their point costs/credits.

## Validate selections

Run the following as a player who has not selected a profile:

- /aftermath character choose medic fit — should succeed; 5 points should remain.
- /aftermath character show — should show Medic and Fit.
- /aftermath character choose medic fit,frail — should fail because the traits conflict.
- /aftermath character choose medic fit,quick_learner,steady_hands — should fail because the total positive cost is greater than the starting budget.
- /aftermath character choose mechanic none — should choose Mechanic without traits for a player without an existing profile.

Use bare IDs in commands; namespaced IDs such as aftermath:medic and aftermath:fit are also supported.

## Persistence and permissions

1. Choose a profile.
2. Leave the world and rejoin it.
3. Run /aftermath character show; the selected profession and traits should remain.
4. Die and respawn; the profile should remain.
5. After a profile is complete, a normal player should not be able to choose again. An operator can reselect only when allowCharacterReselection is enabled in the server config.

## Expected limits in this increment

- Profile storage, selection validation and point accounting are server-authoritative.
- Trait effects are data-only at this stage; they do not yet change movement speed, health, weapon handling or learning.
- There is no client selection screen yet. Players use commands and receive a first-join reminder.
- Also verify a dedicated server before treating M1 as release-ready.
