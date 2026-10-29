package dev.aftermath.client;

import dev.aftermath.network.CharacterScreenPayload;
import dev.aftermath.network.CharacterSelectionResultPayload;
import net.minecraft.client.Minecraft;

/**
 * Client-only handlers invoked directly by AftermathNetworking for clientbound payloads.
 * This class is deliberately not an EventBusSubscriber: it has no @SubscribeEvent methods.
 */
public final class CharacterClientPayloads {
    public static void open(CharacterScreenPayload payload) {
        Minecraft.getInstance().setScreen(new CharacterSelectionScreen(payload.definitionsJson()));
    }

    public static void handleResult(CharacterSelectionResultPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        if (payload.success()) {
            if (minecraft.screen instanceof CharacterSelectionScreen) {
                minecraft.setScreen(null);
            }
        } else if (minecraft.screen instanceof CharacterSelectionScreen screen) {
            screen.setResult(payload.messageKey());
        }
    }

    private CharacterClientPayloads() {}
}
