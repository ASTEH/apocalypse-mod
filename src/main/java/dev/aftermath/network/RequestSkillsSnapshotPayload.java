package dev.aftermath.network;

import dev.aftermath.AftermathMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client request for the current server-authoritative skill snapshot. */
public record RequestSkillsSnapshotPayload() implements CustomPacketPayload {
    public static final Type<RequestSkillsSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "request_skills_snapshot"));
    public static final StreamCodec<ByteBuf, RequestSkillsSnapshotPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestSkillsSnapshotPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
