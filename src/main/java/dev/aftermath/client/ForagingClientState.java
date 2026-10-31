package dev.aftermath.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Bounded client-side cache of the latest server-authoritative search results. */
public final class ForagingClientState {
    private static volatile Snapshot current = Snapshot.inactive();

    private ForagingClientState() {}

    public static void handleSnapshot(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("version") && root.get("version").getAsInt() != 1) {
                current = Snapshot.inactive();
                return;
            }
            boolean active = root.has("active") && root.get("active").getAsBoolean();
            String focus = root.has("focus") ? root.get("focus").getAsString() : "";
            double radius = root.has("radius") ? root.get("radius").getAsDouble() : 0.0D;

            List<String> categories = new ArrayList<>();
            JsonArray categoryArray = root.has("categories") && root.get("categories").isJsonArray()
                    ? root.getAsJsonArray("categories") : new JsonArray();
            for (JsonElement element : categoryArray) {
                if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                    String id = element.getAsString();
                    if (id.matches("[a-z0-9_-]{1,32}") && categories.size() < 32) categories.add(id);
                }
            }

            List<Find> finds = new ArrayList<>();
            JsonArray findArray = root.has("finds") && root.get("finds").isJsonArray()
                    ? root.getAsJsonArray("finds") : new JsonArray();
            for (JsonElement element : findArray) {
                if (!element.isJsonObject() || finds.size() >= 48) continue;
                JsonObject entry = element.getAsJsonObject();
                if (!entry.has("x") || !entry.has("y") || !entry.has("z")
                        || !entry.has("distance") || !entry.has("revealed")) continue;
                int x = entry.get("x").getAsInt();
                int y = entry.get("y").getAsInt();
                int z = entry.get("z").getAsInt();
                double distance = entry.get("distance").getAsDouble();
                if (!Double.isFinite(distance) || distance < 0.0D || distance > 15.0D) continue;
                boolean revealed = entry.get("revealed").getAsBoolean();
                ResourceLocation itemId = revealed && entry.has("item")
                        ? ResourceLocation.tryParse(entry.get("item").getAsString()) : null;
                int count = revealed && entry.has("count")
                        ? Math.max(1, Math.min(64, entry.get("count").getAsInt())) : 0;
                finds.add(new Find(new BlockPos(x, y, z), distance, revealed, itemId, count));
            }

            if (!Double.isFinite(radius)) radius = 0.0D;
            current = new Snapshot(active, focus, Math.max(0.0D, Math.min(15.0D, radius)),
                    List.copyOf(categories), List.copyOf(finds));
        } catch (RuntimeException exception) {
            current = Snapshot.inactive();
        }
    }

    public static Snapshot snapshot() {
        return current;
    }

    public static String nextFocus() {
        Snapshot snapshot = current;
        if (snapshot.categories().isEmpty()) return "";
        int index = snapshot.categories().indexOf(snapshot.focus());
        return snapshot.categories().get((index + 1 + snapshot.categories().size()) % snapshot.categories().size());
    }

    public static Find nearestCollectable(double x, double y, double z) {
        return current.finds().stream()
                .filter(find -> find.revealed() && find.itemId() != null)
                .filter(find -> {
                    double dx = x - (find.position().getX() + 0.5D);
                    double dz = z - (find.position().getZ() + 0.5D);
                    double dy = y - find.position().getY();
                    return dx * dx + dz * dz <= 3.5D * 3.5D && Math.abs(dy) <= 3.0D;
                })
                .min(Comparator.comparingDouble(Find::distance))
                .orElse(null);
    }

    public record Find(BlockPos position, double distance, boolean revealed, ResourceLocation itemId, int count) {}

    public record Snapshot(boolean active, String focus, double radius,
                           List<String> categories, List<Find> finds) {
        public static Snapshot inactive() {
            return new Snapshot(false, "", 0.0D, List.of(), List.of());
        }
    }
}
