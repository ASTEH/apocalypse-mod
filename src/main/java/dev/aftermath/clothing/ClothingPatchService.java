package dev.aftermath.clothing;

import dev.aftermath.AftermathMod;
import dev.aftermath.content.AftermathContent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.ItemStackedOnOtherEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * Small, persistent clothing reinforcement layer. The garment remains the original item:
 * all mod-specific components, enchantments, repair material and durability stay untouched.
 */
public final class ClothingPatchService {
    public static final int MAX_PATCHES_PER_ITEM = 3;
    private static final double ARMOR_PER_PATCH = 0.5D;
    private static final String ROOT_TAG = "aftermath";
    private static final String PATCH_COUNT_TAG = "clothing_patches";

    private ClothingPatchService() {}

    /** Add a small armor attribute bonus whenever a patched garment's modifiers are queried. */
    public static void onItemAttributeModifier(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        int patches = patchCount(stack);
        if (patches <= 0) return;

        // Broken equipment cannot provide its reinforcement. Repairing the original item
        // through its normal vanilla/modded repair path restores the patch bonus.
        if (stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage()) return;

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String uniquePath = "clothing_patch_" + itemId.getNamespace() + "_" + itemId.getPath().replace('/', '_');
        ResourceLocation modifierId = ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, uniquePath);
        event.addModifier(Attributes.ARMOR,
                new AttributeModifier(modifierId, ARMOR_PER_PATCH * patches,
                        AttributeModifier.Operation.ADD_VALUE),
                net.minecraft.world.entity.EquipmentSlotGroup.ARMOR);
    }

    /**
     * Right-click a wearable armor item in an inventory with a patch kit on the cursor.
     * The event is handled on both sides to suppress vanilla stack swapping, but all mutations
     * and consumption are performed only by the server.
     */
    public static void onItemStackedOnOther(ItemStackedOnOtherEvent event) {
        ItemStack carried = event.getCarriedItem();
        ItemStack garment = event.getStackedOnItem();
        if (carried.isEmpty() || garment.isEmpty()
                || !carried.is(AftermathContent.CLOTHING_PATCH.get())
                || event.getClickAction() != ClickAction.SECONDARY) {
            return;
        }

        Player player = event.getPlayer();
        EquipmentSlot equipmentSlot = player.getEquipmentSlotForItem(garment);
        if (equipmentSlot == null || !equipmentSlot.isArmor()) return;

        // This is our interaction, not a normal stack operation.
        event.setCanceled(true);
        if (player.level().isClientSide()) return;

        int current = patchCount(garment);
        if (current >= MAX_PATCHES_PER_ITEM) {
            player.sendSystemMessage(Component.translatable(
                    "message.aftermath.clothing_patches.maxed", MAX_PATCHES_PER_ITEM));
            return;
        }

        if (!player.getAbilities().instabuild) {
            ItemStack remaining = carried.copy();
            remaining.shrink(1);
            if (!event.getCarriedSlotAccess().set(remaining)) return;
        }

        int updated = current + 1;
        setPatchCount(garment, updated);
        event.getSlot().setChanged();
        player.containerMenu.broadcastChanges();
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.PLAYERS, 0.65F, 1.15F);
        player.sendSystemMessage(Component.translatable(
                "message.aftermath.clothing_patches.applied", updated, MAX_PATCHES_PER_ITEM));
    }

    /** Display both the patch count and the bonus, including for third-party armor items. */
    public static void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.is(AftermathContent.CLOTHING_PATCH.get())) {
            event.getToolTip().add(Component.translatable("tooltip.aftermath.clothing_patch.item"));
            return;
        }

        int patches = patchCount(stack);
        if (patches <= 0) return;
        event.getToolTip().add(Component.translatable(
                "tooltip.aftermath.clothing_patch.count", patches, MAX_PATCHES_PER_ITEM));
        event.getToolTip().add(Component.translatable(
                "tooltip.aftermath.clothing_patch.protection", ARMOR_PER_PATCH * patches));
        if (stack.isDamageableItem()) {
            int remaining = Math.max(0, stack.getMaxDamage() - stack.getDamageValue());
            event.getToolTip().add(Component.translatable(
                    "tooltip.aftermath.clothing_patch.durability", remaining, stack.getMaxDamage()));
        }
    }

    public static int patchCount(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null || !customData.contains(ROOT_TAG)) return 0;
        CompoundTag root = customData.copyTag();
        if (!root.contains(ROOT_TAG, Tag.TAG_COMPOUND)) return 0;
        return Math.max(0, Math.min(MAX_PATCHES_PER_ITEM,
                root.getCompound(ROOT_TAG).getInt(PATCH_COUNT_TAG)));
    }

    private static void setPatchCount(ItemStack stack, int count) {
        int safeCount = Math.max(0, Math.min(MAX_PATCHES_PER_ITEM, count));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, root -> {
            CompoundTag aftermath = root.getCompound(ROOT_TAG);
            if (safeCount == 0) {
                aftermath.remove(PATCH_COUNT_TAG);
            } else {
                aftermath.putInt(PATCH_COUNT_TAG, safeCount);
            }

            if (aftermath.isEmpty()) {
                root.remove(ROOT_TAG);
            } else {
                root.put(ROOT_TAG, aftermath);
            }
        });
    }
}
