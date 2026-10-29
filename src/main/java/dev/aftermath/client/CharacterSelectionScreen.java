package dev.aftermath.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aftermath.network.SelectCharacterPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** First-join profession and trait selection. The server revalidates every submitted choice. */
public final class CharacterSelectionScreen extends Screen {
    private final List<ProfessionOption> professions = new ArrayList<>();
    private final List<TraitOption> traits = new ArrayList<>();
    private final Set<String> selectedTraits = new LinkedHashSet<>();
    private String selectedProfessionId = "";
    private String resultMessageKey = "";
    private boolean waitingForServer;

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
                    professions.add(new ProfessionOption(
                            json.get("id").getAsString(),
                            json.get("name").getAsString(),
                            json.get("description").getAsString(),
                            json.get("starting_points").getAsInt(),
                            Set.copyOf(allowed)));
                }
            }

            JsonArray traitArray = root.getAsJsonArray("traits");
            if (traitArray != null) {
                for (JsonElement element : traitArray) {
                    JsonObject json = element.getAsJsonObject();
                    Set<String> conflicts = new LinkedHashSet<>();
                    JsonArray conflictArray = json.getAsJsonArray("conflicts");
                    if (conflictArray != null) conflictArray.forEach(id -> conflicts.add(id.getAsString()));
                    traits.add(new TraitOption(
                            json.get("id").getAsString(),
                            json.get("name").getAsString(),
                            json.get("description").getAsString(),
                            json.get("category").getAsString(),
                            json.get("point_cost").getAsInt(),
                            json.get("point_credit").getAsInt(),
                            Set.copyOf(conflicts)));
                }
            }
        } catch (RuntimeException exception) {
            professions.clear();
            traits.clear();
        }
    }

    @Override
    protected void init() {
        refreshCharacterWidgets();
    }

    private void refreshCharacterWidgets() {
        clearWidgets();
        if (professions.isEmpty()) return;

        int professionWidth = Math.min(112, (width - 32 - (professions.size() - 1) * 5) / professions.size());
        int professionGap = 5;
        int professionTotal = professionWidth * professions.size() + professionGap * (professions.size() - 1);
        int professionX = (width - professionTotal) / 2;
        for (int i = 0; i < professions.size(); i++) {
            ProfessionOption profession = professions.get(i);
            Component label = Component.translatable(profession.name);
            if (profession.id.equals(selectedProfessionId)) {
                label = Component.literal("✓ ").append(label);
            }
            Button button = addRenderableWidget(Button.builder(label, ignored -> {
                selectedProfessionId = profession.id;
                selectedTraits.removeIf(id -> !profession().allowedTraits.contains(id));
                resultMessageKey = "";
                refreshCharacterWidgets();
            }).bounds(professionX + i * (professionWidth + professionGap), 59, professionWidth, 20).build());
            button.active = !waitingForServer;
        }

        ProfessionOption selectedProfession = profession();
        if (selectedProfession == null) return;
        List<TraitOption> visibleTraits = traits.stream()
                .filter(trait -> selectedProfession.allowedTraits.contains(trait.id))
                .toList();
        int rowHeight = 21;
        int traitWidth = Math.max(160, Math.min(340, width - 36));
        int traitX = (width - traitWidth) / 2;
        int y = 112;
        for (TraitOption trait : visibleTraits) {
            boolean selected = selectedTraits.contains(trait.id);
            int points = trait.category.equals("positive") ? -trait.pointCost : trait.pointCredit;
            String pointsLabel = (points > 0 ? "+" : "") + points;
            Component label = Component.literal(selected ? "✓ " : "○ ")
                    .append(Component.translatable(trait.name))
                    .append(Component.literal("  (" + pointsLabel + ")"));
            Button button = addRenderableWidget(Button.builder(label, ignored -> {
                if (waitingForServer) return;
                resultMessageKey = "";
                if (selectedTraits.contains(trait.id)) {
                    selectedTraits.remove(trait.id);
                } else {
                    boolean conflict = !java.util.Collections.disjoint(trait.conflicts, selectedTraits)
                            || selectedTraits.stream().map(this::traitById).filter(option -> option != null)
                            .anyMatch(option -> option.conflicts.contains(trait.id));
                    if (conflict) {
                        resultMessageKey = "screen.aftermath.character.error.conflicting_traits";
                    } else {
                        selectedTraits.add(trait.id);
                    }
                }
                refreshCharacterWidgets();
            }).bounds(traitX, y, traitWidth, 20).build());
            button.active = !waitingForServer;
            y += rowHeight;
        }

        int remaining = remainingPoints();
        int pointsY = y + 3;
        addRenderableWidget(Button.builder(
                Component.translatable("screen.aftermath.character.confirm"),
                ignored -> submit())
                .bounds((width - 180) / 2, pointsY + 43, 180, 20)
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

    private int remainingPoints() {
        ProfessionOption selected = profession();
        if (selected == null) return Integer.MIN_VALUE;
        int remaining = selected.startingPoints;
        for (String id : selectedTraits) {
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

    public void setResult(String messageKey) {
        waitingForServer = false;
        resultMessageKey = messageKey == null ? "" : messageKey;
        refreshCharacterWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        graphics.drawCenteredString(font, Component.translatable("screen.aftermath.character.subtitle"),
                width / 2, 39, 0xD0D0D0);
        graphics.drawCenteredString(font, Component.translatable("screen.aftermath.character.profession"),
                width / 2, 47, 0xFFFFFF);

        ProfessionOption selected = profession();
        int y = 83;
        if (selected != null) {
            graphics.drawCenteredString(font, Component.translatable(selected.description),
                    width / 2, y, 0xC8C8C8);
            graphics.drawCenteredString(font, Component.translatable("screen.aftermath.character.traits"),
                    width / 2, 98, 0xFFFFFF);
        }
        int pointsY = 112 + (selected == null ? 0 : (int) traits.stream()
                .filter(trait -> selected.allowedTraits.contains(trait.id)).count() * 21) + 4;
        int points = remainingPoints();
        int colour = points < 0 ? 0xFF5555 : 0x55FF55;
        graphics.drawCenteredString(font, Component.translatable(
                "screen.aftermath.character.points", points, selected == null ? 0 : selected.startingPoints),
                width / 2, pointsY, colour);
        if (!resultMessageKey.isBlank()) {
            int messageColour = resultMessageKey.contains(".error.") ? 0xFF7777 : 0xFFFF55;
            graphics.drawCenteredString(font, Component.translatable(resultMessageKey),
                    width / 2, pointsY + 20, messageColour);
        }
    }

    @Override
    public void onClose() {
        // First-join setup cannot be skipped by closing the screen.
    }

    private record ProfessionOption(String id, String name, String description,
                                    int startingPoints, Set<String> allowedTraits) {}
    private record TraitOption(String id, String name, String description, String category,
                               int pointCost, int pointCredit, Set<String> conflicts) {}
}
