package dev.aftermath.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aftermath.network.SelectCharacterPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.LinkedHashMap;

/**
 * Survivor dossier drawn directly through GuiGraphics. Selection remains a client-side draft;
 * only the profession ID and trait IDs are submitted, and the server validates the final choice.
 */
public final class CharacterSelectionScreen extends Screen {
    private static final int SHELL = 0xF0090B08;
    private static final int PANEL = 0xE8171A15;
    private static final int PANEL_ALT = 0xED202219;
    private static final int ROW = 0xE51A1D17;
    private static final int ROW_HOVER = 0xEC34372A;
    private static final int ROW_SELECTED = 0xED493727;
    private static final int BORDER = 0xFF554B36;
    private static final int RUST = 0xFFD07843;
    private static final int PAPER = 0xFFE0D7B8;
    private static final int MUTED = 0xFF9E9B83;
    private static final int GREEN = 0xFF9AB58A;
    private static final int RED = 0xFFD77D64;
    private static final int GOLD = 0xFFE0C68A;
    private static final int GAP = 7;
    private static final int PAD = 8;
    private static final int TAB_COUNT = 3;
    private static final String TAB_POSITIVE = "positive";
    private static final String TAB_NEGATIVE = "negative";
    private static final String TAB_NEUTRAL = "neutral";
    private static final Random RANDOM = new Random();

    private static Draft savedDraft;

    private final List<ProfessionOption> professions = new ArrayList<>();
    private final List<TraitOption> traits = new ArrayList<>();
    private final Set<String> selectedTraits = new LinkedHashSet<>();

    private String selectedProfessionId = "aftermath:medic";
    private String resultMessageKey = "";
    private String activeTraitTab = TAB_POSITIVE;
    private String conflictingTraitId = "";
    private boolean waitingForServer;
    private boolean invalidDefinitions;
    private boolean traitsFocused;
    private int focusedTraitIndex;

    private int shellX, shellY, shellW, shellH;
    private int headerY, headerH, bodyY, bodyH, footerY, footerH;
    private Box professionPanel, professionViewport, previewPanel, traitPanel, traitViewport;
    private final Box[] traitTabs = new Box[TAB_COUNT];
    private Box randomButton, resetButton, startButton;
    private int professionScrollOffset, traitScrollOffset;
    private int professionRowHeight, professionRowStep;
    private int traitRowHeight, traitRowStep;

