package dev.aftermath.network;

import dev.aftermath.AftermathMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Requests a search focus; the server validates it against loaded datapack categories. */
public record SetForagingFocusPayload(String categoryId) implements CustomPacketPayload {
    public static final Type<SetForagingFocusPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "set_foraging_focus"));
    public static final StreamCodec<ByteBuf, SetForagingFocusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SetForagingFocusPayload::categoryId,
            SetForagingFocusPayload::new);

    public SetForagingFocusPayload {
        categoryId = categoryId == null ? "" : categoryId;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
