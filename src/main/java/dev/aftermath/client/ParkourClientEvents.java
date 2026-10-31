package dev.aftermath.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aftermath.network.ParkourActionPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Rebindable parkour action; the client sends no position or movement coordinates. */
public final class ParkourClientEvents {
    private static final KeyMapping PARKOUR = new KeyMapping(
            "key.aftermath.parkour",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.aftermath");

    private ParkourClientEvents() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ParkourClientEvents::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(ParkourClientEvents::onClientTick);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(PARKOUR);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null || minecraft.screen != null) return;
        while (PARKOUR.consumeClick()) {
            PacketDistributor.sendToServer(new ParkourActionPayload());
        }
    }
}