    public CharacterSelectionScreen(String definitionsJson) {
        super(Component.translatable("screen.aftermath.character.title"));
        readDefinitions(definitionsJson);
        professions.sort(Comparator.comparing(option -> tr(option.name)));
        traits.sort(Comparator.comparingInt(CharacterSelectionScreen::categoryOrder)
                .thenComparing(option -> tr(option.name)));

        if (!professions.isEmpty()) {
            selectedProfessionId = professions.stream()
                    .anyMatch(option -> option.id.equals("aftermath:medic"))
                    ? "aftermath:medic" : professions.get(0).id;
        }
        restoreDraft();
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
                            string(json, "category", "neutral").toLowerCase(),
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
    protected void init() {
        calculateLayout();
    }

    private void calculateLayout() {
        int margin = clamp(Math.min(width, height) / 32, 4, 16);
        shellW = Math.max(1, Math.min(width - margin * 2, 1100));
        shellH = Math.max(1, Math.min(height - margin * 2, 760));
        shellX = (width - shellW) / 2;
        shellY = (height - shellH) / 2;

        headerH = clamp(shellH / 9, 24, 48);
        footerH = clamp(shellH / 7, 36, 58);
        headerY = shellY + 5;
        footerY = shellY + shellH - footerH - 5;
        bodyY = shellY + headerH + 7;
        bodyH = Math.max(44, footerY - bodyY - 6);

        int pad = Math.min(PAD, Math.max(4, shellW / 90));
        int innerX = shellX + pad;
        int innerW = Math.max(2, shellW - pad * 2);
        int gap = Math.min(GAP, Math.max(4, shellW / 160));
        int leftW = Math.max(1, (innerW - gap) * 56 / 100);
        int rightW = Math.max(1, innerW - leftW - gap);

        professionPanel = new Box(innerX, bodyY, leftW, bodyH);
        professionViewport = new Box(
                professionPanel.x + 5,
                professionPanel.y + 23,
                Math.max(1, professionPanel.w - 16),
                Math.max(1, professionPanel.h - 29));

        int rightX = innerX + leftW + gap;
        int previewH = Math.max(36, Math.min(84, bodyH * 34 / 100));
        previewPanel = new Box(rightX, bodyY, rightW, Math.min(bodyH, previewH));
        int traitY = bodyY + previewPanel.h + gap;
        traitPanel = new Box(rightX, traitY, rightW,
                Math.max(1, bodyY + bodyH - traitY));
        int tabY = traitPanel.y + 20;
        int tabGap = 2;
        int tabW = Math.max(1, (traitPanel.w - 10 - tabGap * (TAB_COUNT - 1)) / TAB_COUNT);
        for (int i = 0; i < TAB_COUNT; i++) {
            traitTabs[i] = new Box(traitPanel.x + 5 + i * (tabW + tabGap), tabY, tabW, 16);
        }
        int traitTop = tabY + 20;
        traitViewport = new Box(traitPanel.x + 5, traitTop,
                Math.max(1, traitPanel.w - 16), Math.max(1, traitPanel.y + traitPanel.h - traitTop - 5));

        professionRowHeight = clamp(professionViewport.h / Math.max(4, Math.min(8, professions.size())), 18, 27);
        professionRowStep = professionRowHeight + 3;
        traitRowHeight = clamp(traitViewport.h / 5, 18, 25);
        traitRowStep = traitRowHeight + 3;

        int innerFooterX = shellX + pad;
        int buttonGap = 4;
        int buttonW = clamp((shellW - pad * 2 - 105 - buttonGap * 3) / 3, 56, 118);
        int buttonH = clamp(footerH - 15, 20, 31);
        int buttonY = footerY + Math.max(4, (footerH - buttonH) / 2);
        startButton = new Box(shellX + shellW - pad - buttonW, buttonY, buttonW, buttonH);
        resetButton = new Box(startButton.x - buttonGap - buttonW, buttonY, buttonW, buttonH);
        randomButton = new Box(resetButton.x - buttonGap - buttonW, buttonY, buttonW, buttonH);

        professionScrollOffset = clamp(professionScrollOffset, 0, maxProfessionOffset());
        traitScrollOffset = clamp(traitScrollOffset, 0, maxTraitOffset());
    }

    private void restoreDraft() {
        Draft draft = savedDraft;
        if (draft == null) return;
        if (professions.stream().anyMatch(option -> option.id.equals(draft.professionId))) {
            selectedProfessionId = draft.professionId;
            for (String id : draft.traitIds) {
                if (traitById(id) != null) selectedTraits.add(id);
            }
            removeInvalidDraftTraits();
        }
    }

    private void removeInvalidDraftTraits() {
        ProfessionOption selected = profession();
        if (selected == null) {
            selectedTraits.clear();
            return;
        }
        selectedTraits.removeIf(id -> {
            TraitOption trait = traitById(id);
            return trait == null
                    || selected.grantedTraits.contains(id)
                    || (!selected.allowedTraits.isEmpty() && !selected.allowedTraits.contains(id))
                    || (!trait.requiresProfession.isBlank() && !trait.requiresProfession.equals(selected.id));
        });
        List<String> ordered = new ArrayList<>(selectedTraits);
        selectedTraits.clear();
        Set<String> accepted = new LinkedHashSet<>(selected.grantedTraits);
        for (String id : ordered) {
            TraitOption candidate = traitById(id);
            if (candidate != null && !conflictsWithAny(candidate, accepted)) {
                selectedTraits.add(id);
                accepted.add(id);
            }
        }
    }

    private void saveDraft() {
        savedDraft = new Draft(selectedProfessionId, List.copyOf(selectedTraits));
    }

    private void selectProfession(String professionId) {
        if (waitingForServer || professionId.equals(selectedProfessionId)) return;
        selectedProfessionId = professionId;
        selectedTraits.removeIf(id -> {
            TraitOption trait = traitById(id);
            ProfessionOption selected = profession();
            return trait == null || selected == null || selected.grantedTraits.contains(id)
                    || (!selected.allowedTraits.isEmpty() && !selected.allowedTraits.contains(id))
                    || (!trait.requiresProfession.isBlank() && !trait.requiresProfession.equals(selected.id));
        });
        removeInvalidDraftTraits();
        professionScrollOffset = professions.indexOf(profession());
        traitScrollOffset = 0;
        focusedTraitIndex = 0;
        resultMessageKey = "";
        conflictingTraitId = "";
        traitsFocused = false;
        saveDraft();
    }

    private void toggleTrait(TraitOption option) {
        if (waitingForServer || profession() == null || profession().grantedTraits.contains(option.id)) return;
        if (selectedTraits.remove(option.id)) {
            resultMessageKey = "";
            conflictingTraitId = "";
            saveDraft();
            return;
        }

        Set<String> activeIds = activeTraitIds();
        TraitOption conflict = conflictingSelectedTrait(option, activeIds);
        if (conflict != null) {
            conflictingTraitId = conflict.id;
            resultMessageKey = "screen.aftermath.character.error.conflicting_traits";
            return;
        }

        selectedTraits.add(option.id);
        resultMessageKey = "";
        conflictingTraitId = "";
        saveDraft();
    }

    private void submit() {
        if (!canSubmit()) return;
        waitingForServer = true;
        resultMessageKey = "";
        saveDraft();
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
            if (trait != null) remaining += trait.category.equals("positive") ? -trait.pointCost : trait.pointCredit;
        }
        return remaining;
    }

    private ProfessionOption profession() {
        return professions.stream().filter(option -> option.id.equals(selectedProfessionId)).findFirst().orElse(null);
    }

    private TraitOption traitById(String id) {
        return traits.stream().filter(option -> option.id.equals(id)).findFirst().orElse(null);
    }

    private List<TraitOption> availableTraits() {
        ProfessionOption selected = profession();
        if (selected == null) return List.of();
        return traits.stream()
                .filter(trait -> selected.allowedTraits.isEmpty() || selected.allowedTraits.contains(trait.id))
                .filter(trait -> trait.requiresProfession.isBlank() || trait.requiresProfession.equals(selected.id))
                .toList();
    }

