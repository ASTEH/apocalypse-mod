package dev.aftermath.character;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/** Applies only explicitly allow-listed, server-side trait effects. */
public final class CharacterEffectApplier {
    public static void apply(ServerPlayer player, List<CharacterDefinitions.TraitDefinition> traits) {
        for (CharacterDefinitions.TraitDefinition trait : traits) {
            for (CharacterDefinitions.TraitEffect effect : trait.effects()) {
                if (!effect.type().equals("attribute_multiplier")) continue;
                Holder<Attribute> attribute = switch (effect.target()) {
                    case "movement_speed" -> Attributes.MOVEMENT_SPEED;
                    case "max_health" -> Attributes.MAX_HEALTH;
                    case "attack_damage" -> Attributes.ATTACK_DAMAGE;
                    default -> null;
                };
                if (attribute == null) continue;

                AttributeInstance instance = player.getAttribute(attribute);
                if (instance == null) continue;
                ResourceLocation modifierId = ResourceLocation.fromNamespaceAndPath(
                        "aftermath", "character/" + trait.id().getPath() + "_" + effect.target());
                instance.removeModifier(modifierId);
                instance.addPermanentModifier(new AttributeModifier(
                        modifierId, effect.value() - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
        }
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
    }

    private CharacterEffectApplier() {}
}
