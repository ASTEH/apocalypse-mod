package dev.aftermath.network;

import dev.aftermath.AftermathMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bounded HUD snapshot; hidden finds do not reveal their item identifier. */
public record ForagingSnapshotPayload(String snapshotJson) implements CustomPacketPayload {
    public static final Type<ForagingSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "foraging_snapshot"));
    public static final StreamCodec<ByteBuf, ForagingSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ForagingSnapshotPayload::snapshotJson,
            ForagingSnapshotPayload::new);

    public ForagingSnapshotPayload {
        snapshotJson = snapshotJson == null ? "{\"version\":1,\"active\":false,\"finds\":[]}" : snapshotJson;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
