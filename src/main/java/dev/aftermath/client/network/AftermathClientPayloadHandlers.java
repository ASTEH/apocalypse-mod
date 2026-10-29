package dev.aftermath.client.network;

import com.google.gson.Gson;
import dev.aftermath.AftermathMod;
import dev.aftermath.client.gui.CharacterSelectionScreen;
import dev.aftermath.network.payload.CharacterCatalogPayload;
import dev.aftermath.network.payload.CharacterSelectionResultPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client-only payload registration; this class is never loaded on a dedicated server. */
@EventBusSubscriber(modid = AftermathMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AftermathClientPayloadHandlers {
    private static final Gson GSON = new Gson();

    private AftermathClientPayloadHandlers() {}

    @SubscribeEvent
    public static void registerClientPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(CharacterCatalogPayload.TYPE, AftermathClientPayloadHandlers::handleCatalog);
        event.register(CharacterSelectionResultPayload.TYPE, AftermathClientPayloadHandlers::handleSelectionResult);
    }

    private static void handleCatalog(CharacterCatalogPayload payload, IPayloadContext context) {
        if (!payload.openScreen()) {
            return;
        }

        try {
            Minecraft.getInstance().setScreen(new CharacterSelectionScreen(payload.catalogJson()));
        } catch (RuntimeException exception) {
            AftermathMod.LOGGER.error("Could not open the character selection screen.", exception);
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.translatable("message.aftermath.character.ui.open_failed"), false);
            }
        }
    }

    private static void handleSelectionResult(CharacterSelectionResultPayload payload, IPayloadContext context) {
        if (Minecraft.getInstance().screen instanceof CharacterSelectionScreen screen) {
            screen.handleServerResult(payload.resultJson());
            return;
        }

        try {
            CharacterNetworkResponse response = GSON.fromJson(payload.resultJson(), CharacterNetworkResponse.class);
            if (response != null && Minecraft.getInstance().player != null && !response.accepted()) {
                Minecraft.getInstance().player.displayClientMessage(
                        Component.translatable("message.aftermath.character.error." + response.errorCode()), false);
            }
        } catch (RuntimeException exception) {
            AftermathMod.LOGGER.warn("Received an unreadable character selection response.", exception);
        }
    }

    private record CharacterNetworkResponse(boolean accepted, String errorCode) {}
}
