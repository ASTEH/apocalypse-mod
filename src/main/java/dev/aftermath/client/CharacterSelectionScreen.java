package dev.aftermath.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aftermath.network.SelectCharacterPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** First-join profession and trait selection. The server revalidates every submitted choice. */
public final class CharacterSelectionScreen extends Screen {
    private final List<ProfessionOption> professions = new ArrayList<>();
    private final List<TraitOption> traits = new ArrayList<>();
    private final Set<String> selectedTraits = new LinkedHashSet<>();
    private String selectedProfessionId = "";
    private String resultMessageKey = "";
    private boolean waitingForServer;
    private int professionPage;
    private int traitPage;
    private boolean widgetRefreshRequested;

    public CharacterSelectionScreen(String definitionsJson) {
        super(Component.translatable("screen.aftermath.character.title"));
        readDefinitions(definitionsJson);
        professions.sort(Comparator.comparing(option -> option.id));
        traits.sort(Comparator.comparing(option -> option.id));
        professions.stream().filter(option -> option.id.equals("aftermath:medic")).findFirst()
                .ifPresentOrElse(option -> selectedProfessionId = option.id,
                        () -> {
                            if (!professions.isEmpty()) selectedProfessionId = professions.get(0).id;
                        });
    }

    private void readDefinitions(String definitionsJson) {
        try {
            JsonObject root = JsonParser.parseString(definitionsJson).getAsJsonObject();
            JsonArray professionArray = root.getAsJsonArray("professions");
            if (professionArray != null) {
                for (JsonElement element : professionArray) {
                    JsonObject json = element.getAsJsonObject();
                    Set<String> allowed = new LinkedHashSet<>();
                    JsonArray allowedArray = json.getAsJsonArray("allowed_traits");
                    if (allowedArray != null) allowedArray.forEach(id -> allowed.add(id.getAsString()));
                    Set<String> granted = new LinkedHashSet<>();
                    JsonArray grantedArray = json.getAsJsonArray("granted_traits");
                    if (grantedArray != null) grantedArray.forEach(id -> granted.add(id.getAsString()));
                    Map<String, Integer> skillBonuses = new LinkedHashMap<>();
                    if (json.has("skill_bonuses") && json.get("skill_bonuses").isJsonObject()) {
                        for (Map.Entry<String, JsonElement> skill : json.getAsJsonObject("skill_bonuses").entrySet()) {
                            skillBonuses.put(skill.getKey(), skill.getValue().getAsInt());
                        }
                    }
                    professions.add(new ProfessionOption(
                            json.get("id").getAsString(),
                            json.get("name").getAsString(),
                            json.get("description").getAsString(),
                            json.has("icon") ? json.get("icon").getAsString() : "minecraft:player_head",
                            json.get("starting_points").getAsInt(),
                            Set.copyOf(allowed),
                            Set.copyOf(granted),
                            Map.copyOf(skillBonuses)));
                }
            }

            JsonArray traitArray = root.getAsJsonArray("traits");
            if (traitArray != null) {
                for (JsonElement element : traitArray) {
                    JsonObject json = element.getAsJsonObject();
                    Set<String> conflicts = new LinkedHashSet<>();
                    JsonArray conflictArray = json.getAsJsonArray("conflicts");
                    if (conflictArray != null) conflictArray.forEach(id -> conflicts.add(id.getAsString()));
                    String requiresProfession = json.has("requires_profession")
                            ? json.get("requires_profession").getAsString() : "";
                    traits.add(new TraitOption(
                            json.get("id").getAsString(),
                            json.get("name").getAsString(),
                            json.get("description").getAsString(),
                            json.get("category").getAsString(),
                            json.get("point_cost").getAsInt(),
                            json.get("point_credit").getAsInt(),
                            Set.copyOf(conflicts),
                            requiresProfession));
                }
            }
        } catch (RuntimeException exception) {
            professions.clear();
            traits.clear();
        }
    }

    @Override
    protected void init() {
        ProfessionOption selected = profession();
        int selectedIndex = selected == null ? 0 : professions.indexOf(selected);
        professionPage = Math.max(0, selectedIndex / professionsPerPage());
        refreshCharacterWidgets();
    }

    /**
     * Rebuild widgets on the next screen tick instead of while a button click is still
     * traversing Screen's listener list. This avoids mutating the live widget collection
     * from inside the click callback.
     */
    @Override
    public void tick() {
        super.tick();
        if (widgetRefreshRequested) {
            widgetRefreshRequested = false;
            refreshCharacterWidgets();
        }
    }

    private void requestWidgetRefresh() {
        widgetRefreshRequested = true;
    }

