package dev.aftermath.network;

import dev.aftermath.AftermathMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-authoritative firearm XP and client handling feedback settings. */
public record WeaponProgressSyncPayload(String experienceCsv, boolean progressionEnabled,
                                        float untrainedAimTimeMultiplier, float untrainedRecoilPitch,
                                        float untrainedRecoilYaw, String handlingMultipliersCsv)
        implements CustomPacketPayload {
    public static final Type<WeaponProgressSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "weapon_progress_sync"));

    // StreamCodec.composite supports up to six fields in this Minecraft version.
    public static final StreamCodec<ByteBuf, WeaponProgressSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, WeaponProgressSyncPayload::experienceCsv,
            ByteBufCodecs.BOOL, WeaponProgressSyncPayload::progressionEnabled,
            ByteBufCodecs.FLOAT, WeaponProgressSyncPayload::untrainedAimTimeMultiplier,
            ByteBufCodecs.FLOAT, WeaponProgressSyncPayload::untrainedRecoilPitch,
            ByteBufCodecs.FLOAT, WeaponProgressSyncPayload::untrainedRecoilYaw,
            ByteBufCodecs.STRING_UTF8, WeaponProgressSyncPayload::handlingMultipliersCsv,
            WeaponProgressSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
