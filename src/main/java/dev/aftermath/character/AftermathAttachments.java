package dev.aftermath.character;

import dev.aftermath.AftermathMod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class AftermathAttachments {
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AftermathMod.MOD_ID);

    public static final Supplier<AttachmentType<CharacterProfile>> CHARACTER_PROFILE = ATTACHMENTS.register(
            "character_profile",
            () -> AttachmentType.builder(CharacterProfile::empty)
                    .serialize(CharacterProfile.CODEC)
                    .copyOnDeath()
                    .build()
    );

    private AftermathAttachments() {}

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }
}
