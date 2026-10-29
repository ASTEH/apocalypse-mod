package dev.aftermath.client.gui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.aftermath.network.payload.CharacterSelectionRequestPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Lightweight first-join profile screen; the server remains authoritative. */
public final class CharacterSelectionScreen extends Screen {
    private static final Gson GSON = new GsonBuilder().create();

    private final CatalogView catalog;
    private final Map<String, Button> professionButtons = new LinkedHashMap<>();
    private final Map<String, Button> traitButtons = new LinkedHashMap<>();
    private final Set<String> selectedTraitIds = new LinkedHashSet<>();

    private String selectedProfessionId;
    private Button confirmButton;
    private Component statusMessage = Component.empty();
    private Component serverErrorMessage;
    private boolean submitting;
    private int panelLeft;
    private int panelWidth;
    private int traitColumnLeft;
    private static final int MAX_VISIBLE_PROFESSIONS = 6;
    private static final int MAX_VISIBLE_TRAITS = 8;

    public CharacterSelectionScreen(String catalogJson) {
        super(Component.translatable("screen.aftermath.character.title"));
        CatalogView parsed = GSON.fromJson(catalogJson, CatalogView.class);
        if (parsed == null || parsed.professions() == null || parsed.traits() == null
                || parsed.professions().isEmpty() || parsed.traits().isEmpty()) {
            throw new IllegalArgumentException("Character catalog is empty or malformed.");
        }
        this.catalog = new CatalogView(List.copyOf(parsed.professions()), List.copyOf(parsed.traits()));
        this.selectedProfessionId = this.catalog.professions().get(0).id();
    }

    @Override
    protected void init() {
        super.init();
        professionButtons.clear();
        traitButtons.clear();

        panelWidth = Math.max(260, Math.min(580, this.width - 20));
        panelLeft = (this.width - panelWidth) / 2;
        int innerWidth = panelWidth - 20;
        int professionColumnWidth = Math.max(110, (innerWidth - 10) * 4 / 10);
        traitColumnLeft = panelLeft + 10 + professionColumnWidth + 10;
        int traitColumnWidth = panelWidth - 20 - professionColumnWidth - 10;

        int rowY = 60;
        int visibleProfessions = Math.min(MAX_VISIBLE_PROFESSIONS, catalog.professions().size());
        for (int i = 0; i < visibleProfessions; i++) {
            ProfessionOption profession = catalog.professions().get(i);
            Button button = Button.builder(Component.empty(), ignored -> selectProfession(profession.id()))
                    .bounds(panelLeft + 10, rowY + i * 26, professionColumnWidth, 22)
                    .tooltip(Tooltip.create(Component.translatable(profession.descriptionKey())))
                    .build();
            professionButtons.put(profession.id(), addRenderableWidget(button));
        }

        int visibleTraits = Math.min(MAX_VISIBLE_TRAITS, catalog.traits().size());
        for (int i = 0; i < visibleTraits; i++) {
            TraitOption trait = catalog.traits().get(i);
            Button button = Button.builder(Component.empty(), ignored -> toggleTrait(trait.id()))
                    .bounds(traitColumnLeft, rowY + i * 26, traitColumnWidth, 22)
                    .tooltip(Tooltip.create(Component.translatable(trait.descriptionKey())))
                    .build();
            traitButtons.put(trait.id(), addRenderableWidget(button));
        }

        confirmButton = addRenderableWidget(Button.builder(
                        Component.translatable("message.aftermath.character.ui.confirm"),
                        ignored -> submitSelection())
                .bounds(this.width / 2 - 100, this.height - 34, 200, 20)
                .build());

        refreshUi();
    }

    private void selectProfession(String id) {
        if (submitting) {
            return;
        }
        selectedProfessionId = id;
        serverErrorMessage = null;
        ProfessionOption profession = selectedProfession();
        if (profession != null && !profession.allowedTraits().isEmpty()) {
            selectedTraitIds.removeIf(traitId -> !profession.allowedTraits().contains(traitId));
        }
        refreshUi();
    }

