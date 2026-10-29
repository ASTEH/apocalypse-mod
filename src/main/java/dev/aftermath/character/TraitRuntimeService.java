package dev.aftermath.character;

import dev.aftermath.AftermathMod;
import dev.aftermath.noise.NoiseService;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.food.FoodData;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side custom trait hooks. Client-only effects (hearing and short sight) are kept in
 * TraitClientEvents and receive only a server-authoritative list of selected trait IDs.
 */
public final class TraitRuntimeService {
    public static final ResourceLocation CLUMSY_FOOTSTEPS =
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "footsteps");

    private static final Map<UUID, Float> LAST_EXHAUSTION = new HashMap<>();

    private TraitRuntimeService() {}

    public static boolean hasHook(ServerPlayer player, String hook) {
        return effects(player, "custom_hook").stream().anyMatch(effect -> effect.target().equals(hook));
    }

    public static double hookValue(ServerPlayer player, String hook, double fallback) {
        return effects(player, "custom_hook").stream()
                .filter(effect -> effect.target().equals(hook))
                .mapToDouble(CharacterDefinitions.TraitEffect::value)
                .findFirst().orElse(fallback);
    }

    public static double multiplier(ServerPlayer player, String effectType, double fallback) {
        double result = fallback;
        for (CharacterDefinitions.TraitEffect effect : effects(player, effectType)) {
            result *= effect.value();
        }
        return Math.max(0.1D, Math.min(4.0D, result));
    }

    public static boolean canRead(ServerPlayer player) {
        return !hasHook(player, "illiterate");
    }

    public static int adjustedReadingDuration(ServerPlayer player, int baseTicks) {
        double multiplier = multiplier(player, "reading_speed_multiplier", 1.0D);
        return (int) Math.max(20L, Math.min(72_000L, (long) Math.ceil(baseTicks * multiplier)));
    }

    private static java.util.List<CharacterDefinitions.TraitEffect> effects(ServerPlayer player, String effectType) {
        CharacterProfile profile = player.getData(AftermathAttachments.CHARACTER_PROFILE);
        if (!profile.created()) return java.util.List.of();
        java.util.ArrayList<CharacterDefinitions.TraitEffect> found = new java.util.ArrayList<>();
        CharacterDefinitions.Snapshot snapshot = CharacterDefinitions.snapshot();
        for (String rawId : profile.traitIds()) {
            ResourceLocation id = ResourceLocation.tryParse(rawId);
            if (id == null) continue;
            CharacterDefinitions.TraitDefinition trait = snapshot.traits().get(id);
            if (trait == null) continue;
            for (CharacterDefinitions.TraitEffect effect : trait.effects()) {
                if (effect.type().equals(effectType)) found.add(effect);
            }
        }
        return found;
    }

    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getSource().getEntity() instanceof Enemy)
                || event.getNewDamage() <= 0.0F) return;

        double multiplier = effects(player, "damage_multiplier").stream()
                .filter(effect -> effect.target().equals("hostile"))
                .mapToDouble(CharacterDefinitions.TraitEffect::value)
                .reduce(1.0D, (left, right) -> left * right);
        if (multiplier < 1.0D) {
            event.setNewDamage((float) (event.getNewDamage() * Math.max(0.1D, multiplier)));
        }
    }

    public static void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0.0F) return;
        if (hasHook(player, "slow_healer")) {
            event.setAmount((float) (event.getAmount() * hookValue(player, "slow_healer", 0.5D)));
        }
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CharacterProfile profile = player.getData(AftermathAttachments.CHARACTER_PROFILE);
        if (!profile.created()) return;

        // Keep these effects short and refresh them on the server so relogs and respawns are safe.
        for (CharacterDefinitions.TraitEffect effect : effects(player, "mob_effect")) {
            ResourceLocation id = ResourceLocation.tryParse(effect.target());
            if (id == null) continue;
            var holder = BuiltInRegistries.MOB_EFFECT.getHolder(id);
            if (holder.isEmpty()) continue;
            MobEffectInstance active = player.getEffect(holder.get());
            int amplifier = (int) effect.value();
            if (active == null || active.getDuration() < 60 || active.getAmplifier() != amplifier) {
                player.addEffect(new MobEffectInstance(holder.get(), effect.durationTicks(), amplifier,
                        true, false, false));
            }
        }

        if (hasHook(player, "iron_stomach")) {
            player.removeEffect(MobEffects.HUNGER);
        }

        if (hasHook(player, "cowardly") && player.tickCount % 20 == 0) {
            boolean nearbyThreat = !player.level().getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(8.0D),
                    entity -> entity instanceof Enemy && entity.isAlive() && !entity.isRemoved()).isEmpty();
            if (nearbyThreat) {
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, true, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 40, 0, true, false, false));
            }
        }

        applyExhaustionHooks(player);
        emitClumsyFootsteps(player);
    }

    private static void applyExhaustionHooks(ServerPlayer player) {
        UUID uuid = player.getUUID();
        FoodData food = player.getFoodData();
        float current = food.getExhaustionLevel();
        Float previous = LAST_EXHAUSTION.put(uuid, current);
        if (previous != null && current > previous && hasHook(player, "light_eater")) {
            float delta = current - previous;
            double multiplier = Math.max(0.1D, Math.min(1.0D, hookValue(player, "light_eater", 0.65D)));
            food.setExhaustion(Math.max(0.0F, current - delta * (1.0F - (float) multiplier)));
            LAST_EXHAUSTION.put(uuid, food.getExhaustionLevel());
        }

        if (hasHook(player, "asthmatic") && player.isSprinting() && player.onGround()
                && player.getDeltaMovement().horizontalDistanceSqr() > 0.0025D
                && player.tickCount % 10 == 0) {
            food.addExhaustion(0.1F);
            LAST_EXHAUSTION.put(uuid, food.getExhaustionLevel());
        }
    }

    private static void emitClumsyFootsteps(ServerPlayer player) {
        if (!hasHook(player, "clumsy_noise")
                || !(player.level() instanceof ServerLevel level)
                || !player.onGround() || player.isCrouching()
                || player.getDeltaMovement().horizontalDistanceSqr() <= 0.0025D
                || level.getGameTime() % 40L != 0L) return;

        // This low-radius noise uses the same loaded-entity-only pipeline as other noise events.
        NoiseService.emitNoise(level, player.position(), CLUMSY_FOOTSTEPS, player.getUUID());
    }

    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_EXHAUSTION.remove(event.getEntity().getUUID());
    }
}
