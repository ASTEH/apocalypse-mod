package dev.aftermath.client;

import dev.aftermath.AftermathMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Small, unobtrusive lifetime zombie kill counter in the top-right HUD corner. */
public final class ZombieKillHud {
    private static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "zombie_kill_counter");
    private static volatile int count;

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ZombieKillHud::registerLayers);
    }

    private static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER_ID, ZombieKillHud::render);
    }

    public static void setCount(int value) {
        count = Math.max(0, value);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return;

        Component label = Component.translatable("hud.aftermath.zombie_kills", count);
        int textWidth = minecraft.font.width(label);
        int x = minecraft.getWindow().getGuiScaledWidth() - textWidth - 12;
        int y = 8;
        graphics.fill(x - 5, y - 3, x + textWidth + 5, y + minecraft.font.lineHeight + 3,
                0x900D1117);
        graphics.drawString(minecraft.font, label, x, y, 0xFFE8E8E8, true);
    }

    private ZombieKillHud() {}
}
