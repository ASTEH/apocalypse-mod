# Medical system integration

## Supported providers

Aftermath does not create a second anatomy, blood or injury simulator when a
full external medical stack is installed.

| Installed mods | Provider | Aftermath behavior |
|---|---|---|
| First Aid New + Model Injuries | Model Injuries over First Aid New | The old severity-only Aftermath bleeding timer and Aftermath bandage logic are skipped |
| First Aid New only | First Aid New for body-part health; Aftermath for fallback bleeding | The external body model stays active; fallback bleeding can be disabled in server config |
| Neither | Aftermath | Uses the standalone bleeding attachment and bandage |

The external mode is enabled by default when both mods are detected. Set
`preferExternalMedicalSystem=false` in the Aftermath server config to force
the legacy fallback for comparison. This is not recommended alongside Model
Injuries because it creates a second blood-loss source.

When external mode is active, a persisted legacy `aftermath:bleeding_status`
is cleared on the next server player tick. It does not alter First Aid or
Model Injuries' own saved data. This prevents old Aftermath bleeding from
resuming if the external mods are later removed.

Use `/aftermath medical status` to see the selected provider. The old
`/aftermath wounds` command now points to the First Aid body inspection
screen when Model Injuries is active. The Aftermath bandage item is not
consumed in that mode and directs the player to the First Aid screen (I).

## Responsibilities

- First Aid New provides locational health, injury debuffs, medicine,
  unconsciousness and rescue.
- Model Injuries extends the First Aid model with fractures, organ/muscle/nerve
  injuries, bleeding, blood/oxygen monitoring, disabled limbs and targeted
  treatment.
- Hematic is a visual blood splatter/staining mod, not a medical authority.
- Fracture Point is equipment/armor functionality; it does not replace the
  First Aid injury simulator.

Model Injuries 0.41.0 lists Minecraft 1.21.1, NeoForge 21.1.257 or newer
within 21.1, and First Aid New 1.3.3 as requirements. Aftermath currently
targets NeoForge 21.1.264, which satisfies that loader minimum. Both
Model Injuries and First Aid must be installed on the server and every client.

## TaCZ and [TaCZ] First Aid Compat

Base TaCZ does not itself advertise First Aid's directional body-part
integration. The separate `tacz_firstaid_compat` addon adds impact-position
based body-part selection, weapon lockout while Near Death, and configurable
instant death when the head is destroyed.

Model Injuries also advertises optional support for selected TaCZ versions.
Its public release notes do not list every supported TaCZ build or state that
it replaces all three features of `tacz_firstaid_compat`. Keep the addon for
now; do not assume it is redundant just because TaCZ is installed.

However, both integrations can touch First Aid's damage pipeline. Test a
low-damage shot to head, left/right arms and legs, then inspect the affected
parts and confirm that each shot applies damage only once. If a shot damages
multiple parts or doubles the damage, compare with `tacz_firstaid_compat`
disabled and keep only the integration that behaves correctly with the exact
TaCZ, First Aid New and Model Injuries versions in the pack.

## Scope

This integration selects a single blood-loss authority and preserves the
standalone fallback. It does not claim to replace the injury model supplied
by First Aid New / Model Injuries or guarantee their optional TaCZ mixins work
together. The number and names of anatomical substructures are owned by those
mods and should be checked in their body inspection UI before building any
parallel Aftermath anatomy.
