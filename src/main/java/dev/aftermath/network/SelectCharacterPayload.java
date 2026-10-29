package dev.aftermath.network;

import dev.aftermath.AftermathMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SelectCharacterPayload(String professionId, String traitIdsCsv) implements CustomPacketPayload {
    public static final Type<SelectCharacterPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "select_character"));
    public static final StreamCodec<ByteBuf, SelectCharacterPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SelectCharacterPayload::professionId,
            ByteBufCodecs.STRING_UTF8, SelectCharacterPayload::traitIdsCsv,
            SelectCharacterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
