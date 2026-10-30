package dev.aftermath.character;

import com.google.gson.JsonPrimitive;
import dev.aftermath.AftermathMod;
import dev.aftermath.config.AftermathServerConfig;
import dev.aftermath.network.CharacterSelectionResultPayload;
import dev.aftermath.network.SelectCharacterPayload;
import dev.aftermath.network.TraitStatePayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Validates and applies character choices on the logical server only. */
public final class CharacterSelectionService {
    public static void handleSelection(SelectCharacterPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;

        if (!AftermathServerConfig.ENABLE_CHARACTER_CREATION.get()) {
            respond(player, false, "screen.aftermath.character.error.disabled");
            return;
        }

        CharacterProfile existing = player.getData(AftermathAttachments.CHARACTER_PROFILE);
        if (existing.created()) {
            respond(player, false, "screen.aftermath.character.error.already_created");
            return;
        }

        CharacterDefinitions.Snapshot definitions = CharacterDefinitions.snapshot();
        ResourceLocation professionId = ResourceLocation.tryParse(payload.professionId());
        CharacterDefinitions.ProfessionDefinition profession =
                professionId == null ? null : definitions.professions().get(professionId);
        if (profession == null) {
            respond(player, false, "screen.aftermath.character.error.invalid_profession");
            return;
        }

        String csv = payload.traitIdsCsv() == null ? "" : payload.traitIdsCsv();
        String[] rawIds = csv.isBlank() ? new String[0] : csv.split(",", -1);
        if (rawIds.length > 32) {
            respond(player, false, "screen.aftermath.character.error.too_many_traits");
            return;
        }

        Set<ResourceLocation> selectedIds = new LinkedHashSet<>();
        List<CharacterDefinitions.TraitDefinition> selectedTraits = new ArrayList<>();
        for (ResourceLocation grantedId : profession.grantedTraits()) {
            CharacterDefinitions.TraitDefinition granted = definitions.traits().get(grantedId);
            if (granted == null || !selectedIds.add(grantedId)) {
                respond(player, false, "screen.aftermath.character.error.invalid_trait");
                return;
            }
            selectedTraits.add(granted);
        }
        for (String rawId : rawIds) {
            ResourceLocation traitId = ResourceLocation.tryParse(rawId);
            if (traitId == null || !selectedIds.add(traitId)) {
                respond(player, false, "screen.aftermath.character.error.invalid_trait");
                return;
            }
            if (!profession.allowedTraits().isEmpty() && !profession.allowedTraits().contains(traitId)) {
                respond(player, false, "screen.aftermath.character.error.trait_not_allowed");
                return;
            }
            CharacterDefinitions.TraitDefinition trait = definitions.traits().get(traitId);
            if (trait == null) {
                respond(player, false, "screen.aftermath.character.error.invalid_trait");
                return;
            }
            if (trait.requiresProfession() != null && !trait.requiresProfession().equals(profession.id())) {
                respond(player, false, "screen.aftermath.character.error.profession_required");
                return;
            }
            selectedTraits.add(trait);
        }

        for (CharacterDefinitions.TraitDefinition trait : selectedTraits) {
            for (ResourceLocation conflict : trait.conflicts()) {
                if (selectedIds.contains(conflict)) {
                    respond(player, false, "screen.aftermath.character.error.conflicting_traits");
                    return;
                }
            }
        }

        int remainingPoints = profession.startingPoints();
        for (CharacterDefinitions.TraitDefinition trait : selectedTraits) {
            if (profession.grantedTraits().contains(trait.id())) continue;
            remainingPoints += trait.positive() ? -trait.pointCost() : trait.pointCredit();
        }
        if (remainingPoints < 0) {
            respond(player, false, "screen.aftermath.character.error.not_enough_points");
            return;
        }

        List<String> savedTraits = selectedTraits.stream().map(trait -> trait.id().toString()).toList();
        player.setData(AftermathAttachments.CHARACTER_PROFILE,
                new CharacterProfile(profession.id().toString(), savedTraits, true, profession.skillBonuses()));
        PacketDistributor.sendToPlayer(player, new TraitStatePayload(String.join(",", savedTraits)));

        CharacterEffectApplier.apply(player, selectedTraits);
        grantStartingItems(player, profession);
        AftermathMod.LOGGER.info("Player {} created an Aftermath character: profession={}, traits={}",
                player.getGameProfile().getName(), profession.id(), savedTraits);
        respond(player, true, "screen.aftermath.character.success");
    }

