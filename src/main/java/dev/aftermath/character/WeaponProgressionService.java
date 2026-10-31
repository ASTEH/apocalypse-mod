package dev.aftermath.character;

import dev.aftermath.AftermathMod;
import dev.aftermath.config.AftermathServerConfig;
import dev.aftermath.weapon.TaczCompatibility;
import dev.aftermath.learning.LearningService;
import dev.aftermath.network.WeaponProgressSyncPayload;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.stream.Collectors;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-owned weapon progression. Direct melee weapons and TaCZ 1.21.1 kinetic bullets are
 * supported without making TaCZ a required dependency; unknown firearms fail closed.
 */
public final class WeaponProgressionService {
    private static final TagKey<Item> MELEE_WEAPONS = TagKey.create(
            Registries.ITEM, ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "weapon_classes/melee"));
    private static final int[] LEVEL_THRESHOLDS = {0, 25, 50, 125, 250, 425, 650, 925, 1250, 1625, 2050};
    private static final List<String> WEAPON_CLASSES = List.of("melee", "pistol", "shotgun", "rifle", "heavy");
    private static final long VICTIM_REPEAT_WINDOW_TICKS = 20L * 60L;
    private static final Map<UUID, RewardStamp> RECENTLY_REWARDED_VICTIMS = new HashMap<>();

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!AftermathServerConfig.ENABLE_WEAPON_PROGRESSION.get()) return;
        LivingEntity victim = event.getEntity();
        if (!(victim instanceof Enemy)) return;

        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer killer)) return;
        String weaponClass = resolveWeaponClass(killer, source);
        if (weaponClass == null) return;

        long now = victim.level().getGameTime();
        UUID victimId = victim.getUUID();
        RewardStamp oldStamp = RECENTLY_REWARDED_VICTIMS.get(victimId);
        if (oldStamp != null
                && oldStamp.dimension().equals(victim.level().dimension().location())
                && now >= oldStamp.gameTime()
                && now - oldStamp.gameTime() < VICTIM_REPEAT_WINDOW_TICKS) return;

        int configuredXp = AftermathServerConfig.WEAPON_XP_PER_KILL.get();
        int perVictimCap = AftermathServerConfig.MAX_XP_PER_VICTIM_PER_MINUTE.get();
        int gainedXp = Math.max(0, Math.min(configuredXp, perVictimCap));
        RECENTLY_REWARDED_VICTIMS.put(victimId,
                new RewardStamp(victim.level().dimension().location(), now));
        cleanOldVictims(now, victim.level().dimension().location());
        if (gainedXp <= 0) return;
        int learningXp = Math.max(1, gainedXp / 2);
        if ("melee".equals(weaponClass)) {
            LearningService.awardMeleeXp(killer, learningXp);
        } else {
            LearningService.awardFirearmsXp(killer, learningXp);
        }

        WeaponProgress before = killer.getData(AftermathAttachments.WEAPON_PROGRESS);
        int oldXp = before.experienceFor(weaponClass);
        int oldLevel = levelFor(oldXp);
        WeaponProgress after = before.addExperience(weaponClass, gainedXp);
        killer.setData(AftermathAttachments.WEAPON_PROGRESS, after);
        syncToClient(killer);

        int newXp = after.experienceFor(weaponClass);
        int newLevel = levelFor(newXp);
        Component className = Component.translatable("weapon_class.aftermath." + weaponClass);
        if (newLevel > oldLevel) {
            killer.displayClientMessage(Component.translatable(
                    "message.aftermath.weapon_level_up", className, newLevel, newXp), true);
            AftermathMod.LOGGER.debug("Player {} reached {} proficiency level {} ({} XP).",
                    killer.getGameProfile().getName(), weaponClass, newLevel, newXp);
        } else {
            killer.displayClientMessage(Component.translatable(
                    "message.aftermath.weapon_xp", className, gainedXp, newXp, newLevel), true);
        }
    }

    /**
     * Melee bonuses stay tied to the held tagged item. TaCZ bullet bonuses use the projectile's owner
     * and gun ID, so swapping items after firing cannot misattribute the weapon class.
     */
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (!AftermathServerConfig.ENABLE_WEAPON_PROGRESSION.get()) return;
        if (!(event.getEntity() instanceof Enemy)) return;
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer attacker)) return;
        String weaponClass = resolveWeaponClass(attacker, source);
        if (weaponClass == null) return;

        int level = levelFor(attacker.getData(AftermathAttachments.WEAPON_PROGRESS)
                .experienceFor(weaponClass));
        float multiplier = level >= 10 ? 1.10F : level >= 5 ? 1.05F : level >= 2 ? 1.02F : 1.0F;
        if (multiplier > 1.0F) event.setNewDamage(event.getNewDamage() * multiplier);
    }

    private static String resolveWeaponClass(ServerPlayer attacker, DamageSource source) {
        if (source.getDirectEntity() == attacker) {
            ItemStack heldWeapon = attacker.getMainHandItem();
            return heldWeapon.is(MELEE_WEAPONS) ? "melee" : null;
        }
        return TaczCompatibility.weaponClassForBullet(source.getDirectEntity(), attacker);
    }

    /** Level 0 is untrained. Existing damage milestones remain at 50 / 425 / 2050 XP for levels 2 / 5 / 10. */
    public static int levelFor(int experience) {
        int xp = Math.max(0, experience);
        int level = 0;
        for (int candidate = 1; candidate <= 10; candidate++) {
            if (xp >= LEVEL_THRESHOLDS[candidate]) level = candidate;
            else break;
        }
        return level;
    }

    public static double handlingPenaltyForLevel(int level) {
        return 1.0D - Math.max(0, Math.min(10, level)) / 10.0D;
    }

    public static int levelFor(ServerPlayer player, String weaponClass) {
        if (!AftermathServerConfig.ENABLE_WEAPON_PROGRESSION.get()
                || weaponClass == null || !WEAPON_CLASSES.contains(weaponClass)) return 10;
        return levelFor(player.getData(AftermathAttachments.WEAPON_PROGRESS).experienceFor(weaponClass));
    }

    public static double spreadPenalty(ServerPlayer player, String weaponClass) {
        if (!AftermathServerConfig.ENABLE_WEAPON_PROGRESSION.get()
                || weaponClass == null || !WEAPON_CLASSES.contains(weaponClass)) return 0.0D;
        double penalty = AftermathServerConfig.UNTRAINED_FIREARM_SPREAD.get()
                * handlingPenaltyForLevel(levelFor(player, weaponClass));
        if (TraitRuntimeService.hasHook(player, "eagle_eye")) {
            penalty *= Math.max(0.0D, Math.min(1.0D,
                    TraitRuntimeService.hookValue(player, "eagle_eye", 1.0D)));
        }
        return Math.max(0.0D, penalty);
    }

    public static double reloadDurationMultiplier(ServerPlayer player, String weaponClass) {
        if (!AftermathServerConfig.ENABLE_WEAPON_PROGRESSION.get()
                || weaponClass == null || !WEAPON_CLASSES.contains(weaponClass)) return 1.0D;
        double penalty = handlingPenaltyForLevel(levelFor(player, weaponClass));
        double multiplier = 1.0D + penalty
                * (AftermathServerConfig.UNTRAINED_FIREARM_RELOAD_DURATION.get() - 1.0D);
        if (TraitRuntimeService.hasHook(player, "quick_reload")) {
            multiplier *= Math.max(0.5D, Math.min(1.0D,
                    TraitRuntimeService.hookValue(player, "quick_reload", 1.0D)));
        }
        return Math.max(0.5D, Math.min(3.0D, multiplier));
    }

    public static double aimTimeMultiplier(ServerPlayer player, String weaponClass) {
        if (!AftermathServerConfig.ENABLE_WEAPON_PROGRESSION.get()
                || weaponClass == null || !WEAPON_CLASSES.contains(weaponClass)) return 1.0D;
        double penalty = handlingPenaltyForLevel(levelFor(player, weaponClass));
        if (TraitRuntimeService.hasHook(player, "eagle_eye")) {
            penalty *= Math.max(0.0D, Math.min(1.0D,
                    TraitRuntimeService.hookValue(player, "eagle_eye", 1.0D)));
        }
        return 1.0D + penalty * (AftermathServerConfig.UNTRAINED_FIREARM_AIM_TIME.get() - 1.0D);
    }

    /** Same formula on the client, using the server-synchronized trait and progression snapshot. */
    public static double aimTimeMultiplier(int level, boolean eagleEye, double untrainedMultiplier) {
        double penalty = handlingPenaltyForLevel(level);
        if (eagleEye) penalty *= 0.8D;
        return 1.0D + penalty * (Math.max(1.0D, untrainedMultiplier) - 1.0D);
    }

    public static void syncToClient(ServerPlayer player) {
        WeaponProgress progress = player.getData(AftermathAttachments.WEAPON_PROGRESS);
        String csv = WEAPON_CLASSES.stream()
                .map(weaponClass -> weaponClass + "=" + progress.experienceFor(weaponClass))
                .collect(Collectors.joining(";"));
        PacketDistributor.sendToPlayer(player, new WeaponProgressSyncPayload(csv,
                AftermathServerConfig.UNTRAINED_FIREARM_AIM_TIME.get().floatValue(),
                AftermathServerConfig.UNTRAINED_FIREARM_RECOIL_PITCH.get().floatValue(),
                AftermathServerConfig.UNTRAINED_FIREARM_RECOIL_YAW.get().floatValue()));
    }

    public static Component buildStatusMessage(ServerPlayer player) {
        WeaponProgress progress = player.getData(AftermathAttachments.WEAPON_PROGRESS);
        Component[] statuses = WEAPON_CLASSES.stream().map(weaponClass -> {
            int xp = progress.experienceFor(weaponClass);
            return Component.translatable("message.aftermath.weapon_class_status",
                    Component.translatable("weapon_class.aftermath." + weaponClass),
                    levelFor(xp), xp);
        }).toArray(Component[]::new);
        return Component.translatable("message.aftermath.weapons_status",
                statuses[0], statuses[1], statuses[2], statuses[3], statuses[4]);
    }

    private static void cleanOldVictims(long now, ResourceLocation dimension) {
        if (RECENTLY_REWARDED_VICTIMS.size() < 2048) return;
        RECENTLY_REWARDED_VICTIMS.entrySet().removeIf(entry -> {
            RewardStamp stamp = entry.getValue();
            return !stamp.dimension().equals(dimension)
                    || now < stamp.gameTime()
                    || now - stamp.gameTime() >= VICTIM_REPEAT_WINDOW_TICKS;
        });
    }

    private record RewardStamp(ResourceLocation dimension, long gameTime) {}

    private WeaponProgressionService() {}
}
