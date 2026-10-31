package dev.aftermath.medical;

import dev.aftermath.AftermathMod;
import dev.aftermath.character.AftermathAttachments;
import dev.aftermath.character.CharacterProfile;
import dev.aftermath.config.AftermathServerConfig;
import dev.aftermath.learning.LearningService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Server-authoritative bleeding and first-aid rules. */
public final class FirstAidService {
    private FirstAidService() {}

    public static void onLivingDamage(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !AftermathServerConfig.ENABLE_BLEEDING.get()
                || MedicalCompatibilityService.usesExternalMedicalSystem()
                || event.getNewDamage() <= 0.0F
                || !(event.getSource().getEntity() instanceof Enemy)) {
            return;
        }

        int chance = AftermathServerConfig.BLEEDING_CHANCE_PERCENT.get();
        if (chance <= 0 || (chance < 100 && player.getRandom().nextInt(100) >= chance)) return;

        int maximum = AftermathServerConfig.MAX_BLEEDING_SEVERITY.get();
        BleedingStatus before = player.getData(AftermathAttachments.BLEEDING_STATUS);
        if (before.severity() >= maximum) return;

        int severity = Math.min(maximum, before.severity() + 1);
        int interval = AftermathServerConfig.BLEEDING_TICK_INTERVAL_TICKS.get();
        int timer = before.isBleeding() && before.ticksUntilDamage() > 0
                ? before.ticksUntilDamage() : interval;
        player.setData(AftermathAttachments.BLEEDING_STATUS, new BleedingStatus(severity, timer));

        String messageKey = before.isBleeding()
                ? "message.aftermath.bleeding.worsened"
                : "message.aftermath.bleeding.started";
        player.displayClientMessage(Component.translatable(messageKey, severity), true);
        AftermathMod.LOGGER.debug("Player {} bleeding severity increased to {}.",
                player.getGameProfile().getName(), severity);
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        BleedingStatus status = player.getData(AftermathAttachments.BLEEDING_STATUS);
        if (MedicalCompatibilityService.usesExternalMedicalSystem()) {
            if (status.isBleeding()) {
                player.setData(AftermathAttachments.BLEEDING_STATUS, BleedingStatus.empty());
            }
            return;
        }
        if (!AftermathServerConfig.ENABLE_BLEEDING.get()) return;
        if (!status.isBleeding()) return;

        if (status.ticksUntilDamage() > 1) {
            player.setData(AftermathAttachments.BLEEDING_STATUS,
                    new BleedingStatus(status.severity(), status.ticksUntilDamage() - 1));
            return;
        }

        float damagePerSeverity = AftermathServerConfig.BLEEDING_DAMAGE_PER_SEVERITY.get().floatValue();
        float damage = damagePerSeverity * status.severity();
        if (damage > 0.0F) {
            player.hurt(player.damageSources().generic(), damage);
        }

        BleedingStatus latest = player.getData(AftermathAttachments.BLEEDING_STATUS);
        if (latest.isBleeding()) {
            player.setData(AftermathAttachments.BLEEDING_STATUS, new BleedingStatus(
                    latest.severity(), AftermathServerConfig.BLEEDING_TICK_INTERVAL_TICKS.get()));
        }
    }

    /** @return true only when a bandage was actually used to treat an injury. */
    public static boolean applyBandage(ServerPlayer player) {
        if (MedicalCompatibilityService.usesExternalMedicalSystem()) {
            message(player, "message.aftermath.medical.use_external_screen");
            return false;
        }
        if (!AftermathServerConfig.ENABLE_BLEEDING.get()) {
            message(player, "message.aftermath.bleeding.disabled");
            return false;
        }

        BleedingStatus status = player.getData(AftermathAttachments.BLEEDING_STATUS);
        if (!status.isBleeding()) {
            message(player, "message.aftermath.bandage.not_bleeding");
            return false;
        }

        CharacterProfile profile = player.getData(AftermathAttachments.CHARACTER_PROFILE);
        boolean medic = profile.created() && "aftermath:medic".equals(profile.professionId());
        int interval = AftermathServerConfig.BLEEDING_TICK_INTERVAL_TICKS.get();
        BleedingStatus updated;
        if (medic || status.severity() <= 1) {
            updated = BleedingStatus.empty();
        } else {
            updated = new BleedingStatus(status.severity() - 1, interval);
        }
        player.setData(AftermathAttachments.BLEEDING_STATUS, updated);

        if (!updated.isBleeding()) {
            message(player, medic
                    ? "message.aftermath.first_aid.medic_treated"
                    : "message.aftermath.bleeding.stopped");
        } else {
            player.displayClientMessage(Component.translatable(
                    "message.aftermath.bleeding.bandaged", updated.severity()), true);
        }
        AftermathMod.LOGGER.debug("Player {} applied a bandage; bleeding severity {} -> {}.",
                player.getGameProfile().getName(), status.severity(), updated.severity());
        LearningService.awardFirstAidXp(player);
        return true;
    }

    public static boolean isAftermathBleedingActive() {
        return AftermathServerConfig.ENABLE_BLEEDING.get()
                && !MedicalCompatibilityService.usesExternalMedicalSystem();
    }

    public static Component buildStatusMessage(ServerPlayer player) {
        if (MedicalCompatibilityService.usesExternalMedicalSystem()) {
            return Component.translatable("message.aftermath.medical.external_active");
        }
        if (!AftermathServerConfig.ENABLE_BLEEDING.get()) {
            return Component.translatable("message.aftermath.bleeding.disabled");
        }

        BleedingStatus status = player.getData(AftermathAttachments.BLEEDING_STATUS);
        if (!status.isBleeding()) return Component.translatable("message.aftermath.bleeding.none");

        int seconds = (int) Math.ceil(status.ticksUntilDamage() / 20.0D);
        return Component.translatable("message.aftermath.bleeding.status",
                status.severity(), AftermathServerConfig.MAX_BLEEDING_SEVERITY.get(), seconds);
    }

    private static void message(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key), true);
    }
}
