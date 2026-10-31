package dev.aftermath.network;

import dev.aftermath.AftermathMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client request only. The server computes and validates the complete movement. */
public record ParkourActionPayload() implements CustomPacketPayload {
    public static final Type<ParkourActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "parkour_action"));
    public static final StreamCodec<ByteBuf, ParkourActionPayload> STREAM_CODEC =
            StreamCodec.unit(new ParkourActionPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
