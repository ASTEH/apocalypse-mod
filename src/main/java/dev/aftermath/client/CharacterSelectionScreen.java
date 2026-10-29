package dev.aftermath.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aftermath.network.SelectCharacterPayload;
import io.github.ssbaxys.lumina.geom.Insets;
import io.github.ssbaxys.lumina.layout.Align;
import io.github.ssbaxys.lumina.layout.Dim;
import io.github.ssbaxys.lumina.layout.StackLayout;
import io.github.ssbaxys.lumina.neoforge.LuminaScreen;
import io.github.ssbaxys.lumina.node.UiRoot;
import io.github.ssbaxys.lumina.render.Brush;
import io.github.ssbaxys.lumina.render.Radii;
import io.github.ssbaxys.lumina.style.ModernTheme;
import io.github.ssbaxys.lumina.style.Styles;
import io.github.ssbaxys.lumina.style.Theme;
import io.github.ssbaxys.lumina.style.Tokens;
import io.github.ssbaxys.lumina.widget.Button;
import io.github.ssbaxys.lumina.widget.Label;
import io.github.ssbaxys.lumina.widget.Panel;
import io.github.ssbaxys.lumina.widget.ScrollPanel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
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
import java.util.Collections;

/**
 * First-join character creation built with Lumina UI.
 *
 * Keeps the existing server-authoritative protocol and validation; Lumina only owns the
 * client layout, scrollable lists, styling and input widgets.
 */
public final class CharacterSelectionScreen extends LuminaScreen {
    private static final int ACCENT = 0xFFD59A6A;
    private static final int TEXT = 0xFFE7E2D8;
    private static final int MUTED = 0xFFACB2AD;
    private static final int GREEN = 0xFF9DD2B4;
    private static final int RED = 0xFFE88779;
    private static final int PANEL = 0xEC191C1D;
    private static final int PANEL_ALT = 0xF0222627;
    private static final int ROW = 0xE72A2E2F;
    private static final int ROW_SELECTED = 0xF0443428;
    private static final int BORDER = 0xFF454B4B;

    private final SelectionState state;

    public CharacterSelectionScreen(String definitionsJson) {
        this(new SelectionState(definitionsJson));
    }

    private CharacterSelectionScreen(SelectionState state) {
        super(Component.translatable("screen.aftermath.character.title"), screen -> state.buildRoot());
        this.state = state;
    }

    /** Character creation is mandatory on first join, so Escape cannot bypass it. */
    @Override
    public void onClose() {
        // Deliberately do not close this screen before the server accepts the character.
    }

