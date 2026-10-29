package dev.aftermath.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aftermath.network.SelectCharacterPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sixik.sdmuilibrary.client.screen.BaseScreen;
import net.sixik.sdmuilibrary.client.utils.math.Vector2;
import net.sixik.sdmuilibrary.client.widgets.RenderWidget;
import net.sixik.sdmuilibrary.client.widgets.buttons.SimpleButtonWidget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * First-join character creation built on SDM UI Lib widgets.
 *
 * The screen uses SDM's BaseScreen, RenderWidget containers and SimpleButtonWidget controls.
 * Character validation and persistence remain server-authoritative.
 */
public final class CharacterSelectionScreen extends BaseScreen {
    private static final int SHELL = 0xF0181B1B;
    private static final int PANEL = 0xF0222625;
    private static final int PANEL_ALT = 0xF02A2F2D;
    private static final int ROW = 0xFF262B29;
    private static final int ROW_HOVER = 0xFF353B37;
    private static final int ROW_SELECTED = 0xFF44382A;
    private static final int BORDER = 0xFF4A514B;
    private static final int ACCENT = 0xFFE0A15D;
    private static final int TEXT = 0xFFF0EEE7;
    private static final int MUTED = 0xFFB0B5AC;
    private static final int GREEN = 0xFF9DD5AF;
    private static final int RED = 0xFFE58C7F;
    private static final int ROW_HEIGHT = 21;
    private static final int ROW_STEP = 23;
    private static final int GAP = 8;
    private static final int PADDING = 8;

    private final List<ProfessionOption> professions = new ArrayList<>();
    private final List<TraitOption> traits = new ArrayList<>();
    private final Set<String> selectedTraits = new LinkedHashSet<>();
    private final Map<String, EntryButton> professionButtons = new LinkedHashMap<>();
    private final Map<String, EntryButton> traitButtons = new LinkedHashMap<>();

    private String selectedProfessionId = "aftermath:medic";
    private String resultMessageKey = "";
    private boolean waitingForServer;
    private boolean invalidDefinitions;

    private RenderWidget professionList;
    private RenderWidget traitList;
    private EntryButton confirmButton;

    private int shellX;
    private int shellY;
    private int shellW;
    private int shellH;
    private int headerY;
    private int headerH;
    private int footerY;
    private int footerH;
    private int bodyY;
    private int bodyH;

    private int professionPanelX;
    private int professionPanelW;
    private int professionRowsX;
    private int professionRowsY;
    private int professionRowsW;
    private int professionRowsH;

    private int traitPanelX;
    private int traitPanelY;
    private int traitPanelW;
    private int traitPanelH;
    private int traitRowsX;
    private int traitRowsY;
    private int traitRowsW;
    private int traitRowsH;

    private int summaryPanelX;
    private int summaryPanelY;
    private int summaryPanelW;
    private int summaryPanelH;

    private boolean compactLayout;
    private int professionScrollOffset;
    private int traitScrollOffset;

    public CharacterSelectionScreen(String definitionsJson) {
        super();
        readDefinitions(definitionsJson);
        professions.sort(Comparator.comparing(option -> tr(option.name)));
        traits.sort(Comparator.comparingInt(CharacterSelectionScreen::categoryOrder)
                .thenComparing(option -> tr(option.name)));
        if (professions.stream().noneMatch(option -> option.id.equals(selectedProfessionId)) && !professions.isEmpty()) {
            selectedProfessionId = professions.get(0).id;
        }
    }

