package dev.aftermath.character;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.aftermath.AftermathMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Reads definitions from data/<namespace>/aftermath/{professions,traits}/.
 * A bad reload does not replace the last known-good catalog.
 */
public final class CharacterDefinitionReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new GsonBuilder().create();

    public CharacterDefinitionReloadListener() {
        super(GSON, "aftermath");
    }

    public static void register(AddReloadListenerEvent event) {
        event.addListener(new CharacterDefinitionReloadListener());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> entries, ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<ResourceLocation, CharacterProfession> professions = new LinkedHashMap<>();
        Map<ResourceLocation, CharacterTrait> traits = new LinkedHashMap<>();
        boolean invalid = false;

        for (Map.Entry<ResourceLocation, JsonElement> entry : entries.entrySet()) {
            String path = entry.getKey().getPath();
            try {
                if (path.startsWith("professions/")) {
                    CharacterProfession profession = parseProfession(entry.getKey(), entry.getValue());
                    professions.put(profession.id(), profession);
                } else if (path.startsWith("traits/")) {
                    CharacterTrait trait = parseTrait(entry.getKey(), entry.getValue());
                    traits.put(trait.id(), trait);
                }
            } catch (RuntimeException exception) {
                invalid = true;
                AftermathMod.LOGGER.error("Invalid Aftermath character definition '{}': {}",
                        entry.getKey(), exception.getMessage());
            }
        }

        if (professions.isEmpty() || traits.isEmpty()) {
            invalid = true;
            AftermathMod.LOGGER.error("Aftermath character reload found no professions or no traits; keeping previous definitions.");
        }

        for (CharacterProfession profession : professions.values()) {
            for (ResourceLocation traitId : profession.allowedTraits()) {
                if (!traits.containsKey(traitId)) {
                    invalid = true;
                    AftermathMod.LOGGER.error("Profession '{}' refers to missing trait '{}'.", profession.id(), traitId);
                }
            }
        }

        for (CharacterTrait trait : traits.values()) {
            for (ResourceLocation conflict : trait.conflicts()) {
                if (trait.id().equals(conflict) || !traits.containsKey(conflict)) {
                    invalid = true;
                    AftermathMod.LOGGER.error("Trait '{}' has invalid conflict '{}'.", trait.id(), conflict);
                }
            }
        }

        if (invalid) {
            AftermathMod.LOGGER.warn("Rejected Aftermath character definitions reload; the last valid snapshot remains active.");
            return;
        }

        CharacterCatalog.replace(professions, traits);
        AftermathMod.LOGGER.info("Loaded {} professions and {} character traits.",
                professions.size(), traits.size());
    }

    private static CharacterProfession parseProfession(ResourceLocation fileId, JsonElement element) {
        JsonObject json = GsonHelper.convertToJsonObject(element, "profession");
        ResourceLocation id = definitionId(fileId, "professions/");
        String name = requiredString(json, "name");
        String description = requiredString(json, "description");
        int points = GsonHelper.getAsInt(json, "starting_points");
        if (points < 0 || points > 100) {
            throw new JsonParseException("'starting_points' must be between 0 and 100.");
        }
        Set<ResourceLocation> allowedTraits = readIds(GsonHelper.getAsJsonArray(json, "allowed_traits"), "allowed_traits");
        return new CharacterProfession(id, name, description, points, allowedTraits);
    }

    private static CharacterTrait parseTrait(ResourceLocation fileId, JsonElement element) {
        JsonObject json = GsonHelper.convertToJsonObject(element, "trait");
        ResourceLocation id = definitionId(fileId, "traits/");
        String name = requiredString(json, "name");
        String description = requiredString(json, "description");
        String categoryText = requiredString(json, "category");
        CharacterTrait.Category category = switch (categoryText) {
            case "positive" -> CharacterTrait.Category.POSITIVE;
            case "negative" -> CharacterTrait.Category.NEGATIVE;
            default -> throw new JsonParseException("'category' must be 'positive' or 'negative'.");
        };

        int pointCost = GsonHelper.getAsInt(json, "point_cost", 0);
        int pointCredit = GsonHelper.getAsInt(json, "point_credit", 0);
        if (category == CharacterTrait.Category.POSITIVE) {
            if (pointCost < 1 || pointCost > 100 || pointCredit != 0) {
                throw new JsonParseException("Positive traits require point_cost 1..100 and must not define point_credit.");
            }
        } else if (pointCredit < 1 || pointCredit > 100 || pointCost != 0) {
            throw new JsonParseException("Negative traits require point_credit 1..100 and must not define point_cost.");
        }

        Set<ResourceLocation> conflicts = json.has("conflicts")
                ? readIds(GsonHelper.getAsJsonArray(json, "conflicts"), "conflicts")
                : Set.of();
        return new CharacterTrait(id, name, description, category, pointCost, pointCredit, conflicts);
    }

    private static ResourceLocation definitionId(ResourceLocation fileId, String folderPrefix) {
        String path = fileId.getPath();
        if (!path.startsWith(folderPrefix)) {
            throw new JsonParseException("Unexpected definition folder: " + path);
        }
        String shortPath = path.substring(folderPrefix.length());
        if (shortPath.isBlank()) {
            throw new JsonParseException("Definition file has an empty id.");
        }
        return ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), shortPath);
    }

    private static String requiredString(JsonObject json, String key) {
        String value = GsonHelper.getAsString(json, key).trim();
        if (value.isEmpty()) {
            throw new JsonParseException("'" + key + "' must not be empty.");
        }
        return value;
    }

    private static Set<ResourceLocation> readIds(JsonArray values, String fieldName) {
        Set<ResourceLocation> result = new LinkedHashSet<>();
        for (JsonElement value : values) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new JsonParseException("'" + fieldName + "' entries must be resource location strings.");
            }
            ResourceLocation id = ResourceLocation.tryParse(value.getAsString());
            if (id == null) {
                throw new JsonParseException("Invalid resource location in '" + fieldName + "': " + value.getAsString());
            }
            if (!result.add(id)) {
                throw new JsonParseException("Duplicate id in '" + fieldName + "': " + id);
            }
        }
        return Set.copyOf(result);
    }
}
