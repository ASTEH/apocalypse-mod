package dev.aftermath.network.payload;

import dev.aftermath.AftermathMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client requests a selection; the server always validates it independently. */
public record CharacterSelectionRequestPayload(String professionId, String traitIdsCsv) implements CustomPacketPayload {
    public static final Type<CharacterSelectionRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "character_selection_request"));

    public static final StreamCodec<ByteBuf, CharacterSelectionRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CharacterSelectionRequestPayload::professionId,
            ByteBufCodecs.STRING_UTF8, CharacterSelectionRequestPayload::traitIdsCsv,
            CharacterSelectionRequestPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