    private void readDefinitions(String definitionsJson) {
        try {
            JsonObject root = JsonParser.parseString(definitionsJson).getAsJsonObject();
            JsonArray professionArray = root.getAsJsonArray("professions");
            if (professionArray != null) {
                for (JsonElement element : professionArray) {
                    JsonObject json = element.getAsJsonObject();
                    professions.add(new ProfessionOption(
                            string(json, "id", ""),
                            string(json, "name", ""),
                            string(json, "description", ""),
                            string(json, "icon", "minecraft:player_head"),
                            integer(json, "starting_points", 8),
                            stringSet(json, "allowed_traits"),
                            stringSet(json, "granted_traits"),
                            integerMap(json, "skill_bonuses"),
                            startingItems(json)));
                }
            }

            JsonArray traitArray = root.getAsJsonArray("traits");
            if (traitArray != null) {
                for (JsonElement element : traitArray) {
                    JsonObject json = element.getAsJsonObject();
                    traits.add(new TraitOption(
                            string(json, "id", ""),
                            string(json, "name", ""),
                            string(json, "description", ""),
                            string(json, "category", "neutral"),
                            integer(json, "point_cost", 0),
                            integer(json, "point_credit", 0),
                            stringSet(json, "conflicts"),
                            string(json, "requires_profession", "")));
                }
            }
            if (professions.isEmpty() || traits.isEmpty()) {
                invalidDefinitions = true;
                resultMessageKey = "screen.aftermath.character.error.invalid_data";
            }
        } catch (RuntimeException exception) {
            professions.clear();
            traits.clear();
            invalidDefinitions = true;
            resultMessageKey = "screen.aftermath.character.error.invalid_data";
        }
    }

    @Override
    protected void rebuildProperty() {
        super.rebuildProperty();

        int screenW = Math.max(1, size.x);
        int screenH = Math.max(1, size.y);
        int margin = screenW < 520 ? 5 : 10;

        shellX = margin;
        shellY = margin;
        shellW = Math.max(1, screenW - margin * 2);
        shellH = Math.max(1, screenH - margin * 2);

        headerY = shellY + 7;
        headerH = screenH < 300 ? 32 : 39;
        footerH = screenH < 300 ? 39 : 47;
        footerY = shellY + shellH - footerH - 6;
        bodyY = headerY + headerH + 7;
        bodyH = Math.max(40, footerY - bodyY - 7);

        int innerX = shellX + PADDING;
        int innerW = Math.max(1, shellW - PADDING * 2);
        compactLayout = screenW < 720;

        if (!compactLayout) {
            int panelGap = GAP;
            int columnsWidth = Math.max(1, innerW - panelGap * 2);
            professionPanelW = clamp(columnsWidth * 26 / 100, 145, 205);
            summaryPanelW = clamp(columnsWidth * 29 / 100, 175, 245);
            traitPanelW = columnsWidth - professionPanelW - summaryPanelW;
            if (traitPanelW < 150) {
                professionPanelW = Math.max(120, columnsWidth / 3);
                summaryPanelW = Math.max(145, columnsWidth / 3);
                traitPanelW = Math.max(90, columnsWidth - professionPanelW - summaryPanelW);
            }

            professionPanelX = innerX;
            traitPanelX = professionPanelX + professionPanelW + panelGap;
            traitPanelY = bodyY;
            traitPanelH = bodyH;
            summaryPanelX = traitPanelX + traitPanelW + panelGap;
            summaryPanelY = bodyY;
            summaryPanelH = bodyH;
        } else {
            professionPanelW = clamp(innerW * 34 / 100, 100, 168);
            int rightW = Math.max(90, innerW - professionPanelW - GAP);
            professionPanelX = innerX;
            traitPanelX = professionPanelX + professionPanelW + GAP;
            traitPanelW = rightW;
            traitPanelY = bodyY;
            traitPanelH = Math.max(42, (bodyH - GAP) * 62 / 100);
            summaryPanelX = traitPanelX;
            summaryPanelY = traitPanelY + traitPanelH + GAP;
            summaryPanelW = rightW;
            summaryPanelH = Math.max(38, bodyH - traitPanelH - GAP);
        }

        professionRowsX = professionPanelX + 5;
        professionRowsY = bodyY + 25;
        professionRowsW = Math.max(1, professionPanelW - 13);
        professionRowsH = Math.max(1, bodyH - 31);

        traitRowsX = traitPanelX + 5;
        traitRowsY = traitPanelY + 25;
        traitRowsW = Math.max(1, traitPanelW - 13);
        traitRowsH = Math.max(1, traitPanelH - 31);
    }

