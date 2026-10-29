package dev.aftermath.character;

import dev.aftermath.AftermathMod;
import dev.aftermath.config.AftermathServerConfig;
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
import java.util.Map;
import java.util.UUID;

/**
 * First weapon-proficiency implementation.
 *
 * Vanilla/direct melee kills are supported now. Projectile/firearm kill attribution remains
 * disabled until an optional weapon-mod adapter can prove which item fired each projectile.
 */
public final class WeaponProgressionService {
    private static final TagKey<Item> MELEE_WEAPONS = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "weapon_classes/melee"));

    private static final int[] LEVEL_THRESHOLDS = {
            0, 0, 50, 125, 250, 425, 650, 925, 1250, 1625, 2050
    };
    private static final long VICTIM_REPEAT_WINDOW_TICKS = 20L * 60L;

    /*
     * LivingDeathEvent should happen once per death, but this guard also protects against
     * duplicate event delivery by integrations. Entries are temporary and never force-load chunks.
     */
    private static final Map<UUID, RewardStamp> RECENTLY_REWARDED_VICTIMS = new HashMap<>();

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!AftermathServerConfig.ENABLE_WEAPON_PROGRESSION.get()) return;

        LivingEntity victim = event.getEntity();
        if (!(victim instanceof Enemy)) return;

        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer killer)) return;

        // Only a direct player hit is supported here; arrows and modded projectiles are deliberately ignored.
        if (source.getDirectEntity() != killer) return;

        ItemStack heldWeapon = killer.getMainHandItem();
        if (!heldWeapon.is(MELEE_WEAPONS)) return;

        long now = victim.level().getGameTime();
        UUID victimId = victim.getUUID();
        RewardStamp oldStamp = RECENTLY_REWARDED_VICTIMS.get(victimId);
        if (oldStamp != null
                && oldStamp.dimension().equals(victim.level().dimension().location())
                && now >= oldStamp.gameTime()
                && now - oldStamp.gameTime() < VICTIM_REPEAT_WINDOW_TICKS) {
            return;
        }

        int configuredXp = AftermathServerConfig.WEAPON_XP_PER_KILL.get();
        int perVictimCap = AftermathServerConfig.MAX_XP_PER_VICTIM_PER_MINUTE.get();
        int gainedXp = Math.max(0, Math.min(configuredXp, perVictimCap));
        RECENTLY_REWARDED_VICTIMS.put(victimId,
                new RewardStamp(victim.level().dimension().location(), now));
        cleanOldVictims(now, victim.level().dimension().location());

        if (gainedXp <= 0) return;

        WeaponProgress before = killer.getData(AftermathAttachments.WEAPON_PROGRESS);
        int oldXp = before.experienceFor("melee");
        int oldLevel = levelFor(oldXp);
        WeaponProgress after = before.addExperience("melee", gainedXp);
        killer.setData(AftermathAttachments.WEAPON_PROGRESS, after);

        int newXp = after.experienceFor("melee");
        int newLevel = levelFor(newXp);
        if (newLevel > oldLevel) {
            killer.displayClientMessage(Component.translatable(
                    "message.aftermath.weapon_level_up",
                    Component.translatable("weapon_class.aftermath.melee"),
                    newLevel,
                    newXp), true);
            AftermathMod.LOGGER.debug("Player {} reached melee proficiency level {} ({} XP).",
                    killer.getGameProfile().getName(), newLevel, newXp);
        } else {
            killer.displayClientMessage(Component.translatable(
                    "message.aftermath.weapon_xp",
                    Component.translatable("weapon_class.aftermath.melee"),
                    gainedXp,
                    newXp,
                    newLevel), true);
        }
    }

    /**
     * Applies modest melee proficiency bonuses to direct melee damage against hostile entities.
     * Projectile damage is excluded by checking the direct entity.
     */
    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (!AftermathServerConfig.ENABLE_WEAPON_PROGRESSION.get()) return;
        if (!(event.getEntity() instanceof Enemy)) return;

        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer attacker) || source.getDirectEntity() != attacker) return;
        if (!attacker.getMainHandItem().is(MELEE_WEAPONS)) return;

        int level = levelFor(attacker.getData(AftermathAttachments.WEAPON_PROGRESS).experienceFor("melee"));
        float multiplier = level >= 10 ? 1.10F : level >= 5 ? 1.05F : level >= 2 ? 1.02F : 1.0F;
        if (multiplier > 1.0F) {
            event.setNewDamage(event.getNewDamage() * multiplier);
        }
    }

    public static int levelFor(int experience) {
        int xp = Math.max(0, experience);
        int level = 1;
        for (int candidate = 2; candidate <= 10; candidate++) {
            if (xp >= LEVEL_THRESHOLDS[candidate]) level = candidate;
            else break;
        }
        return level;
    }

    public static Component buildStatusMessage(ServerPlayer player) {
        WeaponProgress progress = player.getData(AftermathAttachments.WEAPON_PROGRESS);
        int xp = progress.experienceFor("melee");
        int level = levelFor(xp);
        return Component.translatable("message.aftermath.weapon_status",
                Component.translatable("weapon_class.aftermath.melee"),
                level,
                xp,
                LEVEL_THRESHOLDS[Math.min(level + 1, 10)]);
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