    private void toggleTrait(String id) {
        if (submitting) {
            return;
        }
        if (!selectedTraitIds.remove(id)) {
            selectedTraitIds.add(id);
        }
        serverErrorMessage = null;
        refreshUi();
    }

    private void submitSelection() {
        if (submitting || !localSelectionValid()) {
            return;
        }
        submitting = true;
        serverErrorMessage = null;
        refreshUi();
        PacketDistributor.sendToServer(new CharacterSelectionRequestPayload(
                selectedProfessionId, String.join(",", selectedTraitIds)));
    }

    private void refreshUi() {
        ProfessionOption profession = selectedProfession();
        professionButtons.forEach((id, button) -> {
            ProfessionOption option = findProfession(id);
            boolean selected = id.equals(selectedProfessionId);
            button.setMessage(Component.literal(selected ? "✓ " : "  ")
                    .append(Component.translatable(option.nameKey())));
            button.active = !submitting;
        });

        for (TraitOption trait : catalog.traits()) {
            Button button = traitButtons.get(trait.id());
            if (button == null) {
                continue;
            }
            boolean selected = selectedTraitIds.contains(trait.id());
            String points = trait.category().equals("positive")
                    ? " (-" + trait.pointCost() + ")"
                    : " (+" + trait.pointCredit() + ")";
            button.setMessage(Component.literal(selected ? "☑ " : "☐ ")
                    .append(Component.translatable(trait.nameKey()))
                    .append(Component.literal(points)));
            boolean allowed = profession == null || profession.allowedTraits().isEmpty()
                    || profession.allowedTraits().contains(trait.id());
            button.active = !submitting && (selected || allowed);
        }

        if (confirmButton != null) {
            confirmButton.active = !submitting && localSelectionValid();
            confirmButton.setMessage(Component.translatable(submitting
                    ? "message.aftermath.character.ui.confirm_wait"
                    : "message.aftermath.character.ui.confirm"));
        }

        statusMessage = submitting
                ? Component.translatable("message.aftermath.character.ui.saving_status")
                : serverErrorMessage != null ? serverErrorMessage : validationMessage();
    }

    private boolean localSelectionValid() {
        return validateSelection().valid();
    }

    private Validation validateSelection() {
        ProfessionOption profession = selectedProfession();
        if (profession == null) {
            return Validation.error("select_profession", null, null, 0, 0);
        }

        List<TraitOption> selected = new ArrayList<>();
        int cost = 0;
        int credit = 0;
        for (String id : selectedTraitIds) {
            TraitOption trait = findTrait(id);
            if (trait == null) {
                return Validation.error("unknown_trait", id, null, 0, 0);
            }
            if (!profession.allowedTraits().isEmpty() && !profession.allowedTraits().contains(id)) {
                return Validation.error("trait_not_allowed", id, profession.id(), 0, 0);
            }
            for (TraitOption alreadySelected : selected) {
                if (trait.conflicts().contains(alreadySelected.id())
                        || alreadySelected.conflicts().contains(trait.id())) {
                    return Validation.error("trait_conflict", alreadySelected.id(), trait.id(), 0, 0);
                }
            }
            selected.add(trait);
            cost += trait.pointCost();
            credit += trait.pointCredit();
        }

        int available = profession.startingPoints() + credit;
        if (cost > available) {
            return Validation.error("insufficient_points", null, null, cost, available);
        }
        return Validation.valid(available - cost);
    }

