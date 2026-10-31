package dev.aftermath.medical;

import dev.aftermath.AftermathMod;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import java.util.Locale;

/**
 * Reports the external medical provider. Aftermath deliberately does not maintain
 * its own body-part, bleeding, or bandage system.
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

    public static String statusId() {
        if (FIRST_AID_LOADED && MODEL_INJURIES_LOADED) return "firstaid+model_injuries";
        if (FIRST_AID_LOADED) return "firstaid";
        if (MODEL_INJURIES_LOADED) return "model_injuries_without_firstaid";
        return "missing";
    }

    public static void logDetectedMedicalMods() {
        if (FIRST_AID_LOADED && MODEL_INJURIES_LOADED) {
            AftermathMod.LOGGER.info("First Aid New and Model Injuries detected. Their injury and treatment systems are authoritative; Aftermath does not register a parallel bleeding or bandage system.");
        } else if (FIRST_AID_LOADED) {
            AftermathMod.LOGGER.info("First Aid New detected without Model Injuries. Body-part health and treatment are provided by First Aid New; advanced structural injuries are unavailable.");
        } else {
            AftermathMod.LOGGER.warn("First Aid New was not detected. Aftermath's legacy bleeding and bandage fallback has been removed; install First Aid New (and Model Injuries for detailed structural injuries).");
        }
    }

    public static Component buildStatusMessage() {
        return Component.translatable(switch (statusId()) {
            case "firstaid+model_injuries" -> "message.aftermath.medical.status.external";
            case "firstaid" -> "message.aftermath.medical.status.firstaid";
            case "model_injuries_without_firstaid" -> "message.aftermath.medical.status.model_injuries_without_firstaid";
            default -> "message.aftermath.medical.status.missing";
        });
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