    @Override
    protected void addWidgets() {
        professionList = addRenderableWidget(new RenderWidget(
                Vector2.of(professionRowsX, professionRowsY),
                Vector2.of(professionRowsW, professionRowsH)));
        traitList = addRenderableWidget(new RenderWidget(
                Vector2.of(traitRowsX, traitRowsY),
                Vector2.of(traitRowsW, traitRowsH)));

        professionButtons.clear();
        for (ProfessionOption option : professions) {
            EntryButton button = professionList.addRenderableWidget(new EntryButton(
                    Vector2.of(professionRowsX, professionRowsY),
                    Vector2.of(professionRowsW, ROW_HEIGHT),
                    () -> professionLabel(option),
                    () -> option.id.equals(selectedProfessionId),
                    () -> false,
                    () -> !waitingForServer,
                    () -> TEXT,
                    false,
                    () -> selectProfession(option.id)));
            professionButtons.put(option.id, button);
        }

        traitButtons.clear();
        for (TraitOption option : traits) {
            EntryButton button = traitList.addRenderableWidget(new EntryButton(
                    Vector2.of(traitRowsX, traitRowsY),
                    Vector2.of(traitRowsW, ROW_HEIGHT),
                    () -> traitLabel(option),
                    () -> selectedTraits.contains(option.id) || (profession() != null && profession().grantedTraits.contains(option.id)),
                    () -> profession() != null && profession().grantedTraits.contains(option.id),
                    () -> !waitingForServer && (profession() == null || !profession().grantedTraits.contains(option.id)),
                    () -> traitTextColor(option),
                    false,
                    () -> toggleTrait(option)));
            traitButtons.put(option.id, button);
        }

        int buttonW = Math.min(190, Math.max(112, shellW / 3));
        int buttonH = screenHeight() < 300 ? 24 : 29;
        confirmButton = addRenderableWidget(new EntryButton(
                Vector2.of(shellX + shellW - buttonW - 11, footerY + Math.max(2, (footerH - buttonH) / 2)),
                Vector2.of(buttonW, buttonH),
                () -> tr(waitingForServer ? "screen.aftermath.character.saving" : "screen.aftermath.character.confirm"),
                () -> false,
                () -> false,
                this::canSubmit,
                () -> 0xFF171A18,
                true,
                this::submit));

        refreshUi();
    }

    private void refreshUi() {
        layoutProfessionRows();
        layoutTraitRows();
        if (confirmButton != null) {
            confirmButton.setPosition(shellX + shellW - confirmButton.getWidth() - 11,
                    footerY + Math.max(2, (footerH - confirmButton.getHeight()) / 2));
            confirmButton.active = canSubmit();
        }
    }

    private void layoutProfessionRows() {
        if (professionList == null) return;
        professionList.setPosition(professionRowsX, professionRowsY);
        professionList.setWidth(professionRowsW);
        professionList.setHeight(professionRowsH);

        int visibleCount = visibleRows(professionRowsH);
        professionScrollOffset = clamp(professionScrollOffset, 0, Math.max(0, professions.size() - visibleCount));
        for (int i = 0; i < professions.size(); i++) {
            ProfessionOption option = professions.get(i);
            EntryButton button = professionButtons.get(option.id);
            if (button == null) continue;
            boolean visible = i >= professionScrollOffset && i < professionScrollOffset + visibleCount;
            button.visible = visible;
            button.active = visible && !waitingForServer;
            if (visible) {
                int row = i - professionScrollOffset;
                button.setPosition(professionRowsX, professionRowsY + row * ROW_STEP);
                button.setWidth(professionRowsW);
                button.setHeight(ROW_HEIGHT);
            } else {
                button.setPosition(professionRowsX, professionRowsY - 500);
            }
        }
    }

