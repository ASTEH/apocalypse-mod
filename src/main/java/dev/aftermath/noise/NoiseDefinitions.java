package dev.aftermath.noise;
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
import java.util.Map;

/** Loads validated noise-event and horde-archetype definitions from datapacks. */
public final class NoiseDefinitions extends SimplePreparableReloadListener<NoiseDefinitions.Snapshot> {
    public static final NoiseDefinitions INSTANCE = new NoiseDefinitions();
    private static volatile Snapshot current = Snapshot.empty();
    private NoiseDefinitions() {}
    public static void register(AddReloadListenerEvent event) { event.addListener(INSTANCE); }
    @Override protected Snapshot prepare(ResourceManager manager, ProfilerFiller profiler) {
        return new Snapshot(loadNoiseEvents(manager), loadHordeArchetypes(manager));
    }
    @Override protected void apply(Snapshot prepared, ResourceManager manager, ProfilerFiller profiler) {
        current = prepared;
        AftermathMod.LOGGER.info("Loaded {} noise events and {} horde archetypes.",
                prepared.noiseEvents().size(), prepared.hordeArchetypes().size());
    }
    public static Snapshot snapshot() { return current; }
    private static Map<ResourceLocation, NoiseEventDefinition> loadNoiseEvents(ResourceManager manager) {
        Map<ResourceLocation, NoiseEventDefinition> result = new LinkedHashMap<>();
        manager.listResources("metagame_events", id -> id.getPath().endsWith(".json"))
                .entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ResourceLocation id = definitionId(entry.getKey(), "metagame_events");
            if (id == null) return;
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                if (!requiredString(json, "type").equals("noise")) throw new IllegalArgumentException("unsupported noise event type");
                if (!boolOrDefault(json, "affects_loaded_entities_only", true))
                    throw new IllegalArgumentException("noise events may only affect already-loaded entities");
                result.put(id, new NoiseEventDefinition(id,
                        boundedInt(json, "noise_radius", 0, 512),
                        boundedInt(json, "lure_duration_ticks", 1, 72_000),
                        boundedInt(json, "cooldown_ticks", 0, 72_000),
                        parseId(requiredString(json, "target_tag"), "target_tag"),
                        boundedIntOrDefault(json, "max_affected_entities", 32, 1, 128)));
            } catch (Exception ex) {
                AftermathMod.LOGGER.error("Could not load noise event {}: {}", entry.getKey(), ex.getMessage());
            }
        });
        return Map.copyOf(result);
    }
    private static Map<ResourceLocation, HordeArchetypeDefinition> loadHordeArchetypes(ResourceManager manager) {
        Map<ResourceLocation, HordeArchetypeDefinition> result = new LinkedHashMap<>();
        manager.listResources("zombie_archetypes", id -> id.getPath().endsWith(".json"))
                .entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ResourceLocation id = definitionId(entry.getKey(), "zombie_archetypes");
            if (id == null) return;
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                result.put(id, new HordeArchetypeDefinition(id,
                        parseId(requiredString(json, "entity_tag"), "entity_tag"),
                        requiredBoolean(json, "leader"),
                        boundedInt(json, "follower_radius", 1, 128),
                        boundedInt(json, "max_followers", 0, 127),
                        boundedDoubleOrDefault(json, "speed_multiplier", 1.0D, 0.25D, 2.0D)));
            } catch (Exception ex) {
                AftermathMod.LOGGER.error("Could not load horde archetype {}: {}", entry.getKey(), ex.getMessage());
            }
        });
        return Map.copyOf(result);
    }
    private static ResourceLocation definitionId(ResourceLocation fileId, String folder) {
        String prefix = folder + "/";
        String path = fileId.getPath();
        if (!path.startsWith(prefix) || !path.endsWith(".json")) return null;
        String relative = path.substring(prefix.length(), path.length() - ".json".length());
        return relative.isBlank() ? null : ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), relative);
    }
    private static ResourceLocation parseId(String raw, String field) {
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) throw new IllegalArgumentException("invalid resource id in " + field);
        return id;
    }
    private static String requiredString(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) throw new IllegalArgumentException("missing string field " + key);
        return json.get(key).getAsString();
    }
    private static boolean requiredBoolean(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) throw new IllegalArgumentException("missing boolean field " + key);
        return json.get(key).getAsBoolean();
    }
    private static boolean boolOrDefault(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }
    private static int boundedInt(JsonObject json, String key, int min, int max) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()) throw new IllegalArgumentException("missing numeric field " + key);
        int v = json.get(key).getAsInt();
        if (v < min || v > max) throw new IllegalArgumentException(key + " outside permitted range");
        return v;
    }
    private static int boundedIntOrDefault(JsonObject json, String key, int fallback, int min, int max) {
        return json.has(key) ? boundedInt(json, key, min, max) : fallback;
    }
    private static double boundedDoubleOrDefault(JsonObject json, String key, double fallback, double min, double max) {
        if (!json.has(key)) return fallback;
        if (!json.get(key).isJsonPrimitive()) throw new IllegalArgumentException("missing numeric field " + key);
        double v = json.get(key).getAsDouble();
        if (!Double.isFinite(v) || v < min || v > max) throw new IllegalArgumentException(key + " outside permitted range");
        return v;
    }
    public record Snapshot(Map<ResourceLocation, NoiseEventDefinition> noiseEvents,
                           Map<ResourceLocation, HordeArchetypeDefinition> hordeArchetypes) {
        public Snapshot { noiseEvents = Map.copyOf(noiseEvents); hordeArchetypes = Map.copyOf(hordeArchetypes); }
        public static Snapshot empty() { return new Snapshot(Map.of(), Map.of()); }
    }
    public record NoiseEventDefinition(ResourceLocation id, int noiseRadius, int lureDurationTicks,
                                       int cooldownTicks, ResourceLocation targetTag, int maxAffectedEntities) {}
    public record HordeArchetypeDefinition(ResourceLocation id, ResourceLocation entityTag, boolean leader,
                                           int followerRadius, int maxFollowers, double speedMultiplier) {}
}
