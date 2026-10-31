# Clothing patches (Stage 25)

Stage25 implements a small, persistent reinforcement for wearable armor/clothing. It deliberately does not add magazines, journals, stress systems or a parallel repair system.

## Materials and recipe

Craft **2 Clothing Patches** from:

- 1 white wool
- 2 string

Recipe: `data/aftermath/recipe/clothing_patch.json`

Both ingredients are vanilla Minecraft items. No extra gameplay mod or external library is required.

## Applying a patch

1. Open any inventory screen.
2. Pick up a Clothing Patch with the mouse cursor.
3. Right-click a wearable armor/clothing item in an inventory slot.
4. The patch is consumed and the garment's patch count increases by one.

The interaction is server-authoritative. It only handles right-click stacking onto a wearable equipment item; normal inventory interactions with other items remain vanilla.

The handler reads the cursor through the event's carried-slot accessor and the target through the clicked slot. This avoids a known NeoForge 1.21.1 ItemStackedOnOtherEvent argument-order issue, where the two stack fields may be reversed.

The initial cap is **3 patches per garment**. The cap is stored on the garment and cannot be bypassed by repeatedly using patches or by relogging.

## Protection

Each patch adds **+0.5 armor** while that garment is equipped, for a maximum of **+1.5 armor** on one garment. The existing item remains the actual equipped item; Aftermath adds an attribute modifier to its stack when the equipment attributes are queried.

The service uses the item's equipment slot as resolved by the wearer, rather than checking only for vanilla `ArmorItem`. This allows third-party clothing/armor that advertises a normal humanoid armor slot to participate. Items that are not recognized as head, chest, legs or feet equipment are not patchable. Some mods manage protection entirely outside Minecraft's item attribute system; those items may not display or apply this bonus and still need an in-game compatibility test.

## Durability and repair

- The garment keeps its original durability, enchantments, attributes and data components. The patch count is stored under Aftermath's own `CUSTOM_DATA` namespace.
- The garment continues to take and display normal vanilla/modded durability damage.
- A broken/destroyed garment cannot provide an armor attribute bonus. The patch count is not transferred to a replacement item.
- Normal vanilla/modded anvil repair of the original garment is retained. Repairing the garment does not intentionally remove its patch metadata; the item regains its usual protection plus the patch bonus when usable again.
- No new repair station, repair item, durability pool or medical behavior is introduced.

## Configuration and tuning

The current implementation uses code constants:

- Maximum patches per item: 3.
- Armor granted by each patch: 0.5.
- Total maximum extra armor on one item: 1.5.

The bonus is additive to the item's existing armor attribute. It does not increase toughness, knockback resistance or durability.

## Acceptance tests still requiring an in-game run

1. Craft the patch kit from wool + string and verify the output count is 2.
2. Right-click a vanilla helmet, chestplate, leggings and boots in the inventory with a patch kit on the cursor; verify one patch is consumed and the item's tooltip updates.
3. Verify the armor attribute bonus is +0.5 per patch and stops at three patches (+1.5).
4. Attempt a fourth patch; verify it is not consumed and the cap message is shown.
5. Verify the original item, enchantments, custom name, dye, other custom data and remaining durability are preserved.
6. Equip patched items and verify the bonus applies; remove them and verify the bonus disappears.
7. Repair a patched garment using its normal vanilla anvil material; verify patch count and bonus remain.
8. Damage/break the garment; verify normal durability behavior and that a destroyed item does not transfer patches.
9. Test suitable armor/clothing items from the modpack, especially items that use non-vanilla attribute systems. Record unsupported items rather than forcing a dependency or rewriting their armor behavior.
10. Test multiplayer and dedicated-server behavior; ensure the server alone consumes patches and changes patch metadata.

## Explicitly out of scope

- Stage24 body-condition panel: intentionally skipped because the installed modpack already provides that UI.
- HottieZ/HunkZ and all other journal items.
- Stress, boredom, moodles, medical treatment, bleeding or bandages.
- Any replacement of third-party armor durability or repair mechanics.
