package dev.aftermath.character;

import dev.aftermath.AftermathMod;
import dev.aftermath.medical.BleedingStatus;
import com.mojang.serialization.Codec;
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

    public static final Supplier<AttachmentType<WeaponProgress>> WEAPON_PROGRESS = TYPES.register(
            "weapon_progress",
            () -> AttachmentType.builder(WeaponProgress::empty)
                    .serialize(WeaponProgress.CODEC)
                    .copyOnDeath()
                    .build()
    );

    public static final Supplier<AttachmentType<dev.aftermath.learning.LearningProgress>> LEARNING_PROGRESS = TYPES.register(
            "learning_progress",
            () -> AttachmentType.builder(dev.aftermath.learning.LearningProgress::empty)
                    .serialize(dev.aftermath.learning.LearningProgress.CODEC)
                    .copyOnDeath()
                    .build()
    );

    /** Injuries persist through relogs, but deliberately do not copy to a new life on death. */
    /** Lifetime zombie-like kill total; serialized and copied to the respawned player. */
    public static final Supplier<AttachmentType<Integer>> ZOMBIE_KILLS = TYPES.register(
            "zombie_kills",
            () -> AttachmentType.builder(() -> 0)
                    .serialize(Codec.INT)
                    .copyOnDeath()
                    .build()
    );

    public static final Supplier<AttachmentType<BleedingStatus>> BLEEDING_STATUS = TYPES.register(
            "bleeding_status",
            () -> AttachmentType.builder(BleedingStatus::empty)
                    .serialize(BleedingStatus.CODEC)
                    .build()
    );

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }

    private AftermathAttachments() {}
}