    public static void reapplyProfile(ServerPlayer player) {
        CharacterProfile profile = player.getData(AftermathAttachments.CHARACTER_PROFILE);
        if (!profile.created()) return;
        List<CharacterDefinitions.TraitDefinition> traits = profile.traitIds().stream()
                .map(ResourceLocation::tryParse)
                .filter(id -> id != null)
                .map(CharacterDefinitions.snapshot().traits()::get)
                .filter(trait -> trait != null)
                .toList();
        CharacterEffectApplier.apply(player, traits);
    }

    private static void grantStartingItems(ServerPlayer player,
                                           CharacterDefinitions.ProfessionDefinition profession) {
        for (CharacterDefinitions.StartingItem startingItem : profession.startingItems()) {
            ResolvedStartingItem resolved = resolveStartingItem(player, startingItem);
            if (resolved == null) {
                AftermathMod.LOGGER.warn("Skipping unresolved starting item {} (mod={}, fallback={}) for profession {}.",
                        startingItem.itemReference(), startingItem.modId(), startingItem.fallbackReference(), profession.id());
                continue;
            }

            int stackCount = resolved.primaryReference()
                    ? startingItem.count() : startingItem.fallbackCount();
            ItemStack stack = new ItemStack(resolved.item(), stackCount);
            if (resolved.primaryReference()) {
                applyCustomData(stack, startingItem.customData());
            }
            if (!player.getInventory().add(stack) && !stack.isEmpty()) {
                player.drop(stack, false);
            }
        }
    }

    private static ResolvedStartingItem resolveStartingItem(
            ServerPlayer player, CharacterDefinitions.StartingItem definition) {
        if (definition.modId().isBlank()) {
            Item item = resolveItemReference(player, definition.itemReference());
            return item == null ? null : new ResolvedStartingItem(item, true);
        }

        if (ModList.get().isLoaded(definition.modId())) {
            Item modItem = resolveItemReference(player, definition.itemReference());
            if (modItem != null) return new ResolvedStartingItem(modItem, true);
            AftermathMod.LOGGER.warn("Optional item {} from loaded mod {} is unavailable; using fallback {}.",
                    definition.itemReference(), definition.modId(), definition.fallbackReference());
        } else {
            AftermathMod.LOGGER.debug("Optional mod {} is not loaded; using starting-item fallback {}.",
                    definition.modId(), definition.fallbackReference());
        }

        Item fallback = resolveItemReference(player, definition.fallbackReference());
        return fallback == null ? null : new ResolvedStartingItem(fallback, false);
    }

    private static Item resolveItemReference(ServerPlayer player, String reference) {
        if (reference == null || reference.isBlank()) return null;
        if (reference.startsWith("#")) {
            ResourceLocation tagId = ResourceLocation.tryParse(reference.substring(1));
            if (tagId == null) return null;
            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagId);
            List<Item> candidates = BuiltInRegistries.ITEM.stream()
                    .filter(item -> item != Items.AIR)
                    .filter(item -> item.builtInRegistryHolder().is(tagKey))
                    .toList();
            if (candidates.isEmpty()) {
                AftermathMod.LOGGER.warn("Item tag {} contains no registered items.", reference);
                return null;
            }
            return candidates.get(player.getRandom().nextInt(candidates.size()));
        }

        ResourceLocation itemId = ResourceLocation.tryParse(reference);
        if (itemId == null) return null;
        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        return item == Items.AIR ? null : item;
    }

    private static void applyCustomData(ItemStack stack, java.util.Map<String, JsonPrimitive> customData) {
        if (customData.isEmpty()) return;
        CompoundTag tag = new CompoundTag();
        customData.forEach((key, value) -> {
            if (value.isString()) {
                tag.putString(key, value.getAsString());
            } else if (value.isBoolean()) {
                tag.putBoolean(key, value.getAsBoolean());
            } else if (value.isNumber()) {
                BigDecimal number = value.getAsBigDecimal();
                try {
                    tag.putInt(key, number.intValueExact());
                } catch (ArithmeticException outsideIntRangeOrFractional) {
                    try {
                        tag.putLong(key, number.longValueExact());
                    } catch (ArithmeticException outsideLongRangeOrFractional) {
                        tag.putDouble(key, number.doubleValue());
                    }
                }
            }
        });
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private record ResolvedStartingItem(Item item, boolean primaryReference) {}

    private static void respond(ServerPlayer player, boolean success, String messageKey) {
        PacketDistributor.sendToPlayer(player, new CharacterSelectionResultPayload(success, messageKey));
    }

    private CharacterSelectionService() {}
}
