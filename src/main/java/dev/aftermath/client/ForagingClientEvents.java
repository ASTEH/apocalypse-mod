package dev.aftermath.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aftermath.network.CollectForagePayload;
import dev.aftermath.network.SetForagingFocusPayload;
import dev.aftermath.network.ToggleForagingSearchPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Rebindable non-blocking controls for search, category focus and collecting a revealed find. */
public final class ForagingClientEvents {
    private static final KeyMapping TOGGLE_SEARCH = new KeyMapping(
            "key.aftermath.toggle_foraging",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_END,
            "key.categories.aftermath");
    private static final KeyMapping NEXT_FOCUS = new KeyMapping(
            "key.aftermath.next_foraging_focus",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.aftermath");
    private static final KeyMapping COLLECT_FIND = new KeyMapping(
            "key.aftermath.collect_foraging",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_H,
            "key.categories.aftermath");

    private ForagingClientEvents() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ForagingClientEvents::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(ForagingClientEvents::onClientTick);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_SEARCH);
        event.register(NEXT_FOCUS);
        event.register(COLLECT_FIND);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null || minecraft.screen != null) return;

        while (TOGGLE_SEARCH.consumeClick()) {
            PacketDistributor.sendToServer(new ToggleForagingSearchPayload());
        }

        if (!ForagingClientState.snapshot().active()) return;

        while (NEXT_FOCUS.consumeClick()) {
            String next = ForagingClientState.nextFocus();
            if (!next.isBlank()) PacketDistributor.sendToServer(new SetForagingFocusPayload(next));
        }

        while (COLLECT_FIND.consumeClick()) {
            var player = minecraft.player;
            var find = ForagingClientState.nearestCollectable(player.getX(), player.getY(), player.getZ());
            if (find != null) {
                PacketDistributor.sendToServer(new CollectForagePayload(find.position().asLong()));
            }
        }
    }
}