    private Component validationMessage() {
        Validation validation = validateSelection();
        if (validation.valid()) {
            return Component.translatable("message.aftermath.character.ui.points_remaining",
                    validation.remainingPoints());
        }

        return switch (validation.errorCode()) {
            case "trait_not_allowed", "trait_conflict" -> Component.translatable(
                    "message.aftermath.character.error." + validation.errorCode(),
                    safe(validation.argumentOne()), safe(validation.argumentTwo()));
            case "insufficient_points" -> Component.translatable(
                    "message.aftermath.character.error.insufficient_points",
                    validation.requiredPoints(), validation.availablePoints());
            case "unknown_trait" -> Component.translatable(
                    "message.aftermath.character.error.unknown_trait", safe(validation.argumentOne()));
            default -> Component.translatable("message.aftermath.character.ui.select_profession");
        };
    }

    public void handleServerResult(String resultJson) {
        SelectionResponse response;
        try {
            response = GSON.fromJson(resultJson, SelectionResponse.class);
        } catch (RuntimeException exception) {
            response = null;
        }
        submitting = false;

        if (response == null) {
            serverErrorMessage = Component.translatable("message.aftermath.character.ui.invalid_response");
            refreshUi();
            return;
        }

        if (response.accepted()) {
            Minecraft.getInstance().setScreen(null);
            return;
        }

        serverErrorMessage = selectionError(response);
        refreshUi();
    }

    private Component selectionError(SelectionResponse response) {
        String errorCode = safe(response.errorCode());
        String key = "message.aftermath.character.error." + errorCode;
        return switch (errorCode) {
            case "unknown_profession", "unknown_trait", "duplicate_trait" ->
                    Component.translatable(key, safe(response.argumentOne()));
            case "trait_not_allowed", "trait_conflict" ->
                    Component.translatable(key, safe(response.argumentOne()), safe(response.argumentTwo()));
            case "insufficient_points" ->
                    Component.translatable(key, response.requiredPoints(), response.availablePoints());
            default -> Component.translatable(key);
        };
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private ProfessionOption selectedProfession() {
        return findProfession(selectedProfessionId);
    }

    private ProfessionOption findProfession(String id) {
        if (id == null) {
            return null;
        }
        return catalog.professions().stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
    }

    private TraitOption findTrait(String id) {
        return catalog.traits().stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.fill(panelLeft - 4, 28, panelLeft + panelWidth + 4, height - 8, 0xD010151D);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("message.aftermath.character.ui.professions"),
                panelLeft + 10, 43, 0xC8D8F0);
        graphics.drawString(font, Component.translatable("message.aftermath.character.ui.traits"),
                traitColumnLeft, 43, 0xC8D8F0);
        graphics.drawCenteredString(font, statusMessage, width / 2, height - 52,
                localSelectionValid() && serverErrorMessage == null ? 0xE8E8E8 : 0xFF9A80);

        if (catalog.professions().size() > MAX_VISIBLE_PROFESSIONS
                || catalog.traits().size() > MAX_VISIBLE_TRAITS) {
            graphics.drawCenteredString(font,
                    Component.translatable("message.aftermath.character.ui.too_many_definitions"),
                    width / 2, height - 65, 0xFFFF80);
        }
    }

    public record CatalogView(List<ProfessionOption> professions, List<TraitOption> traits) {}
    public record ProfessionOption(
            String id, String nameKey, String descriptionKey, int startingPoints, List<String> allowedTraits
    ) {}
    public record TraitOption(
            String id, String nameKey, String descriptionKey, String category,
            int pointCost, int pointCredit, List<String> conflicts
    ) {}
    private record SelectionResponse(
            boolean accepted, String errorCode, String argumentOne, String argumentTwo,
            int requiredPoints, int availablePoints, int remainingPoints,
            String professionId, String traitIdsCsv
    ) {}
    private record Validation(
            boolean valid, String errorCode, String argumentOne, String argumentTwo,
            int requiredPoints, int availablePoints, int remainingPoints
    ) {
        private static Validation valid(int remaining) {
            return new Validation(true, "", "", "", 0, 0, remaining);
        }
        private static Validation error(String code, String arg1, String arg2, int required, int available) {
            return new Validation(false, code, arg1, arg2, required, available, 0);
        }
    }
}