    private List<TraitOption> activeTabTraits() {
        return availableTraits().stream().filter(trait -> trait.category.equals(activeTraitTab))
                .sorted(Comparator.comparing(option -> tr(option.name))).toList();
    }

    private Set<String> activeTraitIds() {
        Set<String> active = new LinkedHashSet<>(selectedTraits);
        ProfessionOption selected = profession();
        if (selected != null) active.addAll(selected.grantedTraits);
        return active;
    }

    private TraitOption conflictingSelectedTrait(TraitOption candidate, Set<String> activeIds) {
        for (String id : activeIds) {
            TraitOption existing = traitById(id);
            if (existing == null || existing.id.equals(candidate.id)) continue;
            if (candidate.conflicts.contains(id) || existing.conflicts.contains(candidate.id)) return existing;
        }
        return null;
    }

    private boolean conflictsWithAny(TraitOption candidate, Set<String> activeIds) {
        return conflictingSelectedTrait(candidate, activeIds) != null;
    }

    private static int categoryOrder(TraitOption option) {
        return switch (option.category) {
            case "positive" -> 0;
            case "negative" -> 1;
            default -> 2;
        };
    }

    private static int categoryColor(TraitOption option) {
        return switch (option.category) {
            case "positive" -> GREEN;
            case "negative" -> RED;
            default -> PAPER;
        };
    }

    private int maxProfessionOffset() {
        return Math.max(0, professions.size() - visibleProfessionRows());
    }

    private int visibleProfessionRows() {
        return Math.max(1, (professionViewport.h + 3) / professionRowStep);
    }

    private int maxTraitOffset() {
        return Math.max(0, activeTabTraits().size() - visibleTraitRows());
    }

    private int visibleTraitRows() {
        return Math.max(1, (traitViewport.h + 3) / traitRowStep);
    }

    private int professionIndexAt(double mouseX, double mouseY) {
        if (!professionViewport.contains(mouseX, mouseY)) return -1;
        double localY = mouseY - professionViewport.y;
        int row = (int) Math.floor(localY / professionRowStep);
        double inRow = localY - (double) row * professionRowStep;
        int index = professionScrollOffset + row;
        return inRow < professionRowHeight && index >= 0 && index < professions.size() ? index : -1;
    }

    private int traitIndexAt(double mouseX, double mouseY) {
        if (!traitViewport.contains(mouseX, mouseY)) return -1;
        double localY = mouseY - traitViewport.y;
        int row = (int) Math.floor(localY / traitRowStep);
        double inRow = localY - (double) row * traitRowStep;
        int index = traitScrollOffset + row;
        return inRow < traitRowHeight && index >= 0 && index < activeTabTraits().size() ? index : -1;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        drawAtmosphere(graphics);
        drawFrame(graphics);
        drawHeader(graphics);
        drawProfessionPanel(graphics, mouseX, mouseY);
        drawPreviewPanel(graphics);
        drawTraitPanel(graphics, mouseX, mouseY);
        drawFooter(graphics, mouseX, mouseY);

        int professionIndex = professionIndexAt(mouseX, mouseY);
        int traitIndex = traitIndexAt(mouseX, mouseY);
        if (professionIndex >= 0) {
            ProfessionOption option = professions.get(professionIndex);
            drawTooltip(graphics, mouseX, mouseY, tr(option.name), tr(option.description), null);
        } else if (traitIndex >= 0) {
            TraitOption option = activeTabTraits().get(traitIndex);
            TraitOption conflict = conflictingSelectedTrait(option, activeTraitIds());
            String extra = conflict == null ? "" : tr("screen.aftermath.character.ui.incompatible", tr(conflict.name));
            int projectedBalance = remainingPoints() + pointDelta(option);
            if (projectedBalance < 0 && !selectedTraits.contains(option.id)) {
                String shortage = tr("screen.aftermath.character.ui.points_shortage", -projectedBalance);
                extra = extra.isBlank() ? shortage : extra + "\n" + shortage;
            }
            drawTooltip(graphics, mouseX, mouseY, tr(option.name), tr(option.description), extra);
        } else if (remainingPoints() < 0) {
            if (insideButton(mouseX, mouseY, startButton)) {
                drawTooltip(graphics, mouseX, mouseY, tr("screen.aftermath.character.start"),
                        tr("screen.aftermath.character.ui.points_shortage", -remainingPoints()), null);
            }
        }
    }

