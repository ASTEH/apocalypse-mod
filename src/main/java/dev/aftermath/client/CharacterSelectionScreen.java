package dev.aftermath.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aftermath.network.SelectCharacterPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
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

    private void refreshCharacterWidgets() {
        clearWidgets();
        if (professions.isEmpty()) return;

        int visibleProfessionCount = Math.min(professionsPerPage(), professions.size());
        int professionGap = 6;
        int professionWidth = Math.max(72, Math.min(148,
                (width - 72 - (visibleProfessionCount - 1) * professionGap) / Math.max(1, visibleProfessionCount)));
        int professionTotal = professionWidth * visibleProfessionCount + professionGap * (visibleProfessionCount - 1);
        int professionX = (width - professionTotal) / 2;
        int professionPageCount = Math.max(1, (professions.size() + professionsPerPage() - 1) / professionsPerPage());
        professionPage = Math.max(0, Math.min(professionPage, professionPageCount - 1));
        int professionStart = professionPage * professionsPerPage();
        for (int slot = 0; slot < visibleProfessionCount; slot++) {
            ProfessionOption option = professions.get(professionStart + slot);
            Component label = Component.literal("   ")
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
                refreshCharacterWidgets();
            }).bounds(professionX + slot * (professionWidth + professionGap), 59, professionWidth, 20).build());
            button.active = !waitingForServer;
        }
        if (professionPageCount > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), ignored -> {
                professionPage = Math.max(0, professionPage - 1);
                refreshCharacterWidgets();
            }).bounds(8, 60, 20, 18).build()).active = !waitingForServer && professionPage > 0;
            addRenderableWidget(Button.builder(Component.literal(">"), ignored -> {
                professionPage = Math.min(professionPageCount - 1, professionPage + 1);
                refreshCharacterWidgets();
            }).bounds(width - 28, 60, 20, 18).build()).active = !waitingForServer && professionPage < professionPageCount - 1;
        }

        ProfessionOption selectedProfession = profession();
        if (selectedProfession == null) return;
        List<TraitOption> visibleTraits = traits.stream()
                .filter(trait -> selectedProfession.allowedTraits.isEmpty() || selectedProfession.allowedTraits.contains(trait.id))
                .filter(trait -> trait.requiresProfession.isBlank() || trait.requiresProfession.equals(selectedProfession.id))
                .toList();
        int pageSize = traitsPerPage();
        int pageCount = Math.max(1, (visibleTraits.size() + pageSize - 1) / pageSize);
        traitPage = Math.max(0, Math.min(traitPage, pageCount - 1));
        if (pageCount > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), ignored -> {
                traitPage = Math.max(0, traitPage - 1);
                refreshCharacterWidgets();
            }).bounds(width / 2 - 88, 112, 20, 18).build()).active = !waitingForServer && traitPage > 0;
            addRenderableWidget(Button.builder(Component.literal(">"), ignored -> {
                traitPage = Math.min(pageCount - 1, traitPage + 1);
                refreshCharacterWidgets();
            }).bounds(width / 2 + 68, 112, 20, 18).build()).active = !waitingForServer && traitPage < pageCount - 1;
        }
        int rowHeight = 21;
        int traitWidth = Math.max(160, Math.min(340, width - 36));
        int traitX = (width - traitWidth) / 2;
        int y = 132;
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
                refreshCharacterWidgets();
            }).bounds(traitX, y, traitWidth, 20).build());
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
        refreshCharacterWidgets();
        String traitsCsv = selectedTraits.stream().sorted().reduce((a, b) -> a + "," + b).orElse("");
        PacketDistributor.sendToServer(new SelectCharacterPayload(selectedProfessionId, traitsCsv));
    }

    private int professionsPerPage() {
        return Math.max(1, Math.min(4, (width - 88) / 160));
    }

    private int traitsPerPage() {
        return Math.max(1, Math.min(5, (height - 218) / 21));
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
        int visibleCount = Math.min(pageSize, professions.size());
        int gap = 6;
        int buttonWidth = Math.max(72, Math.min(148,
                (width - 72 - (visibleCount - 1) * gap) / Math.max(1, visibleCount)));
        int totalWidth = buttonWidth * visibleCount + gap * (visibleCount - 1);
        int startX = (width - totalWidth) / 2;
        int startIndex = professionPage * pageSize;
        for (int slot = 0; slot < visibleCount; slot++) {
            ProfessionOption profession = professions.get(startIndex + slot);
            ResourceLocation itemId = ResourceLocation.tryParse(profession.icon);
            if (itemId == null) continue;
            net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(Items.PLAYER_HEAD);
            if (item == Items.AIR) continue;
            graphics.renderItem(new ItemStack(item), startX + slot * (buttonWidth + gap) + 2, 61);
        }
    }

    public void setResult(String messageKey) {
        waitingForServer = false;
        resultMessageKey = messageKey == null ? "" : messageKey;
        refreshCharacterWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        drawProfessionIcons(graphics);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.aftermath.character.subtitle"),
                width / 2, 39, 0xD0D0D0);
        graphics.drawCenteredString(font, Component.translatable("screen.aftermath.character.profession"),
                width / 2, 47, 0xFFFFFF);

        ProfessionOption selected = profession();
        if (selected != null) {
            graphics.drawCenteredString(font, Component.translatable(selected.description),
                    width / 2, 83, 0xC8C8C8);
            if (selected.skillBonuses.isEmpty()) {
                graphics.drawCenteredString(font, Component.translatable("screen.aftermath.character.starting_skills_none"),
                        width / 2, 94, 0xA0A0A0);
            } else {
                MutableComponent skills = Component.translatable("screen.aftermath.character.starting_skills");
                boolean first = true;
                for (Map.Entry<String, Integer> skill : selected.skillBonuses.entrySet()) {
                    skills.append(Component.literal(first ? " " : " · "))
                            .append(Component.translatable("skill.aftermath." + skill.getKey()))
                            .append(Component.translatable("screen.aftermath.character.skill_level", skill.getValue()));
                    first = false;
                }
                graphics.drawCenteredString(font, skills, width / 2, 94, 0xA0D8FF);
            }
            graphics.drawCenteredString(font, Component.translatable("screen.aftermath.character.traits"),
                    width / 2, 119, 0xFFFFFF);
        }
        int points = remainingPoints();
        int colour = points < 0 ? 0xFF5555 : 0x55FF55;
        graphics.drawCenteredString(font, Component.translatable(
                "screen.aftermath.character.points", points, selected == null ? 0 : selected.startingPoints),
                width / 2, height - 49, colour);
        if (!resultMessageKey.isBlank()) {
            int messageColour = resultMessageKey.contains(".error.") ? 0xFF7777 : 0xFFFF55;
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
