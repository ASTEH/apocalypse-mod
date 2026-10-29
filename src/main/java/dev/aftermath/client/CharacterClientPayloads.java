package dev.aftermath.client;

import dev.aftermath.AftermathMod;
import dev.aftermath.network.CharacterScreenPayload;
import dev.aftermath.network.CharacterSelectionResultPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = AftermathMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
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