    private void layoutTraitRows() {
        if (traitList == null) return;
        traitList.setPosition(traitRowsX, traitRowsY);
        traitList.setWidth(traitRowsW);
        traitList.setHeight(traitRowsH);

        List<TraitOption> available = visibleTraits();
        int visibleCount = visibleRows(traitRowsH);
        traitScrollOffset = clamp(traitScrollOffset, 0, Math.max(0, available.size() - visibleCount));

        for (TraitOption option : traits) {
            EntryButton button = traitButtons.get(option.id);
            if (button == null) continue;
            int index = available.indexOf(option);
            boolean visible = index >= traitScrollOffset && index < traitScrollOffset + visibleCount;
            button.visible = visible;
            button.active = visible && !waitingForServer
                    && (profession() == null || !profession().grantedTraits.contains(option.id));
            if (visible) {
                int row = index - traitScrollOffset;
                button.setPosition(traitRowsX, traitRowsY + row * ROW_STEP);
                button.setWidth(traitRowsW);
                button.setHeight(ROW_HEIGHT);
            } else {
                button.setPosition(traitRowsX, traitRowsY - 500);
            }
        }
    }

    private int visibleRows(int listHeight) {
        return Math.max(1, (listHeight - 2) / ROW_STEP);
    }

    private List<TraitOption> visibleTraits() {
        ProfessionOption selected = profession();
        if (selected == null) return List.of();
        return traits.stream()
                .filter(trait -> selected.allowedTraits.isEmpty() || selected.allowedTraits.contains(trait.id))
                .filter(trait -> trait.requiresProfession.isBlank() || trait.requiresProfession.equals(selected.id))
                .sorted(Comparator.comparingInt(CharacterSelectionScreen::categoryOrder)
                        .thenComparing(option -> tr(option.name)))
                .toList();
    }

    private void selectProfession(String professionId) {
        if (waitingForServer || professionId.equals(selectedProfessionId)) return;
        selectedProfessionId = professionId;
        ProfessionOption selected = profession();
        selectedTraits.removeIf(id -> {
            TraitOption trait = traitById(id);
            return trait == null
                    || selected == null
                    || selected.grantedTraits.contains(id)
                    || (!selected.allowedTraits.isEmpty() && !selected.allowedTraits.contains(id))
                    || (!trait.requiresProfession.isBlank() && !trait.requiresProfession.equals(selected.id));
        });
        professionScrollOffset = professions.indexOf(selected);
        traitScrollOffset = 0;
        resultMessageKey = "";
        refreshUi();
    }

    private void toggleTrait(TraitOption option) {
        if (waitingForServer || profession() == null || profession().grantedTraits.contains(option.id)) return;
        if (selectedTraits.remove(option.id)) {
            resultMessageKey = "";
            refreshUi();
            return;
        }

        Set<String> activeIds = new LinkedHashSet<>(selectedTraits);
        activeIds.addAll(profession().grantedTraits);
        boolean conflict = !Collections.disjoint(option.conflicts, activeIds)
                || activeIds.stream().map(this::traitById).filter(candidate -> candidate != null)
                .anyMatch(candidate -> candidate.conflicts.contains(option.id));
        if (conflict) {
            resultMessageKey = "screen.aftermath.character.error.conflicting_traits";
            refreshUi();
            return;
        }

        selectedTraits.add(option.id);
        resultMessageKey = "";
        refreshUi();
    }

    private void submit() {
        if (!canSubmit()) return;
        waitingForServer = true;
        resultMessageKey = "";
        refreshUi();
        String traitsCsv = selectedTraits.stream().sorted().reduce((a, b) -> a + "," + b).orElse("");
        PacketDistributor.sendToServer(new SelectCharacterPayload(selectedProfessionId, traitsCsv));
    }

    private boolean canSubmit() {
        return !waitingForServer && !invalidDefinitions && profession() != null && remainingPoints() >= 0;
    }

    private int remainingPoints() {
        ProfessionOption selected = profession();
        if (selected == null) return Integer.MIN_VALUE;
        int remaining = selected.startingPoints;
        for (String id : selectedTraits) {
            if (selected.grantedTraits.contains(id)) continue;
            TraitOption trait = traitById(id);
            if (trait == null) continue;
            remaining += trait.category.equals("positive") ? -trait.pointCost : trait.pointCredit;
        }
        return remaining;
    }

