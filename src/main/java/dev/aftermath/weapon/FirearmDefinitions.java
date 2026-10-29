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
    private static final Set<String> ALLOWED_CLASSES = Set.of("pistol", "shotgun", "rifle", "heavy");
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

    public static String classForGun(ResourceLocation gunId) {
        return gunId == null ? null : current.gunClasses().get(gunId);
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
