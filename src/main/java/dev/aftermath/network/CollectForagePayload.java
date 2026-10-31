package dev.aftermath.network;

import dev.aftermath.AftermathMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Requests collection of a nearby, server-discovered forage point. */
public record CollectForagePayload(long packedPosition) implements CustomPacketPayload {
    public static final Type<CollectForagePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "collect_forage"));
    public static final StreamCodec<ByteBuf, CollectForagePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, CollectForagePayload::packedPosition,
            CollectForagePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
