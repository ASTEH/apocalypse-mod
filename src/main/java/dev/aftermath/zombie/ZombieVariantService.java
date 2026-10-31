package dev.aftermath.zombie;

import dev.aftermath.AftermathMod;
import dev.aftermath.config.AftermathServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

import java.util.ArrayList;
import java.util.List;

/** Applies one weighted archetype to each eligible newly-spawned zombie. */
public final class ZombieVariantService {
    private static final String VARIANT_NBT = "AftermathZombieVariant";
    private static final String HEARING_RANGE_NBT = "AftermathZombieHearingRange";

    private ZombieVariantService() {}

    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        if (!AftermathServerConfig.ENABLE_ZOMBIE_VARIANTS.get()) return;
        Mob mob = event.getEntity();
        if (mob.level().isClientSide()) return;

        List<ZombieVariantDefinitions.Variant> eligible = eligibleVariants(mob, event);
        if (eligible.isEmpty()) return;

        ZombieVariantDefinitions.Variant selected = chooseWeighted(eligible, mob);
        if (selected != null) applyVariant(mob, selected);
    }

    private static List<ZombieVariantDefinitions.Variant> eligibleVariants(Mob mob, FinalizeSpawnEvent event) {
        List<ZombieVariantDefinitions.Variant> eligible = new ArrayList<>();
        ResourceLocation dimension = mob.level().dimension().location();
        BlockPos pos = mob.blockPosition();
        ResourceLocation biome = mob.level().getBiome(pos).unwrapKey().map(key -> key.location()).orElse(null);
        int light = mob.level().getMaxLocalRawBrightness(pos);
        boolean night = ZombieSpawnTime.isNight(mob.level());
        String spawnType = event.getSpawnType().name().toLowerCase(Locale.ROOT);

        for (ZombieVariantDefinitions.Variant variant : ZombieVariantDefinitions.snapshot().variants()) {
            if (!mob.getType().is(TagKey.create(Registries.ENTITY_TYPE, variant.targetTag()))) continue;
            ZombieVariantDefinitions.SpawnRules rules = variant.spawn();
            if (!rules.spawnTypes().contains(spawnType)) continue;
            if (!rules.dimensions().isEmpty() && !rules.dimensions().contains(dimension)) continue;
            if (!rules.biomes().isEmpty() && (biome == null || !rules.biomes().contains(biome))) continue;
            if (light < rules.minLight() || light > rules.maxLight()) continue;
            if (rules.timeOfDay().equals("night") && !night) continue;
            if (rules.timeOfDay().equals("day") && night) continue;
            eligible.add(variant);
        }
        return eligible;
    }

    private static ZombieVariantDefinitions.Variant chooseWeighted(
            List<ZombieVariantDefinitions.Variant> variants, Mob mob) {
        long totalWeight = 0L;
        for (ZombieVariantDefinitions.Variant variant : variants) totalWeight += variant.weight();
        if (totalWeight <= 0L) return null;

        double roll = mob.getRandom().nextDouble() * totalWeight;
        for (ZombieVariantDefinitions.Variant variant : variants) {
            roll -= variant.weight();
            if (roll < 0.0D) return variant;
        }
        return variants.get(variants.size() - 1);
    }

    private static void applyVariant(Mob mob, ZombieVariantDefinitions.Variant variant) {
        var random = mob.getRandom();
        double maxHealth = roll(variant.stats().maxHealth(), random);
        double movementSpeed = roll(variant.stats().movementSpeed(), random);
        double attackDamage = roll(variant.stats().attackDamage(), random);
        double visionRange = roll(variant.senses().visionRange(), random);
        double knockbackResistance = roll(variant.stats().knockbackResistance(), random);
        double hearingRange = roll(variant.senses().hearingRange(), random);

        setBaseValue(mob.getAttribute(Attributes.MAX_HEALTH), maxHealth);
        setBaseValue(mob.getAttribute(Attributes.MOVEMENT_SPEED), movementSpeed);
        setBaseValue(mob.getAttribute(Attributes.ATTACK_DAMAGE), attackDamage);
        setBaseValue(mob.getAttribute(Attributes.FOLLOW_RANGE), visionRange);
        setBaseValue(mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE), knockbackResistance);
        mob.setHealth((float) mob.getMaxHealth());

        mob.getPersistentData().putString(VARIANT_NBT, variant.id().toString());
        mob.getPersistentData().putDouble(HEARING_RANGE_NBT, hearingRange);
        AftermathMod.LOGGER.debug("Applied zombie archetype {} to {} (health={}, speed={}, damage={}, vision={}, hearing={}).",
                variant.id(), mob.getType(), maxHealth, movementSpeed, attackDamage, visionRange, hearingRange);
    }

    private static void setBaseValue(AttributeInstance attribute, double value) {
        if (attribute != null) attribute.setBaseValue(value);
    }

    private static double roll(ZombieVariantDefinitions.ValueRange range,
                               net.minecraft.util.RandomSource random) {
        if (range.min() == range.max()) return range.min();
        return range.min() + random.nextDouble() * (range.max() - range.min());
    }

    /** Variant-specific hearing range; other mobs keep the caller's global radius. */
    public static double hearingRange(Mob mob, double fallback) {
        var data = mob.getPersistentData();
        if (!data.contains(HEARING_RANGE_NBT)) return fallback;
        double hearing = data.getDouble(HEARING_RANGE_NBT);
        if (!Double.isFinite(hearing)) return fallback;
        return Math.max(0.0D, Math.min(128.0D, hearing));
    }
}
