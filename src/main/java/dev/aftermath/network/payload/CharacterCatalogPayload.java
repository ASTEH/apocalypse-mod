package dev.aftermath.network.payload;

import dev.aftermath.AftermathMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-authored character definitions for the client selection screen. */
public record CharacterCatalogPayload(String catalogJson, boolean openScreen) implements CustomPacketPayload {
    public static final Type<CharacterCatalogPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "character_catalog"));

    public static final StreamCodec<ByteBuf, CharacterCatalogPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CharacterCatalogPayload::catalogJson,
            ByteBufCodecs.BOOL, CharacterCatalogPayload::openScreen,
            CharacterCatalogPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
