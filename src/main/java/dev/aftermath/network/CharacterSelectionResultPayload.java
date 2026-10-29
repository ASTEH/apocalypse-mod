package dev.aftermath.network;

import dev.aftermath.AftermathMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CharacterSelectionResultPayload(boolean success, String messageKey) implements CustomPacketPayload {
    public static final Type<CharacterSelectionResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "character_selection_result"));
    public static final StreamCodec<ByteBuf, CharacterSelectionResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, CharacterSelectionResultPayload::success,
            ByteBufCodecs.STRING_UTF8, CharacterSelectionResultPayload::messageKey,
            CharacterSelectionResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
