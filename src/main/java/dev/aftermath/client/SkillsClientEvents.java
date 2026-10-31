package dev.aftermath.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aftermath.network.RequestSkillsSnapshotPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Client-only, rebindable entry point for the unified survivor skills screen. */
public final class SkillsClientEvents {
    private static final KeyMapping OPEN_SKILLS = new KeyMapping(
            "key.aftermath.open_skills",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.aftermath"
    );

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(SkillsClientEvents::registerKeyMappings);
        NeoForge.EVENT_BUS.addListener(SkillsClientEvents::onClientTick);
    }

    private static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SKILLS);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        while (OPEN_SKILLS.consumeClick()) {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.getConnection() == null || minecraft.screen != null) {
                continue;
            }
            PacketDistributor.sendToServer(new RequestSkillsSnapshotPayload());
        }
    }

    private SkillsClientEvents() {}
}
