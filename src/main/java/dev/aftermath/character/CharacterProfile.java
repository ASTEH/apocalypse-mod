package dev.aftermath.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Persistent server-authoritative character selection.
 *
 * Trait effects are intentionally applied by later systems; this record only stores
 * the validated profession and trait IDs.
 */
public record CharacterProfile(String professionId, List<String> traitIds, boolean complete) {
    public static final Codec<CharacterProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("profession", "").forGetter(CharacterProfile::professionId),
            Codec.STRING.listOf().optionalFieldOf("traits", List.of()).forGetter(CharacterProfile::traitIds),
            Codec.BOOL.optionalFieldOf("complete", false).forGetter(CharacterProfile::complete)
    ).apply(instance, CharacterProfile::new));

    public CharacterProfile {
        professionId = professionId == null ? "" : professionId;
        traitIds = traitIds == null ? List.of() : List.copyOf(traitIds);
    }

    public static CharacterProfile empty() {
        return new CharacterProfile("", List.of(), false);
    }
}
