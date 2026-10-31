package dev.aftermath.network;

import dev.aftermath.AftermathMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Compact JSON presentation snapshot; all XP and levels are calculated on the server. */
public record SkillsSnapshotPayload(String snapshotJson) implements CustomPacketPayload {
    public static final Type<SkillsSnapshotPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "skills_snapshot"));
    public static final StreamCodec<ByteBuf, SkillsSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, SkillsSnapshotPayload::snapshotJson,
            SkillsSnapshotPayload::new
    );

    public SkillsSnapshotPayload {
        snapshotJson = snapshotJson == null ? "{\"skills\":[]}" : snapshotJson;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
