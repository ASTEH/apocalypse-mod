package dev.aftermath.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persistent, server-owned character selection and starting professional skill levels. */
public record CharacterProfile(String professionId, List<String> traitIds, boolean created,
                               Map<String, Integer> skillLevels) {
    public static final Codec<CharacterProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("profession_id", "").forGetter(CharacterProfile::professionId),
            Codec.STRING.listOf().optionalFieldOf("trait_ids", List.of()).forGetter(CharacterProfile::traitIds),
            Codec.BOOL.optionalFieldOf("created", false).forGetter(CharacterProfile::created),
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .optionalFieldOf("skill_levels", Map.of())
                    .forGetter(CharacterProfile::skillLevels)
    ).apply(instance, CharacterProfile::new));

    /** Backwards-compatible constructor for profiles created before starting skills existed. */
    public CharacterProfile(String professionId, List<String> traitIds, boolean created) {
        this(professionId, traitIds, created, Map.of());
    }

    public CharacterProfile {
        professionId = professionId == null ? "" : professionId;
        traitIds = traitIds == null ? List.of() : List.copyOf(traitIds);
        Map<String, Integer> sanitized = new LinkedHashMap<>();
        if (skillLevels != null) {
            skillLevels.forEach((skill, level) -> {
                if (skill != null && skill.matches("[a-z0-9_.-]+") && level != null && level > 0) {
                    sanitized.put(skill, Math.min(level, 10));
                }
            });
        }
        skillLevels = Map.copyOf(sanitized);
    }

    public static CharacterProfile empty() {
        return new CharacterProfile("", List.of(), false, Map.of());
    }
}