    private ProfessionOption profession() {
        return professions.stream().filter(option -> option.id.equals(selectedProfessionId)).findFirst().orElse(null);
    }

    private TraitOption traitById(String id) {
        return traits.stream().filter(option -> option.id.equals(id)).findFirst().orElse(null);
    }

    private String professionLabel(ProfessionOption option) {
        return (option.id.equals(selectedProfessionId) ? "✓ " : "  ")
                + fit(tr(option.name), professionRowsW - 14);
    }

    private String traitLabel(TraitOption option) {
        ProfessionOption selected = profession();
        boolean granted = selected != null && selected.grantedTraits.contains(option.id);
        boolean chosen = selectedTraits.contains(option.id) || granted;
        int delta = option.category.equals("positive") ? -option.pointCost
                : option.category.equals("negative") ? option.pointCredit : 0;
        String points = delta > 0 ? "+" + delta : Integer.toString(delta);
        String marker = granted ? "★ " : chosen ? "✓ " : categoryMarker(option.category) + " ";
        return marker + fit(tr(option.name), traitRowsW - 58) + "  [" + points + "]";
    }

    private static String categoryMarker(String category) {
        return switch (category.toLowerCase()) {
            case "positive" -> "+";
            case "negative" -> "−";
            default -> "·";
        };
    }

    private static int categoryOrder(TraitOption option) {
        return switch (option.category.toLowerCase()) {
            case "positive" -> 0;
            case "negative" -> 1;
            default -> 2;
        };
    }

    private static int traitTextColor(TraitOption option) {
        return switch (option.category.toLowerCase()) {
            case "positive" -> 0xFFBDDEB9;
            case "negative" -> 0xFFE3A39A;
            default -> TEXT;
        };
    }

