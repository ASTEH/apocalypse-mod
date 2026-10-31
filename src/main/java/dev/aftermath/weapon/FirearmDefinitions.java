package dev.aftermath.weapon;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aftermath.AftermathMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Maps external gun IDs (not Minecraft item IDs) to an Aftermath weapon class.
 * Definitions are data-driven so custom gun packs can be supported without a hard dependency.
 */
public final class FirearmDefinitions extends SimplePreparableReloadListener<FirearmDefinitions.Snapshot> {
    public static final FirearmDefinitions INSTANCE = new FirearmDefinitions();
    private static final Set<String> ALLOWED_CLASSES = Set.of(
            "pistol", "shotgun", "rifle", "sniper", "heavy");
    private static volatile Snapshot current = Snapshot.empty();

    private FirearmDefinitions() {}

    public static void register(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    @Override
    protected Snapshot prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, String> gunClasses = new LinkedHashMap<>();
        Map<ResourceLocation, Resource> resources = manager.listResources(
                "firearm_classes", id -> id.getPath().endsWith(".json"));
        resources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (!json.has("guns") || !json.get("guns").isJsonObject()) {
                    throw new IllegalArgumentException("missing guns object");
                }
                JsonObject guns = json.getAsJsonObject("guns");
                for (Map.Entry<String, JsonElement> gunEntry : guns.entrySet()) {
                    ResourceLocation gunId = ResourceLocation.tryParse(gunEntry.getKey());
                    if (gunId == null || !gunEntry.getValue().isJsonPrimitive()
                            || !gunEntry.getValue().getAsJsonPrimitive().isString()) {
                        throw new IllegalArgumentException("invalid gun mapping for " + gunEntry.getKey());
                    }
                    String weaponClass = gunEntry.getValue().getAsString().toLowerCase(Locale.ROOT);
                    if (!ALLOWED_CLASSES.contains(weaponClass)) {
                        throw new IllegalArgumentException("unsupported weapon class " + weaponClass
                                + " for " + gunEntry.getKey());
                    }
                    gunClasses.put(gunId, weaponClass);
                }
            } catch (Exception exception) {
                AftermathMod.LOGGER.error("Could not load firearm class definition {}: {}",
                        entry.getKey(), exception.getMessage());
            }
        });
        return new Snapshot(gunClasses);
    }

    @Override
    protected void apply(Snapshot prepared, ResourceManager manager, ProfilerFiller profiler) {
        current = prepared;
        AftermathMod.LOGGER.info("Loaded {} external firearm class mappings.", prepared.gunClasses().size());
    }

    public static Snapshot snapshot() {
        return current;
    }

    /**
     * Explicit datapack mappings win. For TaCZ custom gunpacks, infer a class from the ID when no
     * mapping exists; otherwise valid add-on guns would silently receive no progression at all.
     * Ambiguous names default to rifle, and can be overridden with an explicit JSON mapping.
     */
    public static String classForGun(ResourceLocation gunId) {
        if (gunId == null) return null;
        String explicit = current.gunClasses().get(gunId);
        return explicit != null ? explicit : inferClassFromId(gunId);
    }

    public static boolean hasExplicitClassForGun(ResourceLocation gunId) {
        return gunId != null && current.gunClasses().containsKey(gunId);
    }

    private static String inferClassFromId(ResourceLocation gunId) {
        String id = (gunId.getNamespace() + "_" + gunId.getPath())
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_");

        // Check precision/anti-materiel rifles before the broader heavy-weapon and rifle groups.
        if (containsAny(id, "sniper", "sniper_rifle", "bolt_action", "anti_materiel",
                "anti_material", "awp", "m700", "m107", "m95", "kar98", "kar_98",
                "springfield", "mosin", "intervention", "barrett", "remington_700",
                "svd", "dragunov", "l96", "lonetrail", "spr15hb", "mk14", "50_bmg")) {
            return "sniper";
        }
        if (containsAny(id, "minigun", "gatling", "machine_gun", "machinegun",
                "light_machine_gun", "heavy", "m249", "m240", "m60", "m2_browning",
                "m134", "rpk", "rpg", "rocket_launcher", "grenade_launcher", "flamethrower",
                "mg42", "mg34", "pkm", "evolys", "hmg", "lmg")) {
            return "heavy";
        }
        if (containsAny(id, "shotgun", "shot_gun", "aa12", "aa_12", "m1014", "m870",
                "spas", "db_long", "db_short", "double_barrel", "doublebarrel", "sawed_off",
                "sawedoff", "benelli", "mossberg", "remington_870", "ksg", "usas",
                "striker", "super90", "ks23", "ks_23", "ithaca", "coach_gun")) {
            return "shotgun";
        }
        if (containsAny(id, "pistol", "handgun", "glock", "deagle", "desert_eagle",
                "m1911", "1911", "beretta", "usp", "mk23", "p320", "p226", "revolver",
                "magnum", "taurus", "cz75", "cz_75", "m9a", "five_seven", "fn57",
                "rhino", "walther", "p08", "p99", "px4", "snubnose", "timeless50",
                "p2000", "p250", "p365", "g17", "g18", "g19", "vp9", "hi_power",
                "browning_hp")) {
            return "pistol";
        }

        // Rifles and SMGs are the broadest baseline class for an unknown long firearm.
        return "rifle";
    }

    private static boolean containsAny(String value, String... tokens) {
        for (String token : tokens) {
            if (value.contains(token)) return true;
        }
        return false;
    }

    public record Snapshot(Map<ResourceLocation, String> gunClasses) {
        public Snapshot {
            gunClasses = Map.copyOf(gunClasses);
        }

        public static Snapshot empty() {
            return new Snapshot(Map.of());
        }
    }
}
