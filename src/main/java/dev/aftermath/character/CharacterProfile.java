package dev.aftermath.character;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/** Persistent, server-owned character selection. */
public record CharacterProfile(String professionId, List<String> traitIds, boolean created) {
    public static final Codec<CharacterProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("profession_id", "").forGetter(CharacterProfile::professionId),
            Codec.STRING.listOf().optionalFieldOf("trait_ids", List.of()).forGetter(CharacterProfile::traitIds),
            Codec.BOOL.optionalFieldOf("created", false).forGetter(CharacterProfile::created)
    ).apply(instance, CharacterProfile::new));

    public CharacterProfile {
        professionId = professionId == null ? "" : professionId;
        traitIds = traitIds == null ? List.of() : List.copyOf(traitIds);
    }

    public static CharacterProfile empty() {
        return new CharacterProfile("", List.of(), false);
    }
}
