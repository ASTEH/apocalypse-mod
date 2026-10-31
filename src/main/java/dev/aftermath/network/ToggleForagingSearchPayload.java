package dev.aftermath.network;

import dev.aftermath.AftermathMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client request to toggle the server-authoritative scavenging mode. */
public record ToggleForagingSearchPayload() implements CustomPacketPayload {
    public static final Type<ToggleForagingSearchPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "toggle_foraging_search"));
    public static final StreamCodec<ByteBuf, ToggleForagingSearchPayload> STREAM_CODEC =
            StreamCodec.unit(new ToggleForagingSearchPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
