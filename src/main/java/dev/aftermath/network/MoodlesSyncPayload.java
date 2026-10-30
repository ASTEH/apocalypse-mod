package dev.aftermath.network;

import dev.aftermath.AftermathMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-to-client snapshot of the currently active Moodles; never accepts client mutations. */
public record MoodlesSyncPayload(String entriesJson) implements CustomPacketPayload {
    public static final Type<MoodlesSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "moodles_sync"));
    public static final StreamCodec<ByteBuf, MoodlesSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, MoodlesSyncPayload::entriesJson,
            MoodlesSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
