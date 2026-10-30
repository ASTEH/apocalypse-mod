package dev.aftermath.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aftermath.AftermathMod;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** Project-Zomboid-inspired compact Moodles column in the top-right HUD corner. */
public final class MoodlesHud {
    private static volatile List<MoodleView> entries = List.of();

    private MoodlesHud() {}

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(MoodlesHud::registerLayers);
    }

    private static void registerLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AftermathMod.MOD_ID, "moodles"),
                MoodlesHud::render);
    }

    public static void setEntriesJson(String json) {
        try {
            var parsed = JsonParser.parseString(json);
            if (!parsed.isJsonArray()) {
                entries = List.of();
                return;
            }
            List<MoodleView> next = new ArrayList<>();
            for (JsonElement element : parsed.getAsJsonArray()) {
                if (!element.isJsonObject()) continue;
                JsonObject object = element.getAsJsonObject();
                Map<String, Double> effects = new LinkedHashMap<>();
                if (object.has("effects") && object.get("effects").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> effect : object.getAsJsonObject("effects").entrySet()) {
                        if (effect.getValue().isJsonPrimitive()
                                && effect.getValue().getAsJsonPrimitive().isNumber()) {
                            effects.put(effect.getKey(), effect.getValue().getAsDouble());
                        }
                    }
                }
                next.add(new MoodleView(
                        string(object, "id"),
                        string(object, "name_key"),
                        string(object, "description_key"),
                        string(object, "level_id"),
                        string(object, "level_name_key"),
                        string(object, "level_description_key"),
                        string(object, "icon"),
                        number(object, "value"),
                        (int) number(object, "severity"),
                        Map.copyOf(effects)));
            }
            next.sort(Comparator.comparingInt(MoodleView::severity).reversed());
            entries = List.copyOf(next);
        } catch (Exception exception) {
            AftermathMod.LOGGER.warn("Could not parse Moodles HUD snapshot: {}", exception.getMessage());
            entries = List.of();
        }
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsString() : "";
    }

    private static double number(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsDouble() : 0.0D;
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui || entries.isEmpty()) return;

        Font font = minecraft.font;
        int x = minecraft.getWindow().getGuiScaledWidth() - 8 - 26;
        int y = 34; // Keep the existing zombie counter at y=8 unobstructed.
        double vignette = entries.stream()
                .mapToDouble(entry -> entry.effects().getOrDefault("vignette_additive", 0.0D))
                .max().orElse(0.0D);
        if (vignette > 0.0D) {
            int alpha = Math.max(0, Math.min(112, (int) Math.round(vignette * 180.0D)));
            int thickness = 8 + (int) Math.round(vignette * 34.0D);
            int edge = (alpha << 24) | 0x00551A1A;
            int width = minecraft.getWindow().getGuiScaledWidth();
            int height = minecraft.getWindow().getGuiScaledHeight();
            graphics.fill(0, 0, width, thickness, edge);
            graphics.fill(0, height - thickness, width, height, edge);
            graphics.fill(0, thickness, thickness, height - thickness, edge);
            graphics.fill(width - thickness, thickness, width, height - thickness, edge);
        }
        int mouseX = (int) (minecraft.mouseHandler.xpos()
                * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth());
        int mouseY = (int) (minecraft.mouseHandler.ypos()
                * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight());

        MoodleView hovered = null;
        int hoveredY = y;
        int shown = Math.min(entries.size(), 8);
        for (int index = 0; index < shown; index++) {
            MoodleView moodle = entries.get(index);
            int itemY = y + index * 30;
            int accent = severityColor(moodle);

            graphics.fill(x - 1, itemY - 1, x + 27, itemY + 27, 0xD9000000);
            graphics.fill(x, itemY, x + 25, itemY + 25, 0xE51A1D22);
            graphics.fill(x + 2, itemY + 2, x + 23, itemY + 23, accent);
            net.minecraft.resources.ResourceLocation icon =
                    net.minecraft.resources.ResourceLocation.tryParse(moodle.icon());
            boolean hasIcon = icon != null
                    && minecraft.getResourceManager().getResource(icon).isPresent();
            if (hasIcon) {
                graphics.blit(icon, x + 2, itemY + 2, 0.0F, 0.0F, 21, 21, 32, 32);
            } else {
                graphics.fill(x + 3, itemY + 3, x + 22, itemY + 22, 0xC915171A);
                graphics.drawString(font, symbol(moodle.id()), x + 7, itemY + 7, 0xFFF4F0E8, true);
            }

            double barValue = moodle.id().equals("aftermath:temperature")
                    ? Math.abs(moodle.value()) * (100.0D / 150.0D) : moodle.value();
            int progress = (int) Math.round(Math.max(0.0D, Math.min(100.0D, barValue)) / 100.0D * 19.0D);
            graphics.fill(x + 3, itemY + 21, x + 22, itemY + 23, 0xFF30343A);
            if (progress > 0) graphics.fill(x + 3, itemY + 21, x + 3 + progress, itemY + 23, accent);

            if (mouseX >= x - 1 && mouseX <= x + 27 && mouseY >= itemY - 1 && mouseY <= itemY + 27) {
                hovered = moodle;
                hoveredY = itemY;
            }
        }

        if (hovered != null) {
            Component title = Component.translatable(hovered.nameKey());
            Component level = Component.translatable(hovered.levelNameKey());
            Component description = Component.translatable(hovered.levelDescriptionKey());
            int width = Math.min(220, Math.max(font.width(title), Math.max(font.width(level), font.width(description))) + 12);
            int tooltipX = Math.max(4, x - width - 8);
            int tooltipY = Math.max(4, Math.min(
                    minecraft.getWindow().getGuiScaledHeight() - 42, hoveredY));
            graphics.fill(tooltipX, tooltipY, tooltipX + width, tooltipY + 39, 0xF0101216);
            graphics.fill(tooltipX, tooltipY, tooltipX + 2, tooltipY + 39, severityColor(hovered));
            graphics.drawString(font, title, tooltipX + 7, tooltipY + 5, 0xFFF2F2F2, true);
            graphics.drawString(font, level, tooltipX + 7, tooltipY + 16, severityColor(hovered), true);
            graphics.drawString(font, Component.literal(trim(description.getString(), 34)),
                    tooltipX + 7, tooltipY + 27, 0xFFBFC4C9, false);
        }
    }

    private static int severityColor(MoodleView moodle) {
        if (moodle.id().equals("aftermath:temperature") && moodle.value() < 0.0D) {
            if (moodle.severity() >= 3) return 0xFF3979B8;
            if (moodle.severity() == 2) return 0xFF4B91C5;
            return 0xFF76A9C9;
        }
        if (moodle.id().equals("aftermath:bleeding")) return 0xFFB7443F;
        if (moodle.severity() >= 3) return 0xFFB7443F;
        if (moodle.severity() == 2) return 0xFFD18A35;
        return 0xFFC0A64E;
    }

    private static String symbol(String id) {
        if (id.contains("tired")) return "Zz";
        if (id.contains("exertion")) return "E";
        if (id.contains("panic") || id.contains("stress")) return "!";
        if (id.contains("boredom")) return "…";
        if (id.contains("unhappiness")) return ":(";
        if (id.contains("temperature")) return "°";
        if (id.contains("bleeding")) return "+";
        return "?";
    }

    private static String trim(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value == null ? "" : value;
        return value.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private record MoodleView(
            String id,
            String nameKey,
            String descriptionKey,
            String levelId,
            String levelNameKey,
            String levelDescriptionKey,
            String icon,
            double value,
            int severity,
            Map<String, Double> effects) {}
}
