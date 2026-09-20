package com.xtdpotato.xero_delta.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** File-backed item weight rules in kilograms. */
public final class ServerItemWeights {
    private static final Gson GSON = new Gson();
    private static final String FILE_NAME = "xero_delta_item_weights.json";
    private static final long FILE_CHECK_INTERVAL_NANOS = 1_000_000_000L;
    private static volatile Rules cachedRules;
    private static volatile long cachedModified = Long.MIN_VALUE;
    private static volatile long cachedFileSize = Long.MIN_VALUE;
    private static volatile long nextFileCheckNanos;

    private ServerItemWeights() {
    }

    public static double getWeightFor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0.0D;
        Rules rules = load();
        String key = findRuleKey(rules.weights(), stack);
        Double configured = key == null ? null : rules.weights().get(key);
        return configured == null ? AutomaticItemWeight.estimate(stack) : configured;
    }

    public static Double getConfiguredWeight(String key) {
        return load().weights().get(canonicalRuleKey(key));
    }

    public static String matchingRuleKey(ItemStack stack) {
        return findRuleKey(load().weights(), stack);
    }

    public static synchronized void setWeight(String key, double kilograms) {
        key = canonicalRuleKey(key);
        Rules rules = load();
        Map<String, Double> weights = new HashMap<>(rules.weights());
        weights.put(key, normalize(kilograms));
        Set<String> automatic = new HashSet<>(rules.autoKeys());
        automatic.remove(key);
        save(new Rules(Map.copyOf(weights), Set.copyOf(automatic)));
    }

    public static synchronized void setAutoWeights(Map<String, Double> calculated) {
        Rules rules = load();
        Map<String, Double> weights = new HashMap<>(rules.weights());
        for (String key : rules.autoKeys()) weights.remove(key);
        Set<String> automatic = new HashSet<>();
        for (var entry : calculated.entrySet()) {
            String key = canonicalRuleKey(entry.getKey());
            if (weights.containsKey(key)) continue;
            weights.put(key, normalize(entry.getValue()));
            automatic.add(key);
        }
        save(new Rules(Map.copyOf(weights), Set.copyOf(automatic)));
    }

    public static Map<String, Double> getAllWeights() {
        return load().weights();
    }

    private static Rules load() {
        long now = System.nanoTime();
        Rules current = cachedRules;
        if (current != null && now < nextFileCheckNanos) return current;
        synchronized (ServerItemWeights.class) {
            now = System.nanoTime();
            current = cachedRules;
            if (current != null && now < nextFileCheckNanos) return current;
            Path path = ConfigPaths.file(FILE_NAME);
            ensureFile(path);
            nextFileCheckNanos = now + FILE_CHECK_INTERVAL_NANOS;
            try {
                long modified = Files.getLastModifiedTime(path).toMillis();
                long fileSize = Files.size(path);
                if (current != null && modified == cachedModified && fileSize == cachedFileSize) return current;
                JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
                Map<String, Double> weights = new HashMap<>();
                Set<String> automatic = new HashSet<>();
                if (root.has("weights") && root.get("weights").isJsonObject()) {
                    for (var entry : root.getAsJsonObject("weights").entrySet()) {
                        JsonElement value = entry.getValue();
                        double kilograms;
                        boolean auto = false;
                        if (value.isJsonObject()) {
                            JsonObject object = value.getAsJsonObject();
                            kilograms = object.get("kg").getAsDouble();
                            auto = object.has("auto") && object.get("auto").getAsBoolean();
                        } else {
                            kilograms = value.getAsDouble();
                        }
                        String key = canonicalRuleKey(entry.getKey());
                        weights.put(key, normalize(kilograms));
                        if (auto) automatic.add(key);
                    }
                }
                Rules loaded = refreshAutomaticWeights(new Rules(Map.copyOf(weights), Set.copyOf(automatic)));
                updateCache(loaded, modified, fileSize);
                return loaded;
            } catch (Exception ignored) {
                return current == null ? new Rules(Map.of(), Set.of()) : current;
            }
        }
    }

    private static synchronized void save(Rules rules) {
        Path path = ConfigPaths.file(FILE_NAME);
        try {
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            JsonObject weights = new JsonObject();
            for (var entry : rules.weights().entrySet()) {
                JsonObject value = new JsonObject();
                value.addProperty("kg", normalize(entry.getValue()));
                if (rules.autoKeys().contains(entry.getKey())) value.addProperty("auto", true);
                weights.add(canonicalRuleKey(entry.getKey()), value);
            }
            root.add("weights", weights);
            Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
            updateCache(rules, Files.getLastModifiedTime(path).toMillis(), Files.size(path));
            nextFileCheckNanos = System.nanoTime() + FILE_CHECK_INTERVAL_NANOS;
        } catch (IOException ignored) {
        }
    }

    private static Rules refreshAutomaticWeights(Rules rules) {
        if (rules.autoKeys().isEmpty()) return rules;
        Map<String, Double> generated = AutomaticItemWeight.calculateAll();
        Map<String, Double> refreshed = new HashMap<>(rules.weights());
        boolean changed = false;
        for (String key : rules.autoKeys()) {
            Double latest = generated.get(key);
            if (latest == null) continue;
            double normalized = normalize(latest);
            Double previous = refreshed.put(key, normalized);
            changed |= previous == null || Double.compare(previous, normalized) != 0;
        }
        return changed
            ? new Rules(Map.copyOf(refreshed), Set.copyOf(rules.autoKeys()))
            : rules;
    }

    private static String findRuleKey(Map<String, Double> rules, ItemStack stack) {
        String exact = ServerItemRules.exactKey(ModDataStorage.getKey(stack));
        if (rules.containsKey(exact)) return exact;
        String type = ServerItemRules.exactKey(ModDataStorage.getTypeKey(stack));
        if (rules.containsKey(type)) return type;
        String durability = null;
        for (String key : rules.keySet()) {
            if (!DurabilityRange.matches(key, stack)) continue;
            if (durability == null || key.compareTo(durability) < 0) durability = key;
        }
        if (durability != null) return durability;
        String item = ServerItemRules.exactKey(ModDataStorage.getIdOnlyKey(stack));
        if (rules.containsKey(item)) return item;
        return rules.containsKey("*") ? "*" : null;
    }

    private static String canonicalRuleKey(String key) {
        if (key == null) return "";
        if (!key.startsWith("item:")) return key;
        return ServerItemRules.exactKey(ModDataStorage.canonicalizeRuleKey(key.substring("item:".length())));
    }

    private static double normalize(double kilograms) {
        if (!Double.isFinite(kilograms)) return 0.001D;
        return Math.max(0.001D, Math.min(1_000_000.0D,
            Math.round(kilograms * 1000.0D) / 1000.0D));
    }

    private static void ensureFile(Path path) {
        if (Files.exists(path)) return;
        save(new Rules(Map.of(), Set.of()));
    }

    private static void updateCache(Rules rules, long modified, long fileSize) {
        cachedRules = new Rules(Map.copyOf(rules.weights()), Set.copyOf(rules.autoKeys()));
        cachedModified = modified;
        cachedFileSize = fileSize;
    }

    private record Rules(Map<String, Double> weights, Set<String> autoKeys) {
    }
}
