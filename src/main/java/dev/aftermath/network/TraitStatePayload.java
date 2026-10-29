package dev.aftermath.network;

import dev.aftermath.AftermathMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-authoritative selected traits and effective firearm multipliers for client-only TaCZ visuals.
 */
public record TraitStatePayload(String selectedTraitIdsCsv,
                                float eagleEyeSpreadMultiplier,
                                float quickReloadDurationMultiplier) implements CustomPacketPayload {
    public static final Type<TraitStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "trait_state"));
    public static final StreamCodec<ByteBuf, TraitStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, TraitStatePayload::selectedTraitIdsCsv,
            ByteBufCodecs.FLOAT, TraitStatePayload::eagleEyeSpreadMultiplier,
            ByteBufCodecs.FLOAT, TraitStatePayload::quickReloadDurationMultiplier,
            TraitStatePayload::new);

    /** Backwards-compatible constructor for callers that only need to refresh selected trait IDs. */
    public TraitStatePayload(String selectedTraitIdsCsv) {
        this(selectedTraitIdsCsv, 1.0F, 1.0F);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
