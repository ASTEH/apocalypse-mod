package dev.aftermath.client;

import dev.aftermath.AftermathMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/** Compact PZ-inspired search radar. It is a HUD layer and never captures movement input. */
public final class ForagingHud {
    private static final ResourceLocation LAYER_ID =
            ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "foraging_search_hud");

    private ForagingHud() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ForagingHud::registerLayers);
    }

    private static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(LAYER_ID, ForagingHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) return;

        ForagingClientState.Snapshot snapshot = ForagingClientState.snapshot();
        if (!snapshot.active()) return;

        Font font = minecraft.font;
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int panelWidth = Math.max(150, Math.min(224, screenWidth - 12));
        int panelHeight = 132;
        int x = 6;
        int y = 8;
        int right = x + panelWidth;

        graphics.fill(x, y, right, y + panelHeight, 0xD10B1118);
        graphics.fill(x, y, right, y + 2, 0xFF7CB7B8);
        graphics.fill(x, y + panelHeight - 1, right, y + panelHeight, 0xFF35444B);
        graphics.fill(x, y, x + 1, y + panelHeight, 0xFF35444B);

        graphics.drawString(font, Component.translatable("screen.aftermath.foraging.title"),
                x + 8, y + 7, 0xFFF1F4F2, false);
        Component focus = Component.translatable("screen.aftermath.foraging.category." + snapshot.focus());
        graphics.drawString(font, Component.translatable("screen.aftermath.foraging.focus", focus),
                x + 8, y + 21, 0xFFB8D4D1, false);
        graphics.drawString(font, Component.translatable("screen.aftermath.foraging.radius",
                        String.format(java.util.Locale.ROOT, "%.1f", snapshot.radius())),
                x + 8, y + 34, 0xFFD4DADD, false);

        int radarX = x + 45;
        int radarY = y + 91;
        drawRing(graphics, radarX, radarY, 32, 0xFF5B7A80);
        drawRing(graphics, radarX, radarY, 16, 0x804A6269);
        graphics.fill(radarX - 32, radarY, radarX + 33, radarY + 1, 0x705B7A80);
        graphics.fill(radarX, radarY - 32, radarX + 1, radarY + 33, 0x705B7A80);
        graphics.fill(radarX - 2, radarY - 2, radarX + 3, radarY + 3, 0xFFE7D8A6);

        for (ForagingClientState.Find find : snapshot.finds()) {
            double scale = Math.max(0.1D, snapshot.radius());
            int markerX = radarX + (int) Math.round(
                    (find.position().getX() + 0.5D - minecraft.player.getX()) / scale * 29.0D);
            int markerY = radarY + (int) Math.round(
                    (find.position().getZ() + 0.5D - minecraft.player.getZ()) / scale * 29.0D);
            markerX = Math.max(radarX - 29, Math.min(radarX + 29, markerX));
            markerY = Math.max(radarY - 29, Math.min(radarY + 29, markerY));

            if (find.revealed() && find.itemId() != null) {
                Item item = BuiltInRegistries.ITEM.get(find.itemId());
                if (item != Items.AIR) {
                    graphics.renderItem(new ItemStack(item), markerX - 8, markerY - 8);
                    continue;
                }
            }
            drawEye(graphics, markerX, markerY);
        }

        int textX = x + 88;
        graphics.drawString(font, Component.translatable("screen.aftermath.foraging.discovered",
                snapshot.finds().size()), textX, y + 59, 0xFFD4DADD, false);

        int shown = 0;
        for (ForagingClientState.Find find : snapshot.finds()) {
            if (!find.revealed() || find.itemId() == null || shown >= 2) continue;
            Item item = BuiltInRegistries.ITEM.get(find.itemId());
            if (item == Items.AIR) continue;
            int lineY = y + 75 + shown * 18;
            ItemStack stack = new ItemStack(item);
            graphics.renderItem(stack, textX, lineY - 4);
            String name = stack.getHoverName().getString();
            int availableWidth = Math.max(24, panelWidth - (textX - x) - 45);
            while (!name.isEmpty() && font.width(name) > availableWidth) {
                name = name.substring(0, name.length() - 1);
            }
            if (!name.equals(stack.getHoverName().getString()) && name.length() > 1) {
                name = name.substring(0, Math.max(1, name.length() - 1)) + "…";
            }
            graphics.drawString(font, name + " ×" + find.count(), textX + 19, lineY + 1,
                    0xFFE7E9E6, false);
            shown++;
        }

        graphics.drawString(font, Component.translatable("screen.aftermath.foraging.controls"),
                x + 8, y + 118, 0xFF9BA9AE, false);
    }

    private static void drawRing(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        for (int i = 0; i < 48; i++) {
            double angle = (Math.PI * 2.0D * i) / 48.0D;
            int px = centerX + (int) Math.round(Math.cos(angle) * radius);
            int py = centerY + (int) Math.round(Math.sin(angle) * radius);
            graphics.fill(px, py, px + 1, py + 1, color);
        }
    }

    private static void drawEye(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 4, y - 1, x + 5, y + 2, 0xFFE4C778);
        graphics.fill(x - 2, y - 3, x + 3, y + 4, 0xFFE4C778);
        graphics.fill(x - 1, y - 1, x + 2, y + 2, 0xFF26343A);
    }
}
