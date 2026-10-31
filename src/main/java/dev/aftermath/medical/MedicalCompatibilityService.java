package dev.aftermath.medical;

import dev.aftermath.AftermathMod;
import dev.aftermath.config.AftermathServerConfig;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import java.util.Locale;

/**
 * Selects one authoritative blood-loss implementation.
 * First Aid New owns body-part health and treatment; Model Injuries adds detailed
 * structural injuries and its own blood-loss system.
 */
public final class MedicalCompatibilityService {
    private static final boolean FIRST_AID_LOADED = ModList.get().isLoaded("firstaid");
    private static final boolean MODEL_INJURIES_LOADED = ModList.get().getMods().stream()
            .anyMatch(mod -> {
                String modId = normalize(mod.getModId());
                String displayName = normalize(mod.getDisplayName());
                return modId.contains("modelinjur") || displayName.contains("modelinjur");
            });

    private MedicalCompatibilityService() {}

    public static boolean isFirstAidLoaded() {
        return FIRST_AID_LOADED;
    }

    public static boolean isModelInjuriesLoaded() {
        return MODEL_INJURIES_LOADED;
    }

    /**
     * The legacy Aftermath bleeding timer is redundant only when both First Aid and
     * Model Injuries are present. First Aid alone remains supported with Aftermath fallback.
     */
    public static boolean usesExternalMedicalSystem() {
        return AftermathServerConfig.PREFER_EXTERNAL_MEDICAL_SYSTEM.get()
                && FIRST_AID_LOADED && MODEL_INJURIES_LOADED;
    }

    public static void logDetectedMedicalMods() {
        if (FIRST_AID_LOADED && MODEL_INJURIES_LOADED) {
            AftermathMod.LOGGER.info("First Aid New and Model Injuries detected. Their body-part injury and bleeding systems are authoritative; Aftermath fallback bleeding will be skipped.");
        } else if (FIRST_AID_LOADED) {
            AftermathMod.LOGGER.info("First Aid New detected without Model Injuries. First Aid owns body-part health; Aftermath fallback bleeding remains available.");
        } else {
            AftermathMod.LOGGER.info("No First Aid medical system detected. Aftermath standalone first-aid fallback remains available.");
        }
    }

    public static Component buildStatusMessage() {
        if (usesExternalMedicalSystem()) {
            return Component.translatable("message.aftermath.medical.status.external");
        }
        if (FIRST_AID_LOADED) {
            return Component.translatable("message.aftermath.medical.status.firstaid");
        }
        return Component.translatable("message.aftermath.medical.status.fallback");
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