    /** Keep the live world visible; the UI panels provide contrast without Minecraft's screen dim/blur. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Intentionally empty. LuminaScreen renders after the world without the vanilla fade.
    }

    public void setResult(String messageKey) {
        state.setResult(messageKey);
    }

    private static final class SelectionState {
        private final List<ProfessionOption> professions = new ArrayList<>();
        private final List<TraitOption> traits = new ArrayList<>();
        private final Set<String> selectedTraits = new LinkedHashSet<>();
        private final Map<String, Button> professionButtons = new LinkedHashMap<>();
        private final Map<String, Button> traitButtons = new LinkedHashMap<>();

        private String selectedProfessionId = "aftermath:medic";
        private String resultMessageKey = "";
        private boolean waitingForServer;

        private Panel professionRows;
        private Panel traitRows;
        private Panel summaryRows;
        private ScrollPanel traitScroll;
        private Label pointsLabel;
        private Label statusLabel;
        private Button confirmButton;
        private Theme theme;

        private SelectionState(String definitionsJson) {
            readDefinitions(definitionsJson);
            professions.sort(Comparator.comparing(ProfessionOption::id));
            traits.sort(Comparator.comparing(TraitOption::id));
            if (professions.stream().noneMatch(p -> p.id().equals(selectedProfessionId)) && !professions.isEmpty()) {
                selectedProfessionId = professions.get(0).id();
            }
        }

        private UiRoot buildRoot() {
            theme = ModernTheme.dark(ACCENT);
            theme.set(Tokens.TEXT, TEXT);
            theme.set(Tokens.SURFACE, PANEL);
            theme.set(Tokens.SURFACE_ALT, PANEL_ALT);
            theme.set(Tokens.WELL, 0xA60D1010);
            theme.set(Tokens.BORDER, 0x60454B4B);
            theme.set(Tokens.CORNER, 4f);
            theme.set(Tokens.MOTION, 0.12f);

            Label kicker = label(tr("screen.aftermath.character.kicker"), ACCENT, 0.78f);
            Label title = label(tr("screen.aftermath.character.title"), TEXT, 1.45f);
            Label subtitle = label(tr("screen.aftermath.character.subtitle"), MUTED, 0.88f);
            Panel heading = Panel.column().gap(3).width(Dim.FILL).grow(1f).add(kicker, title, subtitle);
            Label fileStatus = label("SURVIVOR FILE  /  FIRST CONTACT", MUTED, 0.76f);
            fileStatus.style(s -> s.set(Styles.TEXT_COLOR, ACCENT));
            Panel header = surface(Panel.row().gap(12).align(Align.CENTER).width(Dim.FILL).height(Dim.px(54))
                    .add(heading, fileStatus), PANEL_ALT, 0xFF484746, 3f, 10f);

            professionRows = Panel.column().gap(4).width(Dim.FILL);
            ScrollPanel professionsScroll = scroll(new ScrollPanel(professionRows)
                    .width(Dim.FILL).height(Dim.FILL).grow(1f));
            Label professionHeading = label(tr("screen.aftermath.character.profession"), ACCENT, 1.03f);
            Label professionHint = label(tr("screen.aftermath.character.profession_hint"), MUTED, 0.8f);
            Panel professionColumn = surface(Panel.column().gap(7).width(Dim.FILL).height(Dim.FILL).grow(1.0f)
                    .add(professionHeading, professionHint, professionsScroll), PANEL, BORDER, 3f, 8f);

            traitRows = Panel.column().gap(4).width(Dim.FILL);
            traitScroll = scroll(new ScrollPanel(traitRows).width(Dim.FILL).height(Dim.FILL).grow(1f));
            Label traitHeading = label(tr("screen.aftermath.character.available_traits"), ACCENT, 1.03f);
            Label traitHint = label(tr("screen.aftermath.character.trait_hint"), MUTED, 0.8f);
            Panel traitsColumn = surface(Panel.column().gap(7).width(Dim.FILL).height(Dim.FILL).grow(1.6f)
                    .add(traitHeading, traitHint, traitScroll), PANEL, BORDER, 3f, 8f);

            summaryRows = Panel.column().gap(6).width(Dim.FILL);
            ScrollPanel summaryScroll = scroll(new ScrollPanel(summaryRows)
                    .width(Dim.FILL).height(Dim.FILL).grow(1f));
            Label summaryHeading = label(tr("screen.aftermath.character.summary"), ACCENT, 1.03f);
            Label summaryHint = label(tr("screen.aftermath.character.summary_hint"), MUTED, 0.8f);
            Panel summaryColumn = surface(Panel.column().gap(7).width(Dim.FILL).height(Dim.FILL).grow(1.15f)
                    .add(summaryHeading, summaryHint, summaryScroll), PANEL, BORDER, 3f, 8f);

            Panel body = Panel.row().gap(8).width(Dim.FILL).height(Dim.FILL).grow(1f)
                    .add(professionColumn, traitsColumn, summaryColumn);

            pointsLabel = label("", GREEN, 1.0f);
            statusLabel = label("", MUTED, 0.82f);
            Panel footerText = Panel.column().gap(4).width(Dim.FILL).grow(1f).add(pointsLabel, statusLabel);
            confirmButton = new Button(tr("screen.aftermath.character.confirm"), this::submit)
                    .width(Dim.px(174)).height(Dim.px(38)).styleClass("button primary");
            applyButtonStyle(confirmButton, true, false);
            Panel footer = surface(Panel.row().gap(12).align(Align.CENTER).width(Dim.FILL).height(Dim.px(58))
                    .add(footerText, confirmButton), PANEL_ALT, 0xFF484746, 3f, 10f);

            Panel shell = surface(Panel.column().gap(9).width(Dim.percent(0.96f)).height(Dim.percent(0.94f))
                    .add(header, body, footer), 0xE916191A, 0xFF3C4242, 4f, 10f);
            Panel centered = Panel.box(StackLayout.centered()).fill().add(shell);

            refreshProfessionRows();
            refreshTraitRows();
            refreshSummary();
            refreshFooter();
            return new UiRoot(centered, theme);
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
            } catch (RuntimeException exception) {
                professions.clear();
                traits.clear();
                resultMessageKey = "screen.aftermath.character.error.invalid_data";
            }
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
                    items.add(new StartingItem(string(item, "item", string(item, "id", "minecraft:air")),
                            integer(item, "count", 1)));
                }
            }
            return List.copyOf(items);
        }

        private void refreshProfessionRows() {
            if (professionRows == null) return;
            professionRows.clearChildren();
            professionButtons.clear();
            for (ProfessionOption option : professions) {
                boolean selected = option.id().equals(selectedProfessionId);
                String name = tr(option.name());
                Button button = new Button(name, () -> selectProfession(option.id()))
                        .item(option.icon()).iconSize(15).width(Dim.FILL).height(Dim.px(32))
                        .tooltip(tr(option.description()));
                applyButtonStyle(button, selected, false);
                professionRows.addChild(button);
                professionButtons.put(option.id(), button);
            }
        }

        private void refreshTraitRows() {
            if (traitRows == null) return;
            traitRows.clearChildren();
            traitButtons.clear();
            ProfessionOption profession = profession();
            if (profession == null) {
                traitRows.addChild(label(tr("screen.aftermath.character.error.invalid_profession"), RED, 0.9f));
                return;
            }

            List<TraitOption> available = visibleTraits(profession);
            addTraitCategory("positive", available);
            addTraitCategory("negative", available);
            addTraitCategory("neutral", available);
            if (available.isEmpty()) {
                traitRows.addChild(label(tr("screen.aftermath.character.no_traits"), MUTED, 0.9f));
            }
        }

        private void addTraitCategory(String category, List<TraitOption> available) {
            List<TraitOption> categoryTraits = available.stream()
                    .filter(trait -> trait.category().equalsIgnoreCase(category))
                    .toList();
            if (categoryTraits.isEmpty()) return;
            traitRows.addChild(label(tr("screen.aftermath.character.category." + category), MUTED, 0.75f)
                    .style(s -> s.set(Styles.TEXT_COLOR, category.equals("negative") ? RED : ACCENT))
                    .height(Dim.px(16)));
            for (TraitOption trait : categoryTraits) {
                boolean granted = profession().grantedTraits().contains(trait.id());
                boolean selected = granted || selectedTraits.contains(trait.id());
                int delta = trait.category().equalsIgnoreCase("positive")
                        ? -trait.pointCost() : trait.category().equalsIgnoreCase("negative")
                        ? trait.pointCredit() : 0;
                String deltaText = delta > 0 ? "+" + delta : Integer.toString(delta);
                String prefix = granted ? "★ " : selected ? "✓ " : "○ ";
                String labelText = prefix + fit(tr(trait.name()), 24) + "  [" + deltaText + "]";
                Button button = new Button(labelText, () -> toggleTrait(trait))
                        .width(Dim.FILL).height(Dim.px(29))
                        .tooltip(tr(trait.description()));
                if (granted) button.enabled(false);
                applyButtonStyle(button, selected, granted);
                traitRows.addChild(button);
                traitButtons.put(trait.id(), button);
            }
        }

        private void selectProfession(String professionId) {
            if (waitingForServer || professionId.equals(selectedProfessionId)) return;
            selectedProfessionId = professionId;
            ProfessionOption profession = profession();
            Set<String> granted = profession == null ? Set.of() : profession.grantedTraits();
            selectedTraits.removeIf(id -> {
                TraitOption trait = trait(id);
                if (trait == null || granted.contains(id)) return true;
                if (!profession.allowedTraits().isEmpty() && !profession.allowedTraits().contains(id)) return true;
                if (!trait.requiresProfession().isBlank() && !trait.requiresProfession().equals(profession.id())) return true;
                return conflictsWith(trait, granted);
            });
            refreshProfessionRows();
            refreshTraitRows();
            refreshSummary();
            refreshFooter();
            if (traitScroll != null) traitScroll.scrollTo(0f, false);
        }

        private void toggleTrait(TraitOption option) {
            if (waitingForServer || profession() == null || profession().grantedTraits().contains(option.id())) return;
            if (selectedTraits.remove(option.id())) {
                refreshTraitRows();
                refreshSummary();
                refreshFooter();
                return;
            }

            Set<String> active = new LinkedHashSet<>(selectedTraits);
            active.addAll(profession().grantedTraits());
            if (conflictsWith(option, active)) {
                resultMessageKey = "screen.aftermath.character.error.conflicting_traits";
                refreshFooter();
                return;
            }
            selectedTraits.add(option.id());
            resultMessageKey = "";
            refreshTraitRows();
            refreshSummary();
            refreshFooter();
        }

        private boolean conflictsWith(TraitOption candidate, Set<String> otherIds) {
            if (!Collections.disjoint(candidate.conflicts(), otherIds)) return true;
            for (String otherId : otherIds) {
                TraitOption other = trait(otherId);
                if (other != null && other.conflicts().contains(candidate.id())) return true;
            }
            return false;
        }

        private void refreshSummary() {
            if (summaryRows == null) return;
            summaryRows.clearChildren();
            ProfessionOption profession = profession();
            if (profession == null) {
                summaryRows.addChild(label(tr("screen.aftermath.character.error.invalid_profession"), RED, 0.9f));
                return;
            }

            summaryRows.addChild(label(tr(profession.name()), TEXT, 1.16f));
            addWrapped(summaryRows, tr(profession.description()), 25, MUTED, 0.88f);

            summaryRows.addChild(sectionLabel(tr("screen.aftermath.character.starting_skills"), ACCENT));
            if (profession.skillBonuses().isEmpty()) {
                summaryRows.addChild(label(tr("screen.aftermath.character.starting_skills_none"), MUTED, 0.84f));
            } else {
                profession.skillBonuses().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(skill ->
                        summaryRows.addChild(label(tr("skill.aftermath." + skill.getKey()) + "  ·  " + tr("screen.aftermath.character.skill_level_short", skill.getValue()),
                                GREEN, 0.88f)));
            }

            summaryRows.addChild(sectionLabel(tr("screen.aftermath.character.selected_traits")));
            Set<String> displayed = new LinkedHashSet<>();
            for (String id : profession.grantedTraits()) {
                TraitOption trait = trait(id);
                if (trait != null) {
                    summaryRows.addChild(label("★ " + tr(trait.name()) + "  ·  " + tr("screen.aftermath.character.trait.granted"),
                            GREEN, 0.84f));
                    displayed.add(id);
                }
            }
            for (String id : selectedTraits) {
                if (displayed.contains(id)) continue;
                TraitOption trait = trait(id);
                if (trait != null) {
                    summaryRows.addChild(label("✓ " + tr(trait.name()), TEXT, 0.84f));
                    displayed.add(id);
                }
            }
            if (displayed.isEmpty()) summaryRows.addChild(label(tr("screen.aftermath.character.no_traits"), MUTED, 0.84f));

            summaryRows.addChild(sectionLabel(tr("screen.aftermath.character.starting_items")));
            if (profession.startingItems().isEmpty()) {
                summaryRows.addChild(label(tr("screen.aftermath.character.no_items"), MUTED, 0.84f));
            } else {
                for (StartingItem item : profession.startingItems()) {
                    summaryRows.addChild(label(item.count() + " × " + itemName(item.itemId()), TEXT, 0.84f));
                }
            }
        }

        private void refreshFooter() {
            if (pointsLabel == null || confirmButton == null || statusLabel == null) return;
            ProfessionOption profession = profession();
            int points = remainingPoints();
            pointsLabel.text(tr("screen.aftermath.character.points", points, profession == null ? 0 : profession.startingPoints()));
            pointsLabel.style(s -> s.set(Styles.TEXT_COLOR, points < 0 ? RED : points == 0 ? ACCENT : GREEN));
            confirmButton.enabled(!waitingForServer && profession != null && points >= 0 && !professions.isEmpty() && !traits.isEmpty());
            if (waitingForServer) {
                statusLabel.text(tr("screen.aftermath.character.saving"));
                statusLabel.style(s -> s.set(Styles.TEXT_COLOR, ACCENT));
            } else if (!resultMessageKey.isBlank()) {
                statusLabel.text(tr(resultMessageKey));
                statusLabel.style(s -> s.set(Styles.TEXT_COLOR,
                        resultMessageKey.contains(".error.") || resultMessageKey.endsWith("invalid_data") ? RED : MUTED));
            } else {
                statusLabel.text(tr("screen.aftermath.character.footer_hint"));
                statusLabel.style(s -> s.set(Styles.TEXT_COLOR, MUTED));
            }
        }

        private void submit() {
            if (waitingForServer || profession() == null || remainingPoints() < 0) return;
            waitingForServer = true;
            resultMessageKey = "";
            refreshFooter();
            String traitsCsv = selectedTraits.stream().sorted().reduce((a, b) -> a + "," + b).orElse("");
            PacketDistributor.sendToServer(new SelectCharacterPayload(selectedProfessionId, traitsCsv));
        }

        private void setResult(String messageKey) {
            waitingForServer = false;
            resultMessageKey = messageKey == null ? "" : messageKey;
            refreshFooter();
        }

        private int remainingPoints() {
            ProfessionOption selected = profession();
            if (selected == null) return Integer.MIN_VALUE;
            int remaining = selected.startingPoints();
            for (String id : selectedTraits) {
                if (selected.grantedTraits().contains(id)) continue;
                TraitOption option = trait(id);
                if (option == null) continue;
                remaining += option.category().equalsIgnoreCase("positive")
                        ? -option.pointCost() : option.pointCredit();
            }
            return remaining;
        }

        private ProfessionOption profession() {
            return professions.stream().filter(option -> option.id().equals(selectedProfessionId)).findFirst().orElse(null);
        }

        private List<TraitOption> visibleTraits(ProfessionOption selected) {
            return traits.stream()
                    .filter(trait -> selected.allowedTraits().isEmpty() || selected.allowedTraits().contains(trait.id()))
                    .filter(trait -> trait.requiresProfession().isBlank() || trait.requiresProfession().equals(selected.id()))
                    .toList();
        }

        private TraitOption trait(String id) {
            return traits.stream().filter(option -> option.id().equals(id)).findFirst().orElse(null);
        }

        private void addWrapped(Panel panel, String text, int maxChars, int color, float scale) {
            for (String line : wrap(text, maxChars)) {
                panel.addChild(label(line, color, scale));
            }
        }

        private static Panel sectionLabel(String text, int color) {
            return Panel.column().gap(0).width(Dim.FILL).height(Dim.px(18))
                    .add(label(text.toUpperCase(), color, 0.82f));
        }

        private static Label label(String text, int color, float scale) {
            return new Label(text).scale(scale).width(Dim.FILL)
                    .style(s -> s.set(Styles.TEXT_COLOR, color));
        }

        private static Panel surface(Panel panel, int background, int border, float radius, float padding) {
            panel.style(s -> s
                    .set(Styles.BACKGROUND, Brush.solid(background))
                    .set(Styles.BORDER_COLOR, border)
                    .set(Styles.BORDER_WIDTH, 1f)
                    .set(Styles.RADIUS, Radii.all(radius))
                    .set(Styles.PADDING, Insets.all(padding)));
            return panel;
        }

        private static ScrollPanel scroll(ScrollPanel scroll) {
            scroll.style(s -> s
                    .set(Styles.BACKGROUND, Brush.solid(0xA60D1010))
                    .set(Styles.BORDER_COLOR, 0x403A4241)
                    .set(Styles.BORDER_WIDTH, 1f)
                    .set(Styles.RADIUS, Radii.all(2f))
                    .set(Styles.PADDING, Insets.all(5f)));
            return scroll;
        }

        private static void applyButtonStyle(Button button, boolean selected, boolean granted) {
            int background = granted ? 0xE5283632 : selected ? ROW_SELECTED : ROW;
            int border = granted ? 0xFF577F74 : selected ? ACCENT : BORDER;
            int text = granted ? GREEN : selected ? 0xFFFFD5B2 : TEXT;
            button.style(s -> s
                    .set(Styles.BACKGROUND, Brush.solid(background))
                    .set(Styles.BORDER_COLOR, border)
                    .set(Styles.BORDER_WIDTH, selected || granted ? 1.2f : 0.7f)
                    .set(Styles.RADIUS, Radii.all(3f))
                    .set(Styles.PADDING, Insets.of(4f, 6f))
                    .set(Styles.TEXT_COLOR, text)
                    .hovered(h -> h
                            .set(Styles.BACKGROUND, Brush.solid(0xF0493A2E))
                            .set(Styles.BORDER_COLOR, 0xFFFFBD88)));
        }

        private static String tr(String key) {
            return Component.translatable(key).getString();
        }

        private static String tr(String key, Object... args) {
            return Component.translatable(key, args).getString();
        }

        private static String fit(String value, int maxChars) {
            if (value.length() <= maxChars) return value;
            return value.substring(0, Math.max(1, maxChars - 1)).stripTrailing() + "…";
        }

        private static List<String> wrap(String value, int maxChars) {
            List<String> lines = new ArrayList<>();
            String remaining = value == null ? "" : value.strip();
            while (remaining.length() > maxChars) {
                int split = remaining.lastIndexOf(' ', maxChars);
                if (split < 1) split = maxChars;
                lines.add(remaining.substring(0, split).strip());
                remaining = remaining.substring(split).strip();
            }
            if (!remaining.isEmpty()) lines.add(remaining);
            return lines;
        }

        private static String itemName(String itemId) {
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            if (id == null) return itemId;
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
            return item == Items.AIR ? itemId : new ItemStack(item).getHoverName().getString();
        }
    }

    private record ProfessionOption(String id, String name, String description, String icon, int startingPoints,
                                    Set<String> allowedTraits, Set<String> grantedTraits,
                                    Map<String, Integer> skillBonuses, List<StartingItem> startingItems) {
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
