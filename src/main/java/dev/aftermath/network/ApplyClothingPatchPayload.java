package dev.aftermath.network;

import dev.aftermath.AftermathMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Requests server-authoritative clothing reinforcement from the creative inventory. */
public record ApplyClothingPatchPayload(int inventorySlot) implements CustomPacketPayload {
    public static final Type<ApplyClothingPatchPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "apply_clothing_patch"));
    public static final StreamCodec<ByteBuf, ApplyClothingPatchPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ApplyClothingPatchPayload::inventorySlot,
            ApplyClothingPatchPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