    private void drawPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x, y, x + w, y + h, PANEL);
        graphics.fill(x, y, x + w, y + 1, BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, BORDER);
        graphics.fill(x, y, x + 1, y + h, BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, BORDER);
        graphics.fill(x + 1, y + 1, x + w - 1, y + 3, 0xFF353B36);
    }

    @Override
    public void draw(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.fill(shellX, shellY, shellX + shellW, shellY + shellH, SHELL);
        graphics.fill(shellX, shellY, shellX + shellW, shellY + 2, ACCENT);
        graphics.fill(shellX, shellY + shellH - 1, shellX + shellW, shellY + shellH, BORDER);

        int headerX = shellX + PADDING;
        graphics.drawString(Minecraft.getInstance().font,
                tr("screen.aftermath.character.ui.kicker"), headerX, headerY + 1, ACCENT);
        graphics.drawString(Minecraft.getInstance().font,
                tr("screen.aftermath.character.title"), headerX, headerY + 13, TEXT);
        if (!compactLayout) {
            drawRightAligned(graphics, tr("screen.aftermath.character.subtitle"),
                    shellX + shellW - PADDING, headerY + 15, MUTED);
        }

        drawPanel(graphics, professionPanelX, bodyY, professionPanelW, bodyH);
        graphics.drawString(Minecraft.getInstance().font,
                tr("screen.aftermath.character.profession"), professionPanelX + 8, bodyY + 8, ACCENT);
        if (professions.isEmpty()) {
            graphics.drawString(Minecraft.getInstance().font,
                    tr("screen.aftermath.character.ui.empty_professions"),
                    professionPanelX + 8, professionRowsY + 5, RED);
        }
        drawScrollbar(graphics, professionPanelX + professionPanelW - 7, professionRowsY,
                professionRowsH, professions.size(), visibleRows(professionRowsH), professionScrollOffset);

        drawPanel(graphics, traitPanelX, traitPanelY, traitPanelW, traitPanelH);
        graphics.drawString(Minecraft.getInstance().font,
                tr("screen.aftermath.character.traits"), traitPanelX + 8, traitPanelY + 8, ACCENT);
        if (visibleTraits().isEmpty()) {
            graphics.drawString(Minecraft.getInstance().font,
                    tr("screen.aftermath.character.ui.empty_traits"),
                    traitPanelX + 8, traitRowsY + 5, MUTED);
        }
        drawScrollbar(graphics, traitPanelX + traitPanelW - 7, traitRowsY,
                traitRowsH, visibleTraits().size(), visibleRows(traitRowsH), traitScrollOffset);

        drawPanel(graphics, summaryPanelX, summaryPanelY, summaryPanelW, summaryPanelH);
        graphics.drawString(Minecraft.getInstance().font,
                tr("screen.aftermath.character.ui.summary"), summaryPanelX + 8, summaryPanelY + 8, ACCENT);
        drawSummary(graphics);

        int footerX = shellX + PADDING;
        int freeTextW = Math.max(10, confirmButton == null ? shellW - 30 : confirmButton.getX() - footerX - 10);
        int points = remainingPoints();
        String pointText = tr("screen.aftermath.character.points",
                profession() == null ? 0 : points,
                profession() == null ? 0 : profession().startingPoints);
        drawTruncated(graphics, pointText, footerX, footerY + 7, freeTextW, points < 0 ? RED : GREEN);

        String statusKey = !resultMessageKey.isBlank()
                ? resultMessageKey
                : "screen.aftermath.character.ui.footer_hint";
        int statusColor = resultMessageKey.contains(".error.") || invalidDefinitions ? RED : MUTED;
        drawTruncated(graphics, tr(statusKey), footerX, footerY + 23, freeTextW, statusColor);
    }

    private void drawSummary(GuiGraphics graphics) {
        List<String> lines = new ArrayList<>();
        ProfessionOption selected = profession();
        if (selected == null) {
            lines.add(tr("screen.aftermath.character.error.invalid_profession"));
        } else {
            lines.add(tr(selected.name));
            appendWrapped(lines, tr(selected.description), Math.max(50, summaryPanelW - 16));
            lines.add(tr("screen.aftermath.character.starting_skills"));
            if (selected.skillBonuses.isEmpty()) {
                lines.add(tr("screen.aftermath.character.starting_skills_none"));
            } else {
                selected.skillBonuses.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry ->
                        appendWrapped(lines,
                                tr("skill.aftermath." + entry.getKey()) + tr("screen.aftermath.character.skill_level", entry.getValue()),
                                Math.max(50, summaryPanelW - 16)));
            }

            lines.add(tr("screen.aftermath.character.ui.selected_traits"));
            Set<String> shownTraits = new LinkedHashSet<>();
            for (String id : selected.grantedTraits) {
                TraitOption trait = traitById(id);
                if (trait != null) {
                    lines.add("★ " + tr(trait.name));
                    shownTraits.add(id);
                }
            }
            for (String id : selectedTraits) {
                if (shownTraits.contains(id)) continue;
                TraitOption trait = traitById(id);
                if (trait != null) {
                    appendWrapped(lines, "✓ " + tr(trait.name), Math.max(50, summaryPanelW - 16));
                    shownTraits.add(id);
                }
            }
            if (shownTraits.isEmpty()) lines.add(tr("screen.aftermath.character.ui.no_traits_selected"));

            lines.add(tr("screen.aftermath.character.ui.starting_items"));
            if (selected.startingItems.isEmpty()) {
                lines.add(tr("screen.aftermath.character.ui.no_items"));
            } else {
                for (StartingItem item : selected.startingItems) {
                    appendWrapped(lines, item.count + " × " + itemName(item.itemId), Math.max(50, summaryPanelW - 16));
                }
            }
        }

        int maxLines = Math.max(1, (summaryPanelH - 30) / 10);
        int y = summaryPanelY + 25;
        for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
            String line = lines.get(i);
            int color = (line.startsWith("★") || line.startsWith("✓")) ? GREEN : (i == 0 ? TEXT : MUTED);
            drawTruncated(graphics, line, summaryPanelX + 8, y, summaryPanelW - 16, color);
            y += 10;
        }
    }

    private void drawScrollbar(GuiGraphics graphics, int x, int y, int h, int total, int visible, int offset) {
        if (total <= visible || h <= 6) return;
        graphics.fill(x, y, x + 3, y + h, 0xFF151817);
        int thumbH = Math.max(12, h * visible / Math.max(1, total));
        int thumbY = y + (h - thumbH) * offset / Math.max(1, total - visible);
        graphics.fill(x, thumbY, x + 3, thumbY + thumbH, ACCENT);
    }

    private void appendWrapped(List<String> lines, String value, int maxWidth) {
        if (value == null || value.isBlank()) return;
        StringBuilder current = new StringBuilder();
        for (String word : value.split("\\s+")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (Minecraft.getInstance().font.width(candidate) > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current.setLength(0);
                current.append(word);
            } else {
                if (!current.isEmpty()) current.append(' ');
                current.append(word);
            }
        }
        if (!current.isEmpty()) lines.add(current.toString());
    }

    private void drawTruncated(GuiGraphics graphics, String value, int x, int y, int maxWidth, int color) {
        String text = value == null ? "" : value;
        while (!text.isEmpty() && Minecraft.getInstance().font.width(text) > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        graphics.drawString(Minecraft.getInstance().font, text, x, y, color);
    }

    private void drawRightAligned(GuiGraphics graphics, String value, int rightX, int y, int color) {
        String text = value == null ? "" : value;
        int maxWidth = Math.max(0, rightX - (shellX + PADDING) - 220);
        while (!text.isEmpty() && Minecraft.getInstance().font.width(text) > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        graphics.drawString(Minecraft.getInstance().font, text, rightX - Minecraft.getInstance().font.width(text), y, color);
    }

    private String itemName(String itemId) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) return itemId;
        Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
        return item == Items.AIR ? itemId : new ItemStack(item).getHoverName().getString();
    }

    private void setStatus(String messageKey) {
        resultMessageKey = messageKey == null ? "" : messageKey;
    }

    public void setResult(String messageKey) {
        waitingForServer = false;
        setStatus(messageKey);
        refreshUi();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        int step = scrollY > 0 ? -3 : 3;
        if (inside(mouseX, mouseY, professionRowsX, professionRowsY, professionRowsW, professionRowsH)) {
            professionScrollOffset = clamp(professionScrollOffset + step, 0,
                    Math.max(0, professions.size() - visibleRows(professionRowsH)));
            layoutProfessionRows();
            return true;
        }
        if (inside(mouseX, mouseY, traitRowsX, traitRowsY, traitRowsW, traitRowsH)) {
            traitScrollOffset = clamp(traitScrollOffset + step, 0,
                    Math.max(0, visibleTraits().size() - visibleRows(traitRowsH)));
            layoutTraitRows();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public void onClose() {
        // First-join character creation cannot be skipped by closing the screen.
    }

    private int screenHeight() {
        return size == null ? 0 : size.y;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private static String fit(String value, int maxWidth) {
        String text = value == null ? "" : value;
        int allowed = Math.max(8, maxWidth);
        while (!text.isEmpty() && Minecraft.getInstance().font.width(text) > allowed) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private static String tr(String key) {
        return Component.translatable(key).getString();
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : fallback;
    }

    private static int integer(JsonObject json, String key, int fallback) {
        return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsInt() : fallback;
    }

    private static Set<String> stringSet(JsonObject json, String key) {
        Set<String> values = new LinkedHashSet<>();
        JsonArray array = json.getAsJsonArray(key);
        if (array != null) array.forEach(value -> values.add(value.getAsString()));
        return values;
    }

    private static Map<String, Integer> integerMap(JsonObject json, String key) {
        Map<String, Integer> values = new LinkedHashMap<>();
        if (json.has(key) && json.get(key).isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject(key).entrySet()) {
                values.put(entry.getKey(), entry.getValue().getAsInt());
            }
        }
        return values;
    }

    private static List<StartingItem> startingItems(JsonObject json) {
        String key = json.has("starting_items") ? "starting_items" : "startItems";
        List<StartingItem> items = new ArrayList<>();
        if (json.has(key) && json.get(key).isJsonArray()) {
            for (JsonElement element : json.getAsJsonArray(key)) {
                JsonObject item = element.getAsJsonObject();
                items.add(new StartingItem(
                        string(item, "item", string(item, "id", "minecraft:air")),
                        integer(item, "count", 1)));
            }
        }
        return List.copyOf(items);
    }

    private static final class EntryButton extends SimpleButtonWidget {
        private final Supplier<String> label;
        private final BooleanSupplier selected;
        private final BooleanSupplier granted;
        private final BooleanSupplier available;
        private final IntSupplier textColor;
        private final boolean primary;
        private final Runnable action;

        private EntryButton(Vector2 position, Vector2 size,
                            Supplier<String> label,
                            BooleanSupplier selected,
                            BooleanSupplier granted,
                            BooleanSupplier available,
                            IntSupplier textColor,
                            boolean primary,
                            Runnable action) {
            super(Component.empty(), position, size);
            this.label = label;
            this.selected = selected;
            this.granted = granted;
            this.available = available;
            this.textColor = textColor;
            this.primary = primary;
            this.action = action;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            if (active && visible && available.getAsBoolean()) action.run();
        }

        @Override
        public void drawBackground(GuiGraphics graphics, int x, int y, int width, int height,
                                   int mouseX, int mouseY, float tick) {
            boolean isSelected = selected.getAsBoolean();
            boolean isGranted = granted.getAsBoolean();
            boolean hover = active && isMouseOver(mouseX, mouseY);
            int background;
            int accent;
            if (primary) {
                background = active ? (hover ? 0xFFE6B16F : ACCENT) : 0xFF5E574C;
                accent = active ? 0xFFFFD7A2 : BORDER;
            } else {
                background = isGranted ? 0xFF2C3A30
                        : isSelected ? ROW_SELECTED
                        : hover ? ROW_HOVER : ROW;
                accent = isGranted ? GREEN : isSelected ? ACCENT
                        : hover ? 0xFF889185 : BORDER;
                if (!active && !isGranted) background = 0xFF202422;
            }
            graphics.fill(x, y, x + width, y + height, background);
            graphics.fill(x, y, x + 2, y + height, accent);
            graphics.fill(x + 2, y, x + width, y + 1, BORDER);
            graphics.fill(x + 2, y + height - 1, x + width, y + height, BORDER);
        }

        @Override
        public void drawTitle(GuiGraphics graphics, int x, int y, int width, int height,
                              int mouseX, int mouseY, float tick) {
            String text = label.get();
            int availableWidth = Math.max(8, width - 12);
            while (!text.isEmpty() && Minecraft.getInstance().font.width(text) > availableWidth) {
                text = text.substring(0, text.length() - 1);
            }
            int color = primary ? (active ? 0xFF171A18 : MUTED)
                    : granted.getAsBoolean() ? GREEN
                    : selected.getAsBoolean() ? 0xFFFFD8AA
                    : textColor.getAsInt();
            graphics.drawString(Minecraft.getInstance().font, text, x + 6,
                    y + Math.max(2, (height - Minecraft.getInstance().font.lineHeight) / 2), color);
        }
    }

    private record ProfessionOption(String id, String name, String description, String icon,
                                    int startingPoints, Set<String> allowedTraits,
                                    Set<String> grantedTraits, Map<String, Integer> skillBonuses,
                                    List<StartingItem> startingItems) {
        private ProfessionOption {
            allowedTraits = Set.copyOf(allowedTraits);
            grantedTraits = Set.copyOf(grantedTraits);
            skillBonuses = Map.copyOf(skillBonuses);
            startingItems = List.copyOf(startingItems);
        }
    }

    private record TraitOption(String id, String name, String description, String category,
                               int pointCost, int pointCredit, Set<String> conflicts,
                               String requiresProfession) {
        private TraitOption {
            conflicts = Set.copyOf(conflicts);
        }
    }

    private record StartingItem(String itemId, int count) {}
}
