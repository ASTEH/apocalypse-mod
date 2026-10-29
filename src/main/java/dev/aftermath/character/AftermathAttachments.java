package dev.aftermath.character;

import dev.aftermath.AftermathMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Registered persistent attachments used by the character system. */
public final class AftermathAttachments {
    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AftermathMod.MOD_ID);

    public static final Supplier<AttachmentType<CharacterProfile>> CHARACTER_PROFILE = TYPES.register(
            "character_profile",
            () -> AttachmentType.builder(CharacterProfile::empty)
                    .serialize(CharacterProfile.CODEC)
                    .copyOnDeath()
                    .build()
    );

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }

    private AftermathAttachments() {}
}
