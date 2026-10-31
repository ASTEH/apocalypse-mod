# Medical system integration

## Responsibilities

Aftermath does not create a parallel anatomy, bleeding timer, wound model or bandage item. The medical stack in the modpack is authoritative:

- **First Aid New** owns body-part health, injury debuffs, treatment items, medicine, unconsciousness and rescue.
- **Model Injuries** extends that model with fractures, organ/muscle/nerve injuries, blood and oxygen monitoring, disabled limbs, targeted treatment and additional recovery mechanics.
- **Hematic** is a visual blood-splatter/staining mod, not a medical authority.
- **Fracture Point** provides equipment and armor functionality; it does not replace the injury simulator.

Aftermath's old standalone bleeding attachment, bandage item, recipe, Moodle and configuration have been removed. This prevents an uncurable second bleeding system from running when the external medical stack is absent or configured differently. With no First Aid New installed, Aftermath reports the missing dependency rather than silently simulating injuries.

Use `/aftermath medical status` (or `/aftermath wounds`) to inspect provider detection. The Medic profession no longer starts with an Aftermath bandage. It retains its LRTactical AI2 and CarFAK medical supplies where available, with the existing vanilla golden-apple fallback.

## First Aid skill integration

When First Aid New is loaded, an optional Mixin observes the completed callback in its public `ItemHealing` treatment flow. After the original callback runs, Aftermath awards First Aid skill XP through the same capped progression service used by other skills. The regular skill XP multiplier and the Medic's Triage trait continue to apply. Interrupted treatments do not trigger the completion callback and do not grant XP.

This bridge is optional and loaded only when the `firstaid` mod ID is present. Aftermath does not compile against First Aid New's implementation packages and uses reflection only at the optional integration boundary.

## Moodles, stress and injury penalties

The existing Aftermath `damage_taken` state event still drives panic and stress when hostile damage reaches a player. Those Moodles continue to affect Aftermath gameplay effects such as firearm spread. First Aid New / Model Injuries remain responsible for anatomical injury penalties, limb restrictions, pain and movement/attack penalties. Aftermath does not add another generic limp or arm-injury penalty on top of them.

## Compatibility requirements

Model Injuries 0.41.0 lists Minecraft 1.21.1, NeoForge 21.1.257 or newer within 21.1, and First Aid New 1.3.3 as requirements. Aftermath currently targets NeoForge 21.1.264. Install identical First Aid New and Model Injuries versions on the server and every client.

## TaCZ and [TaCZ] First Aid Compat

Base TaCZ does not itself advertise First Aid's directional body-part integration. The separate `tacz_firstaid_compat` addon adds impact-position based body-part selection, weapon lockout while Near Death, and configurable instant death when the head is destroyed.

Model Injuries also advertises optional support for selected TaCZ versions. Its public release notes do not list every supported TaCZ build or state that it replaces all three features of `tacz_firstaid_compat`. Keep the addon for now; do not assume it is redundant just because TaCZ is installed.

However, both integrations can touch First Aid's damage pipeline. Test a low-damage shot to head, left/right arms and legs, then inspect the affected parts and confirm that each shot applies damage only once. If a shot damages multiple parts or doubles the damage, compare with `tacz_firstaid_compat` disabled and keep only the integration that behaves correctly with the exact TaCZ, First Aid New and Model Injuries versions in the pack.
