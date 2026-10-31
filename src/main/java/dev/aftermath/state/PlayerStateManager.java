package dev.aftermath.state;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.Gson;
import dev.aftermath.AftermathMod;
import dev.aftermath.character.AftermathAttachments;
import dev.aftermath.character.CharacterProfile;
import dev.aftermath.config.AftermathServerConfig;
import dev.aftermath.network.MoodlesSyncPayload;
import dev.aftermath.compat.ColdSweatCompatibility;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Server-side simulation, persistence and synchronization for player Moodles. */
public final class PlayerStateManager {
    private static final Gson GSON = new Gson();
    private static final TagKey<EntityType<?>> MOODLE_THREATS = TagKey.create(
            Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "moodle_threats"));
    private static final ResourceLocation MOVEMENT_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "moodle_movement_speed");
    private static final ResourceLocation ATTACK_SPEED_MODIFIER =
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "moodle_attack_speed");

    /** Boredom value from which the "high_boredom" source starts feeding unhappiness (matches the "very_bored" level). */
    private static final double HIGH_BOREDOM_THRESHOLD = 60.0D;
    /** Player counts as moving above this horizontal speed (blocks per tick, smoothed). ~0.6 blocks/s. */
    private static final double MOVING_SPEED = 0.03D;
    /** Upper bound for time credited after a night skip (ticks). */
    private static final long MAX_SLEEP_SKIP_TICKS = 14_000L;
    /**
     * Global cap for the summed "firearm_spread_additive" of all moodles.
     * Tune this to the spread unit of your weapon code.
     */
    private static final double MAX_FIREARM_SPREAD_ADDITIVE = 0.15D;

    /** Per-player runtime data that must NOT be persisted (weak keys, so no cleanup is required). */
    private static final Map<ServerPlayer, Motion> MOTION = Collections.synchronizedMap(new WeakHashMap<>());

    private PlayerStateManager() {}

    /**
     * ServerPlayer#getDeltaMovement() is not updated from client movement packets, so it is ~0 on the server
     * and cannot be used to detect walking/standing. We measure real position change instead.
     */
    private static final class Motion {
        private boolean hasLast;
        private double lastX;
        private double lastZ;
        private double speed; // smoothed horizontal speed, blocks per tick

        private boolean wasSleeping;
        private long sleepStartDayTime;
        private long sleepTicks;

        void updatePosition(ServerPlayer player) {
            double x = player.getX();
            double z = player.getZ();
            if (hasLast) {
                double dx = x - lastX;
                double dz = z - lastZ;
                double instant = Math.sqrt(dx * dx + dz * dz);
                if (instant > 4.0D) instant = 0.0D; // teleport / dimension change
                speed += (instant - speed) * 0.2D;
            }
            lastX = x;
            lastZ = z;
            hasLast = true;
        }

        boolean moving() {
            return speed >= MOVING_SPEED;
        }

        /**
         * Sleeping skips the night in a few real seconds, so per-second sleep recovery would be worthless.
         * When the player wakes up, returns the extra (skipped) seconds that were not simulated tick by tick.
         */
        double trackSleep(ServerPlayer player) {
            long dayTime = player.level().getDayTime();
            boolean sleeping = player.isSleeping();
            double extraSeconds = 0.0D;
            if (sleeping) {
                if (!wasSleeping) {
                    sleepStartDayTime = dayTime;
                    sleepTicks = 0L;
                }
                sleepTicks++;
            } else if (wasSleeping) {
                long elapsed = Math.max(0L, Math.min(MAX_SLEEP_SKIP_TICKS, dayTime - sleepStartDayTime));
                long extra = elapsed - sleepTicks;
                if (extra >= 20L) extraSeconds = extra / 20.0D;
            }
            wasSleeping = sleeping;
            return extraSeconds;
        }
    }

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
        // Remove the retired Aftermath bleeding Moodle value from existing world saves.
        if (values.remove("aftermath:bleeding") != null) changed = true;

        for (PlayerStateDefinition definition : definitions.states().values()) {
            String id = definition.id().toString();
            if (!values.containsKey(id)) {
                values.put(id, definition.initialValue());
                changed = true;
            }
        }

        Motion motion = MOTION.computeIfAbsent(player, ignored -> new Motion());
        motion.updatePosition(player);
        double sleptSeconds = motion.trackSleep(player);
        if (sleptSeconds > 0.0D) {
            changed |= applySleepSkip(player, definitions, values, sleptSeconds);
        }

        long gameTime = player.level().getGameTime();
        for (PlayerStateDefinition definition : definitions.states().values()) {
            if (gameTime % definition.updateIntervalTicks() != 0L) continue;
            String id = definition.id().toString();
            double current = values.getOrDefault(id, definition.initialValue());

 is an absolute value, not a delta.
            if (id.equals("aftermath:temperature")) {
                double bodyTemperature = ColdSweatCompatibility.getBodyTemperature(player);
                double next = definition.clamp(Double.isFinite(bodyTemperature) ? bodyTemperature : 0.0D);
                if (Math.abs(next - current) > 0.000001D) {
                    values.put(id, next);
                    changed = true;
                }
                continue;
            }

            double delta = 0.0D;

            for (PlayerStateDefinition.Source source : definition.sources()) {
                delta += evaluatePeriodicSource(player, source, definition, values, motion);
            }
            for (PlayerStateDefinition.Source source : definition.recovery()) {
                delta += evaluatePeriodicSource(player, source, definition, values, motion);
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

        if (gameTime % 20L == 0L || sleptSeconds > 0.0D) applyGameplayEffects(player, definitions, current);
    }

    private static double evaluatePeriodicSource(ServerPlayer player, PlayerStateDefinition.Source source,
                                                 PlayerStateDefinition definition, Map<String, Double> values,
                                                 Motion motion) {
        boolean moving = motion.moving();
        double intensity = switch (source.type()) {
            case "awake_time" -> player.isSleeping() ? 0.0D : 1.0D;
            case "sleeping" -> player.isSleeping() ? 1.0D : 0.0D;
            case "sprinting" -> player.isSprinting() && moving ? 1.0D : 0.0D;
            case "resting" -> !player.isSleeping() && (player.onGround() || player.isPassenger())
                    && !moving ? 1.0D : 0.0D;
            case "walking" -> !player.isSprinting() && !player.isPassenger() && moving ? 1.0D : 0.0D;
            case "near_tagged_hostile" -> countThreats(player, source.radius(), source.maxEntities());
            case "no_nearby_hostiles" -> countThreats(player, source.radius(), 1) == 0 ? 1.0D : 0.0D;
            case "indoors_idle" -> !isOutdoors(player) && !player.isSleeping() && !moving ? 1.0D : 0.0D;
            case "outdoors_moving" -> isOutdoors(player) && moving ? 1.0D : 0.0D;
            case "high_boredom" -> values.getOrDefault("aftermath:boredom", 0.0D) >= HIGH_BOREDOM_THRESHOLD
                    ? 1.0D : 0.0D;
            default -> 0.0D;
        };

        return sourceDelta(player, source, definition, values, intensity, definition.updateIntervalTicks() / 20.0D);
    }

    private static double sourceDelta(ServerPlayer player, PlayerStateDefinition.Source source,
                                      PlayerStateDefinition definition, Map<String, Double> values,
                                      double intensity, double seconds) {
        double delta = source.deltaPerSecond() * intensity * seconds;
        if (delta > 0.0D) {
            delta *= interactionMultiplier(definition, values);
        }
        return delta * traitMultiplier(player, source);
    }

    /** Applies the "sleeping" sources for the night that was skipped while the player was in bed. */
    private static boolean applySleepSkip(ServerPlayer player, PlayerStateDefinitions.Snapshot definitions,
                                          Map<String, Double> values, double seconds) {
        boolean changed = false;
        for (PlayerStateDefinition definition : definitions.states().values()) {
            String id = definition.id().toString();
            if (id.equals("aftermath:temperature")) continue;
            double delta = 0.0D;
            for (PlayerStateDefinition.Source source : definition.sources()) {
                if (source.type().equals("sleeping")) {
                    delta += sourceDelta(player, source, definition, values, 1.0D, seconds);
                }
            }
            for (PlayerStateDefinition.Source source : definition.recovery()) {
                if (source.type().equals("sleeping")) {
                    delta += sourceDelta(player, source, definition, values, 1.0D, seconds);
                }
            }
            if (delta == 0.0D) continue;
            double current = values.getOrDefault(id, definition.initialValue());
            double next = definition.clamp(current + delta);
            if (Math.abs(next - current) > 0.000001D) {
                values.put(id, next);
                changed = true;
            }
        }
        return changed;
    }

    /** Open sky above the player (leaves do not count as a roof). Dimensions without skylight are never outdoors. */
    private static boolean isOutdoors(ServerPlayer player) {
        Level level = player.level();
        if (!level.dimensionType().hasSkyLight()) return false;
        BlockPos pos = player.blockPosition();
        return pos.getY() >= level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ());
    }

    private static int countThreats(ServerPlayer player, double radius, int limit) {
        if (radius <= 0.0D || limit <= 0) return 0;
        AABB area = player.getBoundingBox().inflate(radius);
        return Math.min(limit, player.level().getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity.isAlive()
                        && (entity instanceof Enemy || entity.getType().is(MOODLE_THREATS))
                        && (entity.distanceToSqr(player) <= 25.0D || player.hasLineOfSight(entity))).size());
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
        // Remove the retired Aftermath bleeding Moodle value from existing world saves.
        if (values.remove("aftermath:bleeding") != null) changed = true;

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
        // Remove the retired Aftermath bleeding Moodle value from existing world saves.
        if (values.remove("aftermath:bleeding") != null) changed = true;
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
        return found ? clampEffect(key, result) : fallback;
    }

    /** Several moodles stack multiplicatively; these limits keep the combined result playable. */
    private static double clampEffect(String key, double value) {
        return switch (key) {
            case "movement_speed_multiplier" -> Math.max(0.60D, value);
            case "attack_speed_multiplier" -> Math.max(0.50D, value);
            case "melee_damage_multiplier" -> Math.max(0.35D, value);
            case "learning_xp_multiplier" -> Math.max(0.40D, value);
            case "critical_chance_multiplier" -> Math.max(0.30D, value);
            case "firearm_spread_additive" -> Math.max(0.0D, Math.min(MAX_FIREARM_SPREAD_ADDITIVE, value));
            default -> value;
        };
    }
}