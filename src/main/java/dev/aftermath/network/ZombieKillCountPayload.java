package dev.aftermath.network;

import dev.aftermath.AftermathMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-authoritative lifetime zombie-like kill count for the local HUD. */
public record ZombieKillCountPayload(int count) implements CustomPacketPayload {
    public static final Type<ZombieKillCountPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "zombie_kill_count"));

    public static final StreamCodec<ByteBuf, ZombieKillCountPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ZombieKillCountPayload::count,
            ZombieKillCountPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