    private void drawAtmosphere(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, 0xA7070907);
        int edge = Math.max(6, Math.min(34, Math.min(width, height) / 12));
        for (int i = 0; i < edge; i++) {
            int alpha = Math.max(0x02, 0x20 - i * 0x20 / edge);
            int shade = alpha << 24;
            graphics.fill(i, 0, i + 1, height, shade);
            graphics.fill(width - i - 1, 0, width - i, height, shade);
            graphics.fill(0, i, width, i + 1, shade);
            graphics.fill(0, height - i - 1, width, height - i, shade);
        }
        long seed = 0x5EEDL;
        for (int i = 0; i < 54; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int x = (int) Math.floorMod(seed >>> 16, Math.max(1, width));
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            int y = (int) Math.floorMod(seed >>> 16, Math.max(1, height));
            int len = 1 + (int) (seed & 3L);
            graphics.fill(x, y, Math.min(width, x + len), Math.min(height, y + 1), 0x16D1B68B);
        }
    }

    private void drawFrame(GuiGraphics graphics) {
        drawPanel(graphics, new Box(shellX, shellY, shellW, shellH), true);
        graphics.fill(shellX + 8, shellY + 4, shellX + shellW - 8, shellY + 5, RUST);
        graphics.fill(shellX + 12, shellY + shellH - 5, shellX + shellW - 12, shellY + shellH - 4, BORDER);
        double flicker = (Math.sin(System.currentTimeMillis() / 517.0D) + 1.0D) * 0.5D;
        int lamp = flicker > 0.75D ? 0xFFE0A05C : 0xFF8B5A36;
        graphics.fill(shellX + shellW - 24, shellY + 8, shellX + shellW - 14, shellY + 10, lamp);
        graphics.fill(shellX + shellW - 22, shellY + 10, shellX + shellW - 16, shellY + 12, 0x604A2916);
    }

    private void drawHeader(GuiGraphics graphics) {
        drawText(graphics, "screen.aftermath.character.ui.kicker", shellX + PAD, shellY + 8, RUST);
        drawText(graphics, "screen.aftermath.character.title", shellX + PAD, shellY + 20, PAPER);
        String subtitle = tr("screen.aftermath.character.subtitle");
        drawRight(graphics, subtitle, shellX + shellW - PAD, shellY + 21, MUTED);
        int stitchY = shellY + headerH + 1;
        for (int x = shellX + 10; x < shellX + shellW - 10; x += 7) {
            graphics.fill(x, stitchY, x + 3, stitchY + 1, (x / 7 % 3 == 0) ? RUST : BORDER);
        }
    }

    private void drawProfessionPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        drawPanel(graphics, professionPanel, false);
        drawText(graphics, "screen.aftermath.character.profession", professionPanel.x + 7, professionPanel.y + 7, RUST);
        int visible = visibleProfessionRows();
        int hoveredIndex = professionIndexAt(mouseX, mouseY);
        graphics.enableScissor(professionViewport.x, professionViewport.y,
                professionViewport.x + professionViewport.w, professionViewport.y + professionViewport.h);
        try {
            for (int row = 0; row < visible; row++) {
                int index = professionScrollOffset + row;
                if (index >= professions.size()) break;
                ProfessionOption option = professions.get(index);
                int y = professionViewport.y + row * professionRowStep;
                boolean selected = option.id.equals(selectedProfessionId);
                boolean hovered = index == hoveredIndex;
                Box card = new Box(professionViewport.x, y, professionViewport.w, professionRowHeight);
                drawRow(graphics, card, selected, hovered, false);

                int iconX = card.x + 4;
                int iconY = card.y + Math.max(1, (card.h - 16) / 2);
                ItemStack icon = professionIcon(option.icon);
                if (!icon.isEmpty()) graphics.renderItem(icon, iconX, iconY);
                int badgeW = Math.min(48, Math.max(28, card.w / 4));
                String badge = "+" + option.startingPoints;
                drawText(graphics, badge, card.x + card.w - badgeW, card.y + Math.max(2, card.h / 2 - 4),
                        selected ? GOLD : MUTED);
                String title = fit(tr(option.name), Math.max(8, card.w - 35 - badgeW));
                drawString(graphics, (selected ? "› " : "  ") + title, card.x + 24,
                        card.y + Math.max(2, (card.h - font.lineHeight) / 2), selected ? GOLD : PAPER);
            }
        } finally {
            graphics.disableScissor();
        }
        drawScrollbar(graphics, professionPanel.x + professionPanel.w - 7,
                professionViewport.y, professionViewport.h, professions.size(), visible, professionScrollOffset);
    }

    private void drawPreviewPanel(GuiGraphics graphics) {
        drawPanel(graphics, previewPanel, false);
        drawText(graphics, "screen.aftermath.character.ui.preview", previewPanel.x + 6, previewPanel.y + 5, RUST);
        ProfessionOption selected = profession();
        if (selected == null) return;

        int headSize = Math.max(14, Math.min(26, previewPanel.h - 20));
        int headX = previewPanel.x + 6;
        int headY = previewPanel.y + 19;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            ResourceLocation skin = minecraft.player.getSkin().texture();

            // Draw only the 8x8 face region and its hat layer, then scale the pose to the dossier size.
            // GuiGraphics uses the skin's nearest-neighbour texture sampler, keeping pixels crisp.
            graphics.pose().pushPose();
            graphics.pose().translate(headX, headY, 0.0D);
            float headScale = headSize / 8.0F;
            graphics.pose().scale(headScale, headScale, 1.0F);
            graphics.blit(skin, 0, 0, 8.0F, 8.0F, 8, 8, 64, 64);
            graphics.blit(skin, 0, 0, 40.0F, 8.0F, 8, 8, 64, 64);
            graphics.pose().popPose();
        } else {
            graphics.renderItem(new ItemStack(Items.PLAYER_HEAD), headX, headY);
        }

        int textX = headX + headSize + 6;
        int textW = Math.max(12, previewPanel.x + previewPanel.w - textX - 6);
        String name = Minecraft.getInstance().player == null
                ? tr("screen.aftermath.character.ui.survivor")
                : Minecraft.getInstance().player.getGameProfile().getName();
        drawString(graphics, fit(name, textW), textX, headY, PAPER);
        drawString(graphics, fit(tr(selected.name), textW), textX, headY + 10, GOLD);
        int descY = headY + 20;
        if (descY + font.lineHeight < previewPanel.y + previewPanel.h) {
            drawString(graphics, fit(tr(selected.description), textW), textX, descY, MUTED);
        }
    }

    private void drawTraitPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        drawPanel(graphics, traitPanel, false);
        drawText(graphics, "screen.aftermath.character.traits", traitPanel.x + 6, traitPanel.y + 5, RUST);
        String[] tabIds = {TAB_POSITIVE, TAB_NEGATIVE, TAB_NEUTRAL};
        for (int i = 0; i < TAB_COUNT; i++) {
            Box tab = traitTabs[i];
            boolean active = activeTraitTab.equals(tabIds[i]);
            boolean hover = tab.contains(mouseX, mouseY);
            int fill = active ? ROW_SELECTED : hover ? ROW_HOVER : 0xB50C0E0B;
            graphics.fill(tab.x, tab.y, tab.x + tab.w, tab.y + tab.h, fill);
            graphics.fill(tab.x, tab.y, tab.x + tab.w, tab.y + 1, active ? RUST : BORDER);
            String key = "screen.aftermath.character.ui.tab." + tabIds[i] + (tab.w < 55 ? ".short" : "");
            drawCentered(graphics, tr(key), tab.x + tab.w / 2, tab.y + 4, active ? GOLD : MUTED);
        }

        List<TraitOption> options = activeTabTraits();
        int hoveredIndex = traitIndexAt(mouseX, mouseY);
        Set<String> highlightedConflicts = new LinkedHashSet<>();
        if (hoveredIndex >= 0) {
            TraitOption hovered = options.get(hoveredIndex);
            TraitOption conflict = conflictingSelectedTrait(hovered, activeTraitIds());
            if (conflict != null) highlightedConflicts.add(conflict.id);
        }

        int visible = visibleTraitRows();
        graphics.enableScissor(traitViewport.x, traitViewport.y,
                traitViewport.x + traitViewport.w, traitViewport.y + traitViewport.h);
        try {
            for (int row = 0; row < visible; row++) {
                int index = traitScrollOffset + row;
                if (index >= options.size()) break;
                TraitOption option = options.get(index);
                int y = traitViewport.y + row * traitRowStep;
                boolean chosen = selectedTraits.contains(option.id)
                        || (profession() != null && profession().grantedTraits.contains(option.id));
                boolean granted = profession() != null && profession().grantedTraits.contains(option.id);
                boolean hovered = index == hoveredIndex;
                boolean conflictTarget = highlightedConflicts.contains(option.id);
                Box card = new Box(traitViewport.x, y, traitViewport.w, traitRowHeight);
                drawRow(graphics, card, chosen, hovered, conflictTarget);

                String marker = granted ? "★" : chosen ? "✓" : "□";
                drawString(graphics, marker, card.x + 4, card.y + Math.max(2, (card.h - font.lineHeight) / 2),
                        granted ? GREEN : categoryColor(option));
                int badgeW = Math.min(36, Math.max(23, card.w / 4));
                int delta = pointDelta(option);
                String cost = delta > 0 ? "+" + delta : Integer.toString(delta);
                drawRight(graphics, cost, card.x + card.w - 4, card.y + Math.max(2, (card.h - font.lineHeight) / 2),
                        delta > 0 ? GREEN : delta < 0 ? RED : MUTED);
                String title = fit(tr(option.name), Math.max(6, card.w - badgeW - 18));
                drawString(graphics, title, card.x + 17, card.y + Math.max(2, (card.h - font.lineHeight) / 2),
                        conflictTarget ? RED : chosen ? GOLD : categoryColor(option));
            }
            if (options.isEmpty()) {
                drawText(graphics, "screen.aftermath.character.ui.no_traits_in_category",
                        traitViewport.x + 3, traitViewport.y + 4, MUTED);
            }
        } finally {
            graphics.disableScissor();
        }
        drawScrollbar(graphics, traitPanel.x + traitPanel.w - 7,
                traitViewport.y, traitViewport.h, options.size(), visible, traitScrollOffset);
    }

    private void drawFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        Box footer = new Box(shellX + 5, footerY, shellW - 10, footerH);
        drawPanel(graphics, footer, false);
        int points = profession() == null ? 0 : remainingPoints();
        String balance = tr("screen.aftermath.character.ui.balance", points);
        drawString(graphics, fit(balance, Math.max(20, randomButton.x - footer.x - 14)),
                footer.x + 7, footer.y + 5, points < 0 ? RED : GREEN);
        String status;
        int statusColor;
        if (!resultMessageKey.isBlank()) {
            status = tr(resultMessageKey);
            statusColor = resultMessageKey.contains(".error.") ? RED : GOLD;
        } else if (points < 0) {
            status = tr("screen.aftermath.character.ui.points_shortage", -points);
            statusColor = RED;
        } else {
            status = tr("screen.aftermath.character.ui.footer_hint");
            statusColor = MUTED;
        }
        drawString(graphics, fit(status, Math.max(15, randomButton.x - footer.x - 14)),
                footer.x + 7, footer.y + 21, statusColor);

        drawButton(graphics, randomButton, tr("screen.aftermath.character.random"), true, false, mouseX, mouseY);
        drawButton(graphics, resetButton, tr("screen.aftermath.character.reset"), true, false, mouseX, mouseY);
        drawButton(graphics, startButton,
                tr(waitingForServer ? "screen.aftermath.character.saving" : "screen.aftermath.character.start"),
                canSubmit(), true, mouseX, mouseY);
    }

    private void drawPanel(GuiGraphics graphics, Box box, boolean outer) {
        if (box.w <= 0 || box.h <= 0) return;
        graphics.fill(box.x, box.y, box.x + box.w, box.y + box.h, outer ? SHELL : PANEL);
        graphics.fill(box.x, box.y, box.x + box.w, box.y + 1, BORDER);
        graphics.fill(box.x, box.y + box.h - 1, box.x + box.w, box.y + box.h, BORDER);
        graphics.fill(box.x, box.y, box.x + 1, box.y + box.h, BORDER);
        graphics.fill(box.x + box.w - 1, box.y, box.x + box.w, box.y + box.h, BORDER);
        if (box.w > 18 && box.h > 12) {
            graphics.fill(box.x + 2, box.y + 2, box.x + 8, box.y + 3, RUST);
            graphics.fill(box.x + box.w - 8, box.y + box.h - 3, box.x + box.w - 2, box.y + box.h - 2, RUST);
            graphics.fill(box.x + 3, box.y + box.h / 2, box.x + 4, box.y + box.h / 2 + 3, 0xFF30291D);
        }
    }

    private void drawRow(GuiGraphics graphics, Box row, boolean selected, boolean hovered, boolean conflict) {
        int fill = selected ? ROW_SELECTED : hovered ? ROW_HOVER : ROW;
        int edge = conflict ? RED : selected ? RUST : hovered ? 0xFF857354 : BORDER;
        graphics.fill(row.x, row.y, row.x + row.w, row.y + row.h, fill);
        graphics.fill(row.x, row.y, row.x + 2, row.y + row.h, edge);
        graphics.fill(row.x + 2, row.y, row.x + row.w, row.y + 1, BORDER);
        graphics.fill(row.x + 2, row.y + row.h - 1, row.x + row.w, row.y + row.h, BORDER);
        if (hovered) {
            graphics.fill(row.x + 3, row.y + 1, row.x + row.w - 2, row.y + 2, 0xFF77674B);
        }
    }

    private void drawScrollbar(GuiGraphics graphics, int x, int y, int h, int total, int visible, int offset) {
        if (total <= visible || h <= 6) return;
        graphics.fill(x, y, x + 3, y + h, 0xD3080A08);
        int thumbH = Math.max(10, h * visible / Math.max(1, total));
        int thumbY = y + (h - thumbH) * offset / Math.max(1, total - visible);
        graphics.fill(x, thumbY, x + 3, thumbY + thumbH, BORDER);
        graphics.fill(x, thumbY, x + 1, thumbY + thumbH, RUST);
    }

    private void drawButton(GuiGraphics graphics, Box box, String label, boolean enabled, boolean primary,
                            int mouseX, int mouseY) {
        boolean hover = box.contains(mouseX, mouseY);
        int base = !enabled ? 0xFF292922 : primary ? (hover ? 0xFF65412A : 0xFF35271D)
                : hover ? 0xFF36372B : 0xFF20231B;
        int edge = !enabled ? 0xFF414137 : hover ? RUST : BORDER;
        graphics.fill(box.x, box.y, box.x + box.w, box.y + box.h, base);
        graphics.fill(box.x, box.y, box.x + box.w, box.y + 1, edge);
        graphics.fill(box.x, box.y + box.h - 1, box.x + box.w, box.y + box.h, edge);
        graphics.fill(box.x, box.y, box.x + 2, box.y + box.h, edge);
        graphics.fill(box.x + box.w - 2, box.y, box.x + box.w, box.y + box.h, edge);
        drawCentered(graphics, fit(label, box.w - 8), box.x + box.w / 2,
                box.y + Math.max(2, (box.h - font.lineHeight) / 2), enabled ? (hover ? GOLD : PAPER) : MUTED);
        if (hover) graphics.fill(box.x + 5, box.y + 2, box.x + box.w - 5, box.y + 3, 0xFF8B613D);
    }

    private void drawTooltip(GuiGraphics graphics, int mouseX, int mouseY, String title, String description, String extra) {
        int maxWidth = Math.min(240, Math.max(110, width - 16));
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.addAll(font.split(Component.literal(description == null ? "" : description), maxWidth - 14));
        if (extra != null && !extra.isBlank()) {
            lines.addAll(font.split(Component.literal(extra.replace("\n", "  •  ")), maxWidth - 14));
        }
        int longest = Math.min(maxWidth, Math.max(font.width(title) + 14,
                lines.stream().mapToInt(font::width).max().orElse(40) + 14));
        int boxH = font.lineHeight + 10 + lines.size() * (font.lineHeight + 1);
        int x = Math.min(mouseX + 12, width - longest - 4);
        int y = Math.min(mouseY + 10, height - boxH - 4);
        x = Math.max(4, x);
        y = Math.max(4, y);

        graphics.fill(x, y, x + longest, y + boxH, 0xF20B0D09);
        graphics.fill(x, y, x + longest, y + 1, RUST);
        graphics.fill(x, y + boxH - 1, x + longest, y + boxH, BORDER);
        graphics.fill(x, y, x + 1, y + boxH, BORDER);
        graphics.fill(x + longest - 1, y, x + longest, y + boxH, BORDER);
        drawString(graphics, fit(title, longest - 12), x + 6, y + 5, GOLD);
        int lineY = y + font.lineHeight + 8;
        int drawn = 0;
        for (FormattedCharSequence line : lines) {
            if (lineY + font.lineHeight >= y + boxH) break;
            graphics.drawString(font, line, x + 6, lineY, drawn >= lines.size() - 1 && extra != null ? RED : MUTED);
            lineY += font.lineHeight + 1;
            drawn++;
        }
    }

    private void drawWrapped(GuiGraphics graphics, String text, int x, int y, int maxWidth, int maxLines, int color) {
        if (text == null || text.isBlank() || maxWidth <= 0 || maxLines <= 0) return;
        List<FormattedCharSequence> lines = font.split(Component.literal(text), maxWidth);
        for (int i = 0; i < Math.min(maxLines, lines.size()); i++) {
            graphics.drawString(font, lines.get(i), x, y + i * (font.lineHeight + 1), color);
        }
    }

    private void drawText(GuiGraphics graphics, String key, int x, int y, int color) {
        drawString(graphics, tr(key), x, y, color);
    }

    private void drawString(GuiGraphics graphics, String text, int x, int y, int color) {
        graphics.drawString(font, text, x, y, color);
    }

    private void drawCentered(GuiGraphics graphics, String text, int centerX, int y, int color) {
        graphics.drawCenteredString(font, text, centerX, y, color);
    }

    private void drawRight(GuiGraphics graphics, String text, int rightX, int y, int color) {
        String clipped = fit(text, Math.max(0, rightX - shellX - PAD));
        graphics.drawString(font, clipped, rightX - font.width(clipped), y, color);
    }

    private String fit(String text, int maxWidth) {
        String result = text == null ? "" : text;
        int allowed = Math.max(0, maxWidth);
        if (font.width(result) <= allowed) return result;
        while (!result.isEmpty() && font.width(result + "…") > allowed) {
            result = result.substring(0, result.length() - 1);
        }
        return result.isEmpty() ? "" : result + "…";
    }

    private ItemStack professionIcon(String iconId) {
        ResourceLocation id = ResourceLocation.tryParse(iconId);
        Item item = id == null ? Items.PLAYER_HEAD : BuiltInRegistries.ITEM.getOptional(id).orElse(Items.PLAYER_HEAD);
        return new ItemStack(item);
    }

    private void drawStatusIfHovered(GuiGraphics graphics, int mouseX, int mouseY) {
        if (remainingPoints() < 0 && startButton.contains(mouseX, mouseY)) {
            drawTooltip(graphics, mouseX, mouseY, tr("screen.aftermath.character.start"),
                    tr("screen.aftermath.character.ui.points_shortage", -remainingPoints()), null);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(mouseX, mouseY, button);
        if (randomButton.contains(mouseX, mouseY)) {
            if (!waitingForServer) chooseRandom();
            return true;
        }
        if (resetButton.contains(mouseX, mouseY)) {
            if (!waitingForServer) resetSelection();
            return true;
        }
        if (startButton.contains(mouseX, mouseY)) {
            submit();
            return true;
        }
        int professionIndex = professionIndexAt(mouseX, mouseY);
        if (professionIndex >= 0) {
            traitsFocused = false;
            selectProfession(professions.get(professionIndex).id);
            return true;
        }
        for (int i = 0; i < TAB_COUNT; i++) {
            if (traitTabs[i].contains(mouseX, mouseY)) {
                activeTraitTab = switch (i) {
                    case 0 -> TAB_POSITIVE;
                    case 1 -> TAB_NEGATIVE;
                    default -> TAB_NEUTRAL;
                };
                traitScrollOffset = 0;
                focusedTraitIndex = 0;
                traitsFocused = true;
                return true;
            }
        }
        int traitIndex = traitIndexAt(mouseX, mouseY);
        if (traitIndex >= 0) {
            traitsFocused = true;
            focusedTraitIndex = traitIndex;
            toggleTrait(activeTabTraits().get(traitIndex));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void chooseRandom() {
        if (professions.isEmpty()) return;
        ProfessionOption selected = professions.get(RANDOM.nextInt(professions.size()));
        selectedProfessionId = selected.id;
        selectedTraits.clear();
        List<TraitOption> candidates = new ArrayList<>(availableTraits());
        candidates.removeIf(trait -> selected.grantedTraits.contains(trait.id));
        Collections.shuffle(candidates, RANDOM);
        Set<String> active = new LinkedHashSet<>(selected.grantedTraits);
        int balance = selected.startingPoints;
        for (TraitOption option : candidates) {
            if (!RANDOM.nextBoolean() || conflictsWithAny(option, active)) continue;
            int next = balance + (option.category.equals("positive") ? -option.pointCost : option.pointCredit);
            if (next < 0) continue;
            selectedTraits.add(option.id);
            active.add(option.id);
            balance = next;
        }
        professionScrollOffset = Math.max(0, professions.indexOf(selected) - visibleProfessionRows() / 2);
        traitScrollOffset = 0;
        focusedTraitIndex = 0;
        activeTraitTab = TAB_POSITIVE;
        resultMessageKey = "";
        conflictingTraitId = "";
        traitsFocused = false;
        calculateLayout();
        saveDraft();
    }

    private void resetSelection() {
        selectedProfessionId = professions.stream().anyMatch(option -> option.id.equals("aftermath:medic"))
                ? "aftermath:medic" : professions.isEmpty() ? "" : professions.get(0).id;
        selectedTraits.clear();
        professionScrollOffset = 0;
        traitScrollOffset = 0;
        focusedTraitIndex = 0;
        activeTraitTab = TAB_POSITIVE;
        resultMessageKey = "";
        conflictingTraitId = "";
        waitingForServer = false;
        traitsFocused = false;
        calculateLayout();
        saveDraft();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0.0D) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (professionViewport.contains(mouseX, mouseY)) {
            professionScrollOffset = clamp(professionScrollOffset + (scrollY > 0 ? -2 : 2), 0, maxProfessionOffset());
            return true;
        }
        if (traitViewport.contains(mouseX, mouseY)) {
            traitScrollOffset = clamp(traitScrollOffset + (scrollY > 0 ? -2 : 2), 0, maxTraitOffset());
            focusedTraitIndex = clamp(focusedTraitIndex, 0, Math.max(0, activeTabTraits().size() - 1));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            saveDraft();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            traitsFocused = !traitsFocused;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE && traitsFocused) {
            List<TraitOption> visible = activeTabTraits();
            if (!visible.isEmpty()) toggleTrait(visible.get(clamp(focusedTraitIndex, 0, visible.size() - 1)));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            int direction = keyCode == GLFW.GLFW_KEY_UP ? -1 : 1;
            if (!traitsFocused) moveProfessionFocus(direction);
            else moveTraitFocus(direction);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT && traitsFocused) {
            changeTraitTab(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT && traitsFocused) {
            changeTraitTab(1);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void moveProfessionFocus(int direction) {
        if (professions.isEmpty()) return;
        int current = Math.max(0, professions.indexOf(profession()));
        int next = clamp(current + direction, 0, professions.size() - 1);
        selectProfession(professions.get(next).id);
        if (next < professionScrollOffset) professionScrollOffset = next;
        else if (next >= professionScrollOffset + visibleProfessionRows()) {
            professionScrollOffset = next - visibleProfessionRows() + 1;
        }
    }

    private void moveTraitFocus(int direction) {
        List<TraitOption> options = activeTabTraits();
        if (options.isEmpty()) return;
        focusedTraitIndex = clamp(focusedTraitIndex + direction, 0, options.size() - 1);
        if (focusedTraitIndex < traitScrollOffset) traitScrollOffset = focusedTraitIndex;
        else if (focusedTraitIndex >= traitScrollOffset + visibleTraitRows()) {
            traitScrollOffset = focusedTraitIndex - visibleTraitRows() + 1;
        }
    }

    private void changeTraitTab(int direction) {
        int index = activeTraitTab.equals(TAB_POSITIVE) ? 0 : activeTraitTab.equals(TAB_NEGATIVE) ? 1 : 2;
        index = clamp(index + direction, 0, TAB_COUNT - 1);
        activeTraitTab = index == 0 ? TAB_POSITIVE : index == 1 ? TAB_NEGATIVE : TAB_NEUTRAL;
        traitScrollOffset = 0;
        focusedTraitIndex = 0;
    }

    public void setResult(String messageKey) {
        waitingForServer = false;
        resultMessageKey = messageKey == null ? "" : messageKey;
    }

    @Override
    public void onClose() {
        // First-join creation must not be skipped. The draft is still saved before this callback.
        saveDraft();
    }

    @Override
    public void removed() {
        saveDraft();
    }

    private int pointDelta(TraitOption option) {
        return option.category.equals("positive") ? -option.pointCost
                : option.category.equals("negative") ? option.pointCredit : 0;
    }

    private String tr(String key) {
        return Component.translatable(key).getString();
    }

    private String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private static boolean insideButton(double mouseX, double mouseY, Box box) {
        return box != null && box.contains(mouseX, mouseY);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, Math.max(min, max)));
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

    private record Box(int x, int y, int w, int h) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        }
    }

    private record Draft(String professionId, List<String> traitIds) {}
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
