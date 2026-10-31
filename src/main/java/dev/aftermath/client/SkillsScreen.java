package dev.aftermath.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Compact Project-Zomboid-inspired skills dossier with category filtering and scrolling. */
public final class SkillsScreen extends Screen {
    private static final int PANEL = 0xF0141712;
    private static final int PANEL_ALT = 0xF020231D;
    private static final int ROW = 0xE51D211A;
    private static final int ROW_HOVER = 0xEC30372A;
    private static final int ROW_SELECTED = 0xED4C3B28;
    private static final int BORDER = 0xFF655640;
    private static final int RUST = 0xFFD07843;
    private static final int PAPER = 0xFFE3DCC3;
    private static final int MUTED = 0xFF9C9A84;
    private static final int GREEN = 0xFF98B38A;
    private static final int BAR_BG = 0xFF35382D;
    private static final int ROW_HEIGHT_WIDE = 54;
    private static final int ROW_HEIGHT_NARROW = 72;

    private final List<SkillEntry> skills;
    private final boolean invalidSnapshot;
    private String activeCategory = "all";
    private String selectedId;
    private int scrollOffset;
    private int panelX;
    private int panelY;
    private int panelWidth;
    private int panelHeight;
    private int listX;
    private int listY;
    private int listWidth;
    private int listHeight;
    private int rowHeight;
    private int visibleRows;
    private int detailX;
    private int detailWidth;
    private boolean showDetails;

    private SkillsScreen(List<SkillEntry> skills, boolean invalidSnapshot) {
        super(Component.translatable("screen.aftermath.skills.title"));
        this.skills = List.copyOf(skills);
        this.invalidSnapshot = invalidSnapshot;
        this.selectedId = skills.isEmpty() ? null : skills.get(0).id();
    }

    public static SkillsScreen fromSnapshot(String snapshotJson) {
        try {
            JsonObject root = JsonParser.parseString(snapshotJson).getAsJsonObject();
            JsonArray array = root.getAsJsonArray("skills");
            if (array == null || array.size() > 64) return new SkillsScreen(List.of(), true);

            List<SkillEntry> parsed = new ArrayList<>();
            for (JsonElement element : array) {
                if (!element.isJsonObject()) return new SkillsScreen(List.of(), true);
                JsonObject json = element.getAsJsonObject();
                String id = requiredString(json, "id");
                String name = requiredString(json, "name");
                String description = requiredString(json, "description");
                String category = requiredString(json, "category");
                int level = boundedInt(json, "level", 0, 10);
                int maxLevel = boundedInt(json, "max_level", 1, 10);
                int experience = boundedInt(json, "experience", 0, 2_000_000_000);
                int xpToNext = boundedInt(json, "xp_to_next", 0, 2_000_000_000);
                double progress = json.get("progress").getAsDouble();
                if (!Double.isFinite(progress)) return new SkillsScreen(List.of(), true);
                parsed.add(new SkillEntry(id, name, description, category, level, maxLevel,
                        experience, xpToNext, Math.max(0.0D, Math.min(1.0D, progress))));
            }
            parsed.sort(Comparator.comparing(SkillEntry::category).thenComparing(SkillEntry::id));
            return new SkillsScreen(parsed, false);
        } catch (RuntimeException exception) {
            return new SkillsScreen(List.of(), true);
        }
    }

