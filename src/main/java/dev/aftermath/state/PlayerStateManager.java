package dev.aftermath.state;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import dev.aftermath.AftermathMod;
import dev.aftermath.character.AftermathAttachments;
import dev.aftermath.character.CharacterProfile;
import dev.aftermath.config.AftermathServerConfig;
import dev.aftermath.network.MoodlesSyncPayload;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.LinkedHashMap;
import java.util.Map;

/** Server-side simulation, persistence and synchronization for player Moodles. */
public final class PlayerStateManager {
    private static final Gson GSON = new Gson();
    private static final TagKey<EntityType<?>> MOODLE_THREATS = TagKey.create(
            Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "moodle_threats"));
    private static final ResourceLocation MOVEMENT_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "moodle_movement_speed");
    private static final ResourceLocation ATTACK_SPEED_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "moodle_attack_speed");

    private PlayerStateManager() {}

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!AftermathServerConfig.ENABLE_MOODLES.get()) {
            if (player.level().getGameTime() % 20L == 0L) {
                clearGameplayModifiers(player);
                sync(player);
            }
            return;
        }

        PlayerStateDefinitions.Snapshot definitions = PlayerStateDefinitions.snapshot();
        if (definitions.states().isEmpty()) return;

        PlayerStateData before = player.getData(AftermathAttachments.PLAYER_STATES);
        Map<String, Double> values = new LinkedHashMap<>(before.values());
        boolean changed = false;

        for (PlayerStateDefinition definition : definitions.states().values()) {
            String id = definition.id().toString();
            if (!values.containsKey(id)) {
                values.put(id, definition.initialValue());
                changed = true;
            }
        }

        long gameTime = player.level().getGameTime();
        for (PlayerStateDefinition definition : definitions.states().values()) {
            if (gameTime % definition.updateIntervalTicks() != 0L) continue;
            String id = definition.id().toString();
            double current = values.getOrDefault(id, definition.initialValue());
            double delta = 0.0D;

            for (PlayerStateDefinition.Source source : definition.sources()) {
                delta += evaluatePeriodicSource(player, source, definition, values);
            }
            for (PlayerStateDefinition.Source source : definition.recovery()) {
                delta += evaluatePeriodicSource(player, source, definition, values);
            }

            double next = definition.clamp(current + delta);
            if (Math.abs(next - current) > 0.000001D) {
                values.put(id, next);
                changed = true;
            }
        }

        PlayerStateData current = changed ? new PlayerStateData(values) : before;
        if (changed) player.setData(AftermathAttachments.PLAYER_STATES, current);
        if (changed || gameTime % 20L == 0L) sync(player, current, definitions);

        if (gameTime % 20L == 0L) applyGameplayEffects(player, definitions, current);
    }

    private static double evaluatePeriodicSource(ServerPlayer player, PlayerStateDefinition.Source source,
                                                  PlayerStateDefinition definition, Map<String, Double> values) {
        double intensity = switch (source.type()) {
            case "awake_time" -> player.isSleeping() ? 0.0D : 1.0D;
            case "sleeping" -> player.isSleeping() ? 1.0D : 0.0D;
            case "sprinting" -> player.isSprinting() ? 1.0D : 0.0D;
            case "resting" -> !player.isSleeping() && player.onGround()
                    && player.getDeltaMovement().horizontalDistanceSqr() < 0.0004D ? 1.0D : 0.0D;
            case "walking" -> !player.isSprinting()
                    && player.getDeltaMovement().horizontalDistanceSqr() >= 0.0004D ? 1.0D : 0.0D;
            case "near_tagged_hostile" -> countThreats(player, source.radius(), source.maxEntities());
            case "no_nearby_hostiles" -> countThreats(player, source.radius(), 1) == 0 ? 1.0D : 0.0D;
            default -> 0.0D;
        };

        double delta = source.deltaPerSecond() * intensity
                * (definition.updateIntervalTicks() / 20.0D);
        if (delta > 0.0D) {
            delta *= interactionMultiplier(definition, values);
        }
        delta *= traitMultiplier(player, source);
        return delta;
    }

    private static int countThreats(ServerPlayer player, double radius, int limit) {
        if (radius <= 0.0D || limit <= 0) return 0;
        AABB area = player.getBoundingBox().inflate(radius);
        return Math.min(limit, player.level().getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity.isAlive() && entity instanceof Enemy && entity.getType().is(MOODLE_THREATS)).size());
    }

    private static double interactionMultiplier(PlayerStateDefinition definition, Map<String, Double> values) {
        double multiplier = 1.0D;
        for (PlayerStateDefinition.Interaction interaction : definition.interactions()) {
            double sourceValue = values.getOrDefault(interaction.sourceState(), 0.0D);
            if (compare(interaction.operator(), sourceValue, interaction.threshold())) {
                multiplier *= interaction.multiplier();
            }
        }
        return Math.max(0.0D, Math.min(4.0D, multiplier));
    }

    private static boolean compare(String operator, double value, double threshold) {
        return switch (operator) {
            case "gte" -> value >= threshold;
            case "gt" -> value > threshold;
            case "lte" -> value <= threshold;
            case "lt" -> value < threshold;
            case "eq" -> Math.abs(value - threshold) < 0.0001D;
            default -> false;
        };
    }

    private static double traitMultiplier(ServerPlayer player, PlayerStateDefinition.Source source) {
        if (source.traitMultipliers().isEmpty()) return 1.0D;
        CharacterProfile profile = player.getData(AftermathAttachments.CHARACTER_PROFILE);
        if (!profile.created()) return 1.0D;
        double result = 1.0D;
        for (String traitId : profile.traitIds()) {
            Double multiplier = source.traitMultipliers().get(traitId);
            if (multiplier != null) result *= multiplier;
        }
        return Math.max(0.0D, Math.min(4.0D, result));
    }

    /** Applies one-shot actions such as a melee hit or taking hostile damage. */
    public static void triggerEvent(ServerPlayer player, String eventType) {
        if (!AftermathServerConfig.ENABLE_MOODLES.get()) return;
        PlayerStateDefinitions.Snapshot definitions = PlayerStateDefinitions.snapshot();
        PlayerStateData before = player.getData(AftermathAttachments.PLAYER_STATES);
        Map<String, Double> values = new LinkedHashMap<>(before.values());
        boolean changed = false;

        for (PlayerStateDefinition definition : definitions.states().values()) {
            double current = values.getOrDefault(definition.id().toString(), definition.initialValue());
            double delta = 0.0D;
            for (PlayerStateDefinition.Source source : definition.sources()) {
                if (source.type().equals(eventType)) {
                    delta += source.deltaPerEvent() * traitMultiplier(player, source);
                }
            }
            for (PlayerStateDefinition.Source source : definition.recovery()) {
                if (source.type().equals(eventType)) {
                    delta += source.deltaPerEvent() * traitMultiplier(player, source);
                }
            }
            if (delta != 0.0D) {
                double next = definition.clamp(current + delta);
                values.put(definition.id().toString(), next);
                changed |= Math.abs(next - current) > 0.000001D;
            }
        }

        if (changed) {
            PlayerStateData updated = new PlayerStateData(values);
            player.setData(AftermathAttachments.PLAYER_STATES, updated);
            sync(player, updated, definitions);
            applyGameplayEffects(player, definitions, updated);
        }
    }

    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (event.getSource().getEntity() instanceof ServerPlayer attacker
                && event.getSource().getDirectEntity() == attacker
                && event.getEntity() != attacker) {
            double meleeMultiplier = effect(attacker, "melee_damage_multiplier", 1.0D);
            if (Math.abs(meleeMultiplier - 1.0D) > 0.0001D) {
                event.setNewDamage((float) (event.getNewDamage() * meleeMultiplier));
            }
            triggerEvent(attacker, "melee_hit");
        }

        if (event.getEntity() instanceof ServerPlayer victim
                && event.getNewDamage() > 0.0F
                && event.getSource().getEntity() instanceof Enemy) {
            triggerEvent(victim, "damage_taken");
        }
    }

    public static void initializeAndSync(ServerPlayer player) {
        if (!AftermathServerConfig.ENABLE_MOODLES.get()) {
            clearGameplayModifiers(player);
            sync(player);
            return;
        }
        PlayerStateDefinitions.Snapshot definitions = PlayerStateDefinitions.snapshot();
        PlayerStateData before = player.getData(AftermathAttachments.PLAYER_STATES);
        Map<String, Double> values = new LinkedHashMap<>(before.values());
        boolean changed = false;
        for (PlayerStateDefinition definition : definitions.states().values()) {
            if (!values.containsKey(definition.id().toString())) {
                values.put(definition.id().toString(), definition.initialValue());
                changed = true;
            }
        }
        PlayerStateData current = changed ? new PlayerStateData(values) : before;
        if (changed) player.setData(AftermathAttachments.PLAYER_STATES, current);
        sync(player, current, definitions);
        applyGameplayEffects(player, definitions, current);
    }

    public static void sync(ServerPlayer player) {
        sync(player, player.getData(AftermathAttachments.PLAYER_STATES), PlayerStateDefinitions.snapshot());
    }

    private static void sync(ServerPlayer player, PlayerStateData data,
                             PlayerStateDefinitions.Snapshot definitions) {
        JsonArray entries = new JsonArray();
        if (AftermathServerConfig.ENABLE_MOODLES.get()) {
            for (PlayerStateDefinition definition : definitions.states().values()) {
                double value = data.get(definition.id().toString(), definition.initialValue());
                PlayerStateDefinition.Level level = definition.levelFor(value);
                if (level == null) continue;

                JsonObject entry = new JsonObject();
                entry.addProperty("id", definition.id().toString());
                entry.addProperty("name_key", definition.nameKey());
                entry.addProperty("description_key", definition.descriptionKey());
                entry.addProperty("level_id", level.id());
                entry.addProperty("level_name_key", level.nameKey());
                entry.addProperty("level_description_key", level.descriptionKey());
                entry.addProperty("icon", level.icon().toString());
                entry.addProperty("value", value);
                entry.addProperty("severity", level.priority());
                JsonObject effects = new JsonObject();
                level.effects().forEach(effects::addProperty);
                entry.add("effects", effects);
                entries.add(entry);
            }
        }
        PacketDistributor.sendToPlayer(player, new MoodlesSyncPayload(GSON.toJson(entries)));
    }

    private static void applyGameplayEffects(ServerPlayer player,
                                             PlayerStateDefinitions.Snapshot definitions,
                                             PlayerStateData data) {
        double movement = effect(data, definitions, "movement_speed_multiplier", 1.0D);
        double attackSpeed = effect(data, definitions, "attack_speed_multiplier", 1.0D);
        setMultiplier(player, Attributes.MOVEMENT_SPEED, MOVEMENT_MODIFIER, movement);
        setMultiplier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_MODIFIER, attackSpeed);

        if (effect(data, definitions, "sprint_allowed", 1.0D) < 0.5D && player.isSprinting()) {
            player.setSprinting(false);
        }
    }

    private static void clearGameplayModifiers(ServerPlayer player) {
        setMultiplier(player, Attributes.MOVEMENT_SPEED, MOVEMENT_MODIFIER, 1.0D);
        setMultiplier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_MODIFIER, 1.0D);
    }

    private static void setMultiplier(ServerPlayer player, Holder<Attribute> attribute,
                                      ResourceLocation id, double multiplier) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        instance.removeModifier(id);
        double safe = Math.max(0.1D, Math.min(2.0D, multiplier));
        if (Math.abs(safe - 1.0D) > 0.0001D) {
            instance.addTransientModifier(new AttributeModifier(
                    id, safe - 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    public static double effect(ServerPlayer player, String key, double fallback) {
        if (!AftermathServerConfig.ENABLE_MOODLES.get()) return fallback;
        return effect(player.getData(AftermathAttachments.PLAYER_STATES),
                PlayerStateDefinitions.snapshot(), key, fallback);
    }

    private static double effect(PlayerStateData data, PlayerStateDefinitions.Snapshot definitions,
                                 String key, double fallback) {
        double result = fallback;
        boolean found = false;
        boolean additive = key.endsWith("_additive");
        for (PlayerStateDefinition definition : definitions.states().values()) {
            double value = data.get(definition.id().toString(), definition.initialValue());
            PlayerStateDefinition.Level level = definition.levelFor(value);
            if (level == null || !level.effects().containsKey(key)) continue;
            double contribution = level.effects().get(key);
            if (additive) result += contribution;
            else result *= contribution;
            found = true;
        }
        return found ? result : fallback;
    }
}