    private void refreshCharacterWidgets() {
        clearWidgets();
        if (professions.isEmpty()) return;

        int professionPageSize = professionsPerPage();
        int professionPageCount = Math.max(1, (professions.size() + professionPageSize - 1) / professionPageSize);
        professionPage = Math.max(0, Math.min(professionPage, professionPageCount - 1));
        int professionStart = professionPage * professionPageSize;
        int visibleProfessionCount = Math.min(professionPageSize, professions.size() - professionStart);
        if (visibleProfessionCount <= 0) return;

        int professionGap = 6;
        int professionWidth = Math.max(72, Math.min(148,
                (width - 72 - (visibleProfessionCount - 1) * professionGap) / visibleProfessionCount));
        int professionTotal = professionWidth * visibleProfessionCount + professionGap * (visibleProfessionCount - 1);
        int professionX = (width - professionTotal) / 2;
        for (int slot = 0; slot < visibleProfessionCount; slot++) {
            ProfessionOption option = professions.get(professionStart + slot);
            Component label = Component.literal("    ")
                    .append(option.id.equals(selectedProfessionId) ? Component.literal("✓ ") : Component.empty())
                    .append(Component.translatable(option.name));
            Button button = addRenderableWidget(Button.builder(label, ignored -> {
                selectedProfessionId = option.id;
                traitPage = 0;
                selectedTraits.removeIf(id -> {
                    TraitOption trait = traitById(id);
                    return option.grantedTraits.contains(id)
                            || (!option.allowedTraits.isEmpty() && !option.allowedTraits.contains(id))
                            || trait == null
                            || (!trait.requiresProfession.isBlank() && !trait.requiresProfession.equals(option.id));
                });
                resultMessageKey = "";
                requestWidgetRefresh();
            }).tooltip(Tooltip.create(Component.translatable(option.description)))
                    .bounds(professionX + slot * (professionWidth + professionGap), professionButtonsY(), professionWidth, 20).build());
            button.active = !waitingForServer;
        }
        if (professionPageCount > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), ignored -> {
                professionPage = Math.max(0, professionPage - 1);
                requestWidgetRefresh();
            }).bounds(10, 60, 20, 18).build()).active = !waitingForServer && professionPage > 0;
            addRenderableWidget(Button.builder(Component.literal(">"), ignored -> {
                professionPage = Math.min(professionPageCount - 1, professionPage + 1);
                requestWidgetRefresh();
            }).bounds(width - 30, 60, 20, 18).build()).active = !waitingForServer && professionPage < professionPageCount - 1;
        }

        ProfessionOption selectedProfession = profession();
        if (selectedProfession == null) return;
        List<TraitOption> visibleTraits = visibleTraitsFor(selectedProfession);
        int pageSize = traitsPerPage();
        int pageCount = Math.max(1, (visibleTraits.size() + pageSize - 1) / pageSize);
        traitPage = Math.max(0, Math.min(traitPage, pageCount - 1));
        if (pageCount > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), ignored -> {
                traitPage = Math.max(0, traitPage - 1);
                requestWidgetRefresh();
            }).bounds(width / 2 - 88, traitHeaderY() - 4, 20, 18).build()).active = !waitingForServer && traitPage > 0;
            addRenderableWidget(Button.builder(Component.literal(">"), ignored -> {
                traitPage = Math.min(pageCount - 1, traitPage + 1);
                requestWidgetRefresh();
            }).bounds(width / 2 + 68, traitHeaderY() - 4, 20, 18).build()).active = !waitingForServer && traitPage < pageCount - 1;
        }
        int rowHeight = 21;
        int traitWidth = Math.max(120, Math.min(340, width - 32));
        int traitX = (width - traitWidth) / 2;
        int y = traitRowsStartY();
        int pageStart = traitPage * pageSize;
        for (TraitOption trait : visibleTraits.subList(pageStart, Math.min(visibleTraits.size(), pageStart + pageSize))) {
            boolean granted = selectedProfession.grantedTraits.contains(trait.id);
            boolean selected = selectedTraits.contains(trait.id) || granted;
            int points = trait.category.equals("positive") ? -trait.pointCost : trait.pointCredit;
            String pointsLabel = (points > 0 ? "+" : "") + points;
            Component label;
            if (granted) {
                label = Component.literal("★ ")
                        .append(Component.translatable(trait.name))
                        .append(Component.literal("  ("))
                        .append(Component.translatable("screen.aftermath.character.trait.granted"))
                        .append(Component.literal(")"));
            } else {
                label = Component.literal(selected ? "✓ " : "○ ")
                        .append(Component.translatable(trait.name))
                        .append(Component.literal("  (" + pointsLabel + ")"));
            }
            Button button = addRenderableWidget(Button.builder(label, ignored -> {
                if (waitingForServer || granted) return;
                resultMessageKey = "";
                if (selectedTraits.contains(trait.id)) {
                    selectedTraits.remove(trait.id);
                } else {
                    Set<String> activeTraitIds = new LinkedHashSet<>(selectedTraits);
                    activeTraitIds.addAll(selectedProfession.grantedTraits);
                    boolean conflict = !java.util.Collections.disjoint(trait.conflicts, activeTraitIds)
                            || activeTraitIds.stream().map(this::traitById).filter(option -> option != null)
                            .anyMatch(option -> option.conflicts.contains(trait.id));
                    if (conflict) {
                        resultMessageKey = "screen.aftermath.character.error.conflicting_traits";
                    } else {
                        selectedTraits.add(trait.id);
                    }
                }
                requestWidgetRefresh();
            }).tooltip(Tooltip.create(Component.translatable(trait.description)))
                    .bounds(traitX, y, traitWidth, 20).build());
            button.active = !waitingForServer && !granted;
            y += rowHeight;
        }

        int remaining = remainingPoints();
        addRenderableWidget(Button.builder(
                Component.translatable("screen.aftermath.character.confirm"),
                ignored -> submit())
                .bounds((width - 180) / 2, height - 27, 180, 20)
                .build()).active = !waitingForServer && remaining >= 0 && selectedProfession != null;
        getChildButtons().forEach(button -> button.active = button.active && !waitingForServer);
    }

    private List<Button> getChildButtons() {
        return children().stream().filter(Button.class::isInstance).map(Button.class::cast).toList();
    }

    private void submit() {
        if (waitingForServer || profession() == null || remainingPoints() < 0) return;
        waitingForServer = true;
        resultMessageKey = "screen.aftermath.character.saving";
        requestWidgetRefresh();
        String traitsCsv = selectedTraits.stream().sorted().reduce((a, b) -> a + "," + b).orElse("");
        PacketDistributor.sendToServer(new SelectCharacterPayload(selectedProfessionId, traitsCsv));
    }

    private int professionsPerPage() {
        return Math.max(1, Math.min(4, (width - 88) / 160));
    }

    private int traitsPerPage() {
        int panelBottom = Math.max(traitRowsStartY() + 20, height - 60);
        int rowsThatFit = (panelBottom - traitRowsStartY() - 20) / 21 + 1;
        return Math.max(1, Math.min(5, rowsThatFit));
    }

    private int professionButtonsY() {
        return 59;
    }

    private List<FormattedCharSequence> selectedDescriptionLines() {
        ProfessionOption selected = profession();
        if (selected == null) return List.of();
        List<FormattedCharSequence> lines = font.split(
                Component.translatable(selected.description), Math.max(80, width - 36));
        int maxLines = height >= 280 ? 3 : 2;
        if (lines.isEmpty()) return List.of();
        return lines.subList(0, Math.min(lines.size(), maxLines));
    }

    private int startingSkillsY() {
        return 94 + Math.max(1, selectedDescriptionLines().size()) * 9 + 2;
    }

    private int traitsHeaderY() {
        return Math.max(119, startingSkillsY() + 11);
    }

    private int traitRowsStartY() {
        return traitsHeaderY() + 13;
    }

    private List<TraitOption> visibleTraitsFor(ProfessionOption selectedProfession) {
        return traits.stream()
                .filter(trait -> selectedProfession.allowedTraits.isEmpty()
                        || selectedProfession.allowedTraits.contains(trait.id))
                .filter(trait -> trait.requiresProfession.isBlank()
                        || trait.requiresProfession.equals(selectedProfession.id))
                .toList();
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

    private void drawProfessionIcons(GuiGraphics graphics) {
        if (professions.isEmpty()) return;
        int pageSize = professionsPerPage();
        int pageCount = Math.max(1, (professions.size() + pageSize - 1) / pageSize);
        int safePage = Math.max(0, Math.min(professionPage, pageCount - 1));
        int startIndex = safePage * pageSize;
        int visibleCount = Math.min(pageSize, professions.size() - startIndex);
        if (visibleCount <= 0) return;

        int gap = 6;
        int buttonWidth = Math.max(72, Math.min(148,
                (width - 72 - (visibleCount - 1) * gap) / visibleCount));
        int totalWidth = buttonWidth * visibleCount + gap * (visibleCount - 1);
        int startX = (width - totalWidth) / 2;
        for (int slot = 0; slot < visibleCount; slot++) {
            ProfessionOption profession = professions.get(startIndex + slot);
            ResourceLocation itemId = ResourceLocation.tryParse(profession.icon);
            if (itemId == null) continue;
            net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(Items.PLAYER_HEAD);
            if (item == Items.AIR) continue;
            graphics.renderItem(new ItemStack(item), startX + slot * (buttonWidth + gap) + 2, professionButtonsY() + 2);
        }
    }

    public void setResult(String messageKey) {
        waitingForServer = false;
        resultMessageKey = messageKey == null ? "" : messageKey;
        requestWidgetRefresh();
    }

    private void drawInterfacePanels(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, 0xB5000000);
        graphics.fill(6, 6, Math.max(6, width - 6), 7, 0xFF9A5435);
        graphics.fill(6, Math.max(7, height - 7), Math.max(6, width - 6), Math.max(8, height - 6), 0xFF553629);

        int panelWidth = Math.max(0, width - 20);
        drawPanel(graphics, 10, 9, panelWidth, 35);
        drawPanel(graphics, 10, 46, panelWidth, 40);
        drawPanel(graphics, 10, 88, panelWidth, Math.max(1, height - 148));
        drawPanel(graphics, 10, height - 57, panelWidth, 49);

        int dividerY = traitsHeaderY() - 8;
        if (dividerY > 88 && dividerY < height - 60) {
            graphics.fill(16, dividerY, Math.max(16, width - 16), dividerY + 1, 0xFF634630);
        }
    }

    private void drawPanel(GuiGraphics graphics, int x, int y, int panelWidth, int panelHeight) {
        if (panelWidth <= 0 || panelHeight <= 0) return;
        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xD512181A);
        graphics.fill(x, y, x + panelWidth, y + 1, 0xFF465153);
        graphics.fill(x, y + panelHeight - 1, x + panelWidth, y + panelHeight, 0xFF2C3538);
        graphics.fill(x, y, Math.min(x + 2, x + panelWidth), y + panelHeight, 0xFF925134);
        graphics.fill(x + panelWidth - 1, y, x + panelWidth, y + panelHeight, 0xFF273033);
    }

    private void drawOutline(GuiGraphics graphics, int x, int y, int outlineWidth, int outlineHeight, int color) {
        if (outlineWidth <= 0 || outlineHeight <= 0) return;
        int thickness = Math.min(2, Math.min(outlineWidth, outlineHeight));
        graphics.fill(x, y, x + outlineWidth, y + thickness, color);
        graphics.fill(x, y + outlineHeight - thickness, x + outlineWidth, y + outlineHeight, color);
        graphics.fill(x, y, x + thickness, y + outlineHeight, color);
        graphics.fill(x + outlineWidth - thickness, y, x + outlineWidth, y + outlineHeight, color);
    }

    private void drawSelectionAccents(GuiGraphics graphics) {
        if (professions.isEmpty()) return;
        int pageSize = professionsPerPage();
        int safePage = Math.max(0, Math.min(professionPage,
                Math.max(0, (professions.size() - 1) / pageSize)));
        int startIndex = safePage * pageSize;
        int visibleCount = Math.min(pageSize, professions.size() - startIndex);
        int gap = 6;
        int buttonWidth = Math.max(72, Math.min(148,
                (width - 72 - (visibleCount - 1) * gap) / visibleCount));
        int totalWidth = buttonWidth * visibleCount + gap * (visibleCount - 1);
        int startX = (width - totalWidth) / 2;
        int selectedIndex = professions.indexOf(profession());
        if (selectedIndex >= startIndex && selectedIndex < startIndex + visibleCount) {
            int slot = selectedIndex - startIndex;
            drawOutline(graphics, startX + slot * (buttonWidth + gap), professionButtonsY(),
                    buttonWidth, 20, 0xFFFF9A59);
        }

        ProfessionOption selectedProfession = profession();
        if (selectedProfession == null) return;
        List<TraitOption> availableTraits = visibleTraitsFor(selectedProfession);
        int traitPageSize = traitsPerPage();
        int pageCount = Math.max(1, (availableTraits.size() + traitPageSize - 1) / traitPageSize);
        int safeTraitPage = Math.max(0, Math.min(traitPage, pageCount - 1));
        int pageStart = safeTraitPage * traitPageSize;
        int visibleTraitsCount = Math.min(traitPageSize, availableTraits.size() - pageStart);
        int traitWidth = Math.max(120, Math.min(340, width - 32));
        int traitX = (width - traitWidth) / 2;
        for (int row = 0; row < visibleTraitsCount; row++) {
            TraitOption trait = availableTraits.get(pageStart + row);
            boolean granted = selectedProfession.grantedTraits.contains(trait.id);
            if (granted || selectedTraits.contains(trait.id)) {
                drawOutline(graphics, traitX, traitRowsStartY() + row * 21, traitWidth, 20,
                        granted ? 0xFF63A79D : 0xFFCA8752);
            }
        }
    }

    public void setResult(String messageKey) {
        waitingForServer = false;
        resultMessageKey = messageKey == null ? "" : messageKey;
        requestWidgetRefresh();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        drawInterfacePanels(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        drawSelectionAccents(graphics);
        drawProfessionIcons(graphics);

        graphics.drawCenteredString(font, Component.translatable("screen.aftermath.character.kicker"),
                width / 2, 10, 0xFFB87850);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFF0E5D8);
        graphics.drawCenteredString(font, Component.translatable("screen.aftermath.character.subtitle"),
                width / 2, 32, 0xFFB6B9B4);

        graphics.drawString(font, Component.translatable("screen.aftermath.character.profession"),
                16, 49, 0xFFD09A70);
        int professionPageSize = professionsPerPage();
        int professionPageCount = Math.max(1, (professions.size() + professionPageSize - 1) / professionPageSize);
        Component professionPageLabel = Component.translatable(
                "screen.aftermath.character.page", Math.min(professionPage + 1, professionPageCount), professionPageCount);
        graphics.drawString(font, professionPageLabel,
                width - 16 - font.width(professionPageLabel), 49, 0xFF8E9693);

        ProfessionOption selected = profession();
        if (selected != null) {
            List<FormattedCharSequence> descriptionLines = selectedDescriptionLines();
            if (descriptionLines.isEmpty()) {
                graphics.drawCenteredString(font, Component.translatable(selected.description),
                        width / 2, 94, 0xFFD0D0C5);
            } else {
                for (int i = 0; i < descriptionLines.size(); i++) {
                    graphics.drawCenteredString(font, descriptionLines.get(i),
                            width / 2, 94 + i * 9, 0xFFD0D0C5);
                }
            }

            if (selected.skillBonuses.isEmpty()) {
                graphics.drawCenteredString(font, Component.translatable("screen.aftermath.character.starting_skills_none"),
                        width / 2, startingSkillsY(), 0xFF9EA9A4);
            } else {
                MutableComponent skills = Component.translatable("screen.aftermath.character.starting_skills");
                boolean first = true;
                for (Map.Entry<String, Integer> skill : selected.skillBonuses.entrySet()) {
                    skills.append(Component.literal(first ? " " : " · "))
                            .append(Component.translatable("skill.aftermath." + skill.getKey()))
                            .append(Component.translatable("screen.aftermath.character.skill_level", skill.getValue()));
                    first = false;
                }
                graphics.drawCenteredString(font, skills, width / 2, startingSkillsY(), 0xFFA9D8E5);
            }

            graphics.drawCenteredString(font, Component.translatable("screen.aftermath.character.traits"),
                    width / 2, traitsHeaderY(), 0xFFF0E5D8);
            List<TraitOption> availableTraits = visibleTraitsFor(selected);
            int traitPageSize = traitsPerPage();
            int traitPageCount = Math.max(1, (availableTraits.size() + traitPageSize - 1) / traitPageSize);
            Component traitPageLabel = Component.translatable(
                    "screen.aftermath.character.page", Math.min(traitPage + 1, traitPageCount), traitPageCount);
            graphics.drawString(font, traitPageLabel,
                    width - 16 - font.width(traitPageLabel), traitsHeaderY() + 1, 0xFF8E9693);
        }

        int points = remainingPoints();
        int colour = points < 0 ? 0xFFFF6E63 : points == 0 ? 0xFFE0B45F : 0xFF8FD19E;
        graphics.drawCenteredString(font, Component.translatable(
                "screen.aftermath.character.points", points, selected == null ? 0 : selected.startingPoints),
                width / 2, height - 49, colour);
        if (!resultMessageKey.isBlank()) {
            int messageColour = resultMessageKey.contains(".error.") ? 0xFFFF7770 : 0xFFE9CF84;
            graphics.drawCenteredString(font, Component.translatable(resultMessageKey),
                    width / 2, height - 38, messageColour);
        }
    }

    @Override
    public void onClose() {
        // First-join setup cannot be skipped by closing the screen.
    }

    private record ProfessionOption(String id, String name, String description, String icon,
                                    int startingPoints, Set<String> allowedTraits,
                                    Set<String> grantedTraits, Map<String, Integer> skillBonuses) {}
    private record TraitOption(String id, String name, String description, String category,
                               int pointCost, int pointCredit, Set<String> conflicts,
                               String requiresProfession) {}
}