    private static String requiredString(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()
                || !json.getAsJsonPrimitive(key).isString()) {
            throw new IllegalArgumentException("Missing " + key);
        }
        String value = json.get(key).getAsString();
        if (value.isBlank() || value.length() > 128) throw new IllegalArgumentException("Invalid " + key);
        return value;
    }

    private static int boundedInt(JsonObject json, String key, int min, int max) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive()
                || !json.getAsJsonPrimitive(key).isNumber()) {
            throw new IllegalArgumentException("Missing number " + key);
        }
        int value = json.get(key).getAsInt();
        if (value < min || value > max) throw new IllegalArgumentException("Out of range " + key);
        return value;
    }

    private List<String> categories() {
        return skills.stream().map(SkillEntry::category).distinct().sorted().toList();
    }

    private List<SkillEntry> visibleSkills() {
        if (activeCategory.equals("all")) return skills;
        return skills.stream().filter(skill -> skill.category().equals(activeCategory)).toList();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xB8000000);
        calculateLayout();

        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 2, RUST);
        graphics.fill(panelX, panelY, panelX + 1, panelY + panelHeight, BORDER);
        graphics.fill(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight, BORDER);
        graphics.fill(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, BORDER);

        drawHeader(graphics);
        drawCategoryTabs(graphics, mouseX, mouseY);
        drawSkillList(graphics, mouseX, mouseY);
        if (showDetails) drawSelectedDetails(graphics);
        drawFooter(graphics);
    }

    private void calculateLayout() {
        panelWidth = Math.max(260, Math.min(900, width - 20));
        panelHeight = Math.max(170, Math.min(560, height - 20));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        showDetails = panelWidth >= 610;
        int contentX = panelX + 12;
        int contentWidth = panelWidth - 24;
        listX = contentX;
        listY = panelY + 78;
        listHeight = Math.max(64, panelHeight - 112);
        rowHeight = showDetails ? ROW_HEIGHT_WIDE : ROW_HEIGHT_NARROW;
        if (showDetails) {
            listWidth = Math.max(280, (int) (contentWidth * 0.57D));
            detailX = listX + listWidth + 10;
            detailWidth = contentWidth - listWidth - 10;
        } else {
            listWidth = contentWidth;
            detailX = 0;
            detailWidth = 0;
        }
        visibleRows = Math.max(1, (listHeight - 6) / rowHeight);
        int maxScroll = Math.max(0, visibleSkills().size() - visibleRows);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    private void drawHeader(GuiGraphics graphics) {
        graphics.drawString(font, Component.translatable("screen.aftermath.skills.title"),
                panelX + 14, panelY + 10, PAPER);
        graphics.drawString(font, Component.translatable("screen.aftermath.skills.subtitle"),
                panelX + 14, panelY + 27, MUTED);
        graphics.drawString(font, Component.literal(skills.size() + " / 64"),
                panelX + panelWidth - 14 - font.width(skills.size() + " / 64"),
                panelY + 14, RUST);
        graphics.fill(panelX + 12, panelY + 47, panelX + panelWidth - 12, panelY + 48, BORDER);
    }

    private void drawCategoryTabs(GuiGraphics graphics, int mouseX, int mouseY) {
        List<String> tabs = new ArrayList<>();
        tabs.add("all");
        tabs.addAll(categories());
        int tabY = panelY + 54;
        int gap = 4;
        int available = panelWidth - 24 - gap * (tabs.size() - 1);
        int tabWidth = Math.max(28, available / Math.max(1, tabs.size()));
        int x = panelX + 12;
        for (String category : tabs) {
            int right = x + tabWidth;
            boolean selected = activeCategory.equals(category);
            boolean hovered = mouseX >= x && mouseX <= right && mouseY >= tabY && mouseY <= tabY + 18;
            graphics.fill(x, tabY, right, tabY + 18,
                    selected ? ROW_SELECTED : hovered ? ROW_HOVER : PANEL_ALT);
            graphics.fill(x, tabY, right, tabY + 1, selected ? RUST : BORDER);
            String suffix = category.equals("all") ? "all" : category;
            String translation = "screen.aftermath.skills.category." + suffix
                    + (panelWidth < 700 ? ".short" : "");
            Component label = Component.translatable(translation);
            graphics.drawCenteredString(font, label, x + tabWidth / 2, tabY + 5, selected ? PAPER : MUTED);
            x = right + gap;
        }
    }

    private void drawSkillList(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(listX, listY, listX + listWidth, listY + listHeight, PANEL_ALT);
        graphics.fill(listX, listY, listX + listWidth, listY + 1, BORDER);
        graphics.drawString(font, Component.translatable("screen.aftermath.skills.list"),
                listX + 7, listY + 6, MUTED);

        List<SkillEntry> visible = visibleSkills();
        int contentY = listY + 22;
        if (visible.isEmpty()) {
            Component message = Component.translatable(invalidSnapshot
                    ? "screen.aftermath.skills.data_error" : "screen.aftermath.skills.empty");
            for (FormattedCharSequence line : font.split(message, Math.max(20, listWidth - 20))) {
                graphics.drawString(font, line, listX + 9, contentY, MUTED);
                contentY += 10;
            }
            return;
        }

        for (int row = 0; row < visibleRows; row++) {
            int index = scrollOffset + row;
            if (index >= visible.size()) break;
            SkillEntry entry = visible.get(index);
            int y = contentY + row * rowHeight;
            boolean selected = entry.id().equals(selectedId);
            boolean hovered = mouseX >= listX && mouseX <= listX + listWidth
                    && mouseY >= y && mouseY < y + rowHeight - 3;
            graphics.fill(listX + 4, y, listX + listWidth - 4, y + rowHeight - 3,
                    selected ? ROW_SELECTED : hovered ? ROW_HOVER : ROW);
            if (selected) graphics.fill(listX + 4, y, listX + 6, y + rowHeight - 3, RUST);

            Component name = Component.translatable(entry.nameKey());
            Component level = Component.translatable("screen.aftermath.skills.level",
                    entry.level(), entry.maxLevel());
            int levelWidth = font.width(level);
            graphics.drawString(font, name, listX + 11, y + 5, selected ? PAPER : 0xFFD1CBB7);
            graphics.drawString(font, level, listX + listWidth - 12 - levelWidth, y + 5, GREEN);

            Component xp = Component.translatable("screen.aftermath.skills.xp", entry.experience());
            graphics.drawString(font, xp, listX + 11, y + 19, MUTED);
            int barX = listX + 11;
            int barWidth = Math.max(36, listWidth - 22);
            int barY = y + (showDetails ? 37 : 35);
            graphics.fill(barX, barY, barX + barWidth, barY + 5, BAR_BG);
            graphics.fill(barX, barY, barX + (int) (barWidth * entry.progress()), barY + 5, entry.level() >= entry.maxLevel() ? RUST : GREEN);

            if (!showDetails) {
                Component description = Component.translatable(entry.descriptionKey());
                List<FormattedCharSequence> wrapped = font.split(description, Math.max(20, listWidth - 22));
                int descY = y + 44;
                for (int line = 0; line < Math.min(2, wrapped.size()); line++) {
                    graphics.drawString(font, wrapped.get(line), listX + 11, descY + line * 9, MUTED);
                }
            }
        }

        if (visible.size() > visibleRows) {
            int trackX = listX + listWidth - 3;
            int trackY = contentY;
            int trackHeight = Math.max(10, visibleRows * rowHeight - 6);
            graphics.fill(trackX, trackY, trackX + 2, trackY + trackHeight, BAR_BG);
            int thumbHeight = Math.max(12, trackHeight * visibleRows / visible.size());
            int maxScroll = Math.max(1, visible.size() - visibleRows);
            int thumbY = trackY + (trackHeight - thumbHeight) * scrollOffset / maxScroll;
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, RUST);
        }
    }

    private void drawSelectedDetails(GuiGraphics graphics) {
        graphics.fill(detailX, listY, detailX + detailWidth, listY + listHeight, PANEL_ALT);
        graphics.fill(detailX, listY, detailX + detailWidth, listY + 1, BORDER);
        SkillEntry selected = visibleSkills().stream()
                .filter(skill -> skill.id().equals(selectedId)).findFirst()
                .orElseGet(() -> visibleSkills().isEmpty() ? null : visibleSkills().get(0));
        if (selected == null) return;

        int x = detailX + 12;
        int y = listY + 12;
        int textWidth = Math.max(30, detailWidth - 24);
        for (FormattedCharSequence line : font.split(Component.translatable(selected.nameKey()), textWidth)) {
            graphics.drawString(font, line, x, y, PAPER);
            y += 11;
        }
        y += 5;
        graphics.drawString(font, Component.translatable("screen.aftermath.skills.level",
                selected.level(), selected.maxLevel()), x, y, GREEN);
        y += 18;
        graphics.drawString(font, Component.translatable("screen.aftermath.skills.xp",
                selected.experience()), x, y, PAPER);
        y += 12;

        Component next = selected.level() >= selected.maxLevel()
                ? Component.translatable("screen.aftermath.skills.max_level")
                : Component.translatable("screen.aftermath.skills.to_next", selected.xpToNext());
        graphics.drawString(font, next, x, y, selected.level() >= selected.maxLevel() ? RUST : MUTED);
        y += 20;

        graphics.drawString(font, Component.translatable("screen.aftermath.skills.progress"), x, y, MUTED);
        y += 10;
        int barWidth = textWidth;
        graphics.fill(x, y, x + barWidth, y + 8, BAR_BG);
        graphics.fill(x, y, x + (int) (barWidth * selected.progress()), y + 8,
                selected.level() >= selected.maxLevel() ? RUST : GREEN);
        y += 22;

        graphics.drawString(font, Component.translatable("screen.aftermath.skills.description"), x, y, RUST);
        y += 13;
        for (FormattedCharSequence line : font.split(
                Component.translatable(selected.descriptionKey()), textWidth)) {
            if (y > listY + listHeight - 12) break;
            graphics.drawString(font, line, x, y, 0xFFC7C4B4);
            y += 11;
        }
    }

    private void drawFooter(GuiGraphics graphics) {
        graphics.drawString(font, Component.translatable("screen.aftermath.skills.footer"),
                panelX + 12, panelY + panelHeight - 17, MUTED);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        calculateLayout();

        List<String> tabs = new ArrayList<>();
        tabs.add("all");
        tabs.addAll(categories());
        int tabY = panelY + 54;
        int gap = 4;
        int available = panelWidth - 24 - gap * (tabs.size() - 1);
        int tabWidth = Math.max(28, available / Math.max(1, tabs.size()));
        int x = panelX + 12;
        for (String category : tabs) {
            if (mouseX >= x && mouseX <= x + tabWidth && mouseY >= tabY && mouseY <= tabY + 18) {
                activeCategory = category;
                scrollOffset = 0;
                List<SkillEntry> visible = visibleSkills();
                selectedId = visible.isEmpty() ? null : visible.get(0).id();
                return true;
            }
            x += tabWidth + gap;
        }

        int contentY = listY + 22;
        if (mouseX >= listX && mouseX <= listX + listWidth
                && mouseY >= contentY && mouseY < listY + listHeight) {
            int row = (int) ((mouseY - contentY) / rowHeight);
            int index = scrollOffset + row;
            List<SkillEntry> visible = visibleSkills();
            if (index >= 0 && index < visible.size()) {
                selectedId = visible.get(index).id();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        calculateLayout();
        if (mouseX >= listX && mouseX <= listX + listWidth
                && mouseY >= listY && mouseY <= listY + listHeight) {
            int maxScroll = Math.max(0, visibleSkills().size() - visibleRows);
            int delta = (int) Math.signum(scrollY);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - delta * 2));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record SkillEntry(String id, String nameKey, String descriptionKey, String category,
                              int level, int maxLevel, int experience, int xpToNext, double progress) {}
}
