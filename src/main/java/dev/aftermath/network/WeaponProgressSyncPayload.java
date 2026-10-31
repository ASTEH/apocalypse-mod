package dev.aftermath.network;

import dev.aftermath.AftermathMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-authoritative firearm XP and client handling feedback settings. */
public record WeaponProgressSyncPayload(String experienceCsv, float untrainedAimTimeMultiplier,
                                        float untrainedRecoilPitch, float untrainedRecoilYaw)
        implements CustomPacketPayload {
    public static final Type<WeaponProgressSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "weapon_progress_sync"));

    public static final StreamCodec<ByteBuf, WeaponProgressSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, WeaponProgressSyncPayload::experienceCsv,
            ByteBufCodecs.FLOAT, WeaponProgressSyncPayload::untrainedAimTimeMultiplier,
            ByteBufCodecs.FLOAT, WeaponProgressSyncPayload::untrainedRecoilPitch,
            ByteBufCodecs.FLOAT, WeaponProgressSyncPayload::untrainedRecoilYaw,
            WeaponProgressSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
