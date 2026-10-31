package dev.aftermath.character;

import dev.aftermath.AftermathMod;
import dev.aftermath.config.AftermathServerConfig;
import dev.aftermath.weapon.TaczCompatibility;
import dev.aftermath.learning.LearningService;
import dev.aftermath.noise.ZombieEntityClassifier;
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
import net.minecraft.world.entity.Mob;
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
    private static final List<String> WEAPON_CLASSES = List.of("melee", "pistol", "shotgun", "rifle", "sniper", "heavy");
    private static final long VICTIM_REPEAT_WINDOW_TICKS = 20L * 60L;
    private static final Map<UUID, RewardStamp> RECENTLY_REWARDED_VICTIMS = new HashMap<>();

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!AftermathServerConfig.ENABLE_WEAPON_PROGRESSION.get()) return;
        LivingEntity victim = event.getEntity();
        if (!(victim instanceof Mob mob) || !ZombieEntityClassifier.isZombieLikeKillTarget(mob)) return;

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
        if (!(event.getEntity() instanceof Mob mob) || !ZombieEntityClassifier.isZombieLikeKillTarget(mob)) return;
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer attacker)) return;
        String weaponClass = resolveWeaponClass(attacker, source);
        if (weaponClass == null) return;

        int level = levelFor(attacker.getData(AftermathAttachments.WEAPON_PROGRESS)
                .experienceFor(weaponClass));
        // Every level now matters: +1% damage per level, up to +10% at level 10.
        float multiplier = 1.0F + Math.max(0, Math.min(10, level)) * 0.01F;
        if (multiplier > 1.0F) event.setNewDamage(event.getNewDamage() * multiplier);
    }

    private static String resolveWeaponClass(ServerPlayer attacker, DamageSource source) {
        if (source.getDirectEntity() == attacker) {
            ItemStack heldWeapon = attacker.getMainHandItem();
            return heldWeapon.is(MELEE_WEAPONS) ? "melee" : null;
        }
        return TaczCompatibility.weaponClassForBullet(source.getDirectEntity(), attacker);
    }

    /** Level 10 requires 6500 XP; at the default 10 XP per kill this is about 650 zombie kills. */
    public static int levelFor(int experience) {
        int xp = Math.max(0, experience);
        int level = 0;
        for (int candidate = 1; candidate <= 10; candidate++) {
            if (xp >= WeaponProgress.thresholdForLevel(candidate)) level = candidate;
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

    /** Class-specific difficulty is strongest at level 0 and fades smoothly to neutral at level 10. */
    public static double handlingClassMultiplier(String weaponClass) {
        return switch (weaponClass == null ? "" : weaponClass) {
            case "shotgun" -> AftermathServerConfig.UNTRAINED_SHOTGUN_HANDLING_MULTIPLIER.get();
            case "heavy" -> AftermathServerConfig.UNTRAINED_HEAVY_HANDLING_MULTIPLIER.get();
            case "sniper" -> AftermathServerConfig.UNTRAINED_SNIPER_HANDLING_MULTIPLIER.get();
            default -> 1.0D;
        };
    }

    public static double spreadPenalty(ServerPlayer player, String weaponClass) {
        if (!AftermathServerConfig.ENABLE_WEAPON_PROGRESSION.get()
                || weaponClass == null || !WEAPON_CLASSES.contains(weaponClass)) return 0.0D;
        double penalty = AftermathServerConfig.UNTRAINED_FIREARM_SPREAD.get()
                * handlingClassMultiplier(weaponClass)
                * handlingPenaltyForLevel(levelFor(player, weaponClass));
        if (TraitRuntimeService.hasHook(player, "eagle_eye")) {
            penalty *= Math.max(0.0D, Math.min(1.0D,
                    TraitRuntimeService.hookValue(player, "eagle_eye", 1.0D)));
        }
        return Math.max(0.0D, penalty);
    }

    public static double reloadDurationMultiplier(ServerPlayer player, String weaponClass) {
        double penalty = 0.0D;
        if (AftermathServerConfig.ENABLE_WEAPON_PROGRESSION.get()
                && weaponClass != null && WEAPON_CLASSES.contains(weaponClass)) {
            penalty = handlingPenaltyForLevel(levelFor(player, weaponClass))
                    * handlingClassMultiplier(weaponClass);
        }
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
        double penalty = handlingPenaltyForLevel(levelFor(player, weaponClass))
                * handlingClassMultiplier(weaponClass);
        if (TraitRuntimeService.hasHook(player, "eagle_eye")) {
            penalty *= Math.max(0.0D, Math.min(1.0D,
                    TraitRuntimeService.hookValue(player, "eagle_eye", 1.0D)));
        }
        return 1.0D + penalty * (AftermathServerConfig.UNTRAINED_FIREARM_AIM_TIME.get() - 1.0D);
    }

    /** Same formula on the client, using the server-synchronized trait and progression snapshot. */
    public static double aimTimeMultiplier(int level, boolean eagleEye, double untrainedMultiplier) {
        return aimTimeMultiplier(level, eagleEye, untrainedMultiplier, 1.0D);
    }

    public static double aimTimeMultiplier(int level, boolean eagleEye, double untrainedMultiplier,
                                           double classHandlingMultiplier) {
        double penalty = handlingPenaltyForLevel(level) * Math.max(1.0D, classHandlingMultiplier);
        if (eagleEye) penalty *= 0.8D;
        return 1.0D + penalty * (Math.max(1.0D, untrainedMultiplier) - 1.0D);
    }

    public static void syncToClient(ServerPlayer player) {
        WeaponProgress progress = player.getData(AftermathAttachments.WEAPON_PROGRESS);
        String csv = WEAPON_CLASSES.stream()
                .map(weaponClass -> weaponClass + "=" + progress.experienceFor(weaponClass))
                .collect(Collectors.joining(";"));
        PacketDistributor.sendToPlayer(player, new WeaponProgressSyncPayload(csv,
                AftermathServerConfig.ENABLE_WEAPON_PROGRESSION.get(),
                AftermathServerConfig.UNTRAINED_FIREARM_AIM_TIME.get().floatValue(),
                AftermathServerConfig.UNTRAINED_FIREARM_RECOIL_PITCH.get().floatValue(),
                AftermathServerConfig.UNTRAINED_FIREARM_RECOIL_YAW.get().floatValue(),
                "shotgun=" + AftermathServerConfig.UNTRAINED_SHOTGUN_HANDLING_MULTIPLIER.get()
                        + ";heavy=" + AftermathServerConfig.UNTRAINED_HEAVY_HANDLING_MULTIPLIER.get()
                        + ";sniper=" + AftermathServerConfig.UNTRAINED_SNIPER_HANDLING_MULTIPLIER.get()));
    }

    public static Component buildStatusMessage(ServerPlayer player) {
        WeaponProgress progress = player.getData(AftermathAttachments.WEAPON_PROGRESS);
        Component[] statuses = WEAPON_CLASSES.stream().map(weaponClass -> {
            int xp = progress.experienceFor(weaponClass);
            return Component.translatable("message.aftermath.weapon_class_status",
                    Component.translatable("weapon_class.aftermath." + weaponClass),
                    levelFor(xp), xp);
        }).toArray(Component[]::new);
        return Component.translatable("message.aftermath.weapons_status", (Object[]) statuses);
    }

    /**
     * Builds an in-game diagnostic for the held TaCZ gun. This intentionally reports the
     * server-computed numbers used by the progression service, not client-side guesses.
     */
    public static Component buildHeldDiagnosticsMessage(ServerPlayer player, ResourceLocation gunId,
                                                         String weaponClass, boolean explicitlyMapped) {
        WeaponProgress progress = player.getData(AftermathAttachments.WEAPON_PROGRESS);
        int xp = progress.experienceFor(weaponClass);
        int level = levelFor(xp);
        int xpToNextLevel = level >= 10 ? 0 : Math.max(0, WeaponProgress.thresholdForLevel(level + 1) - xp);
        double classMultiplier = handlingClassMultiplier(weaponClass);
        double remainingPenalty = handlingPenaltyForLevel(level) * classMultiplier;
        double aimMultiplier = aimTimeMultiplier(player, weaponClass);
        double reloadMultiplier = reloadDurationMultiplier(player, weaponClass);
        double spread = spreadPenalty(player, weaponClass);
        double damageBonusPercent = level * 1.0D;
        double recoilPitch = AftermathServerConfig.UNTRAINED_FIREARM_RECOIL_PITCH.get() * remainingPenalty;
        double recoilYaw = AftermathServerConfig.UNTRAINED_FIREARM_RECOIL_YAW.get() * remainingPenalty;

        return Component.translatable("message.aftermath.weapon_held.diagnostics",
                gunId.toString(),
                Component.translatable("weapon_class.aftermath." + weaponClass),
                Component.translatable(explicitlyMapped
                        ? "message.aftermath.weapon_held.mapped"
                        : "message.aftermath.weapon_held.inferred"),
                level, xp, xpToNextLevel,
                formatDiagnosticNumber(damageBonusPercent),
                formatDiagnosticNumber(spread),
                formatDiagnosticNumber(aimMultiplier),
                formatDiagnosticNumber(reloadMultiplier),
                formatDiagnosticNumber(recoilPitch),
                formatDiagnosticNumber(recoilYaw));
    }

    private static String formatDiagnosticNumber(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
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
