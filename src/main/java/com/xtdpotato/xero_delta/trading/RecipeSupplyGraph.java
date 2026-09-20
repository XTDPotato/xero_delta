package com.xtdpotato.xero_delta.trading;

import java.util.*;

/** Bidirectional recipe connectivity; only recipe outputs are eligible for supply. */
public final class RecipeSupplyGraph {
    private final Map<String, String> parent = new HashMap<>();
    private final Map<String, Integer> sizes = new HashMap<>();
    private final Set<String> outputs = new LinkedHashSet<>();
    private Map<String, Set<String>> resolved;

    public void addRecipe(String output, Collection<String> ingredients) {
        if (resolved != null) throw new IllegalStateException("Recipe graph is already sealed");
        outputs.add(output);
        root(output);
        for (String input : ingredients) {
            String a = root(output), b = root(input);
            if (a.equals(b)) continue;
            if (sizes.get(a) < sizes.get(b)) { String swap = a; a = b; b = swap; }
            parent.put(b, a);
            sizes.put(a, sizes.get(a) + sizes.get(b));
        }
    }

    public Set<String> outputsFor(String key) {
        seal();
        return resolved.getOrDefault(key, Set.of());
    }

    public boolean hasRecipe(String key) { return outputs.contains(key); }

    private String root(String key) {
        parent.putIfAbsent(key, key);
        sizes.putIfAbsent(key, 1);
        String result = key;
        while (!parent.get(result).equals(result)) result = parent.get(result);
        while (!parent.get(key).equals(key)) {
            String next = parent.get(key);
            parent.put(key, result);
            key = next;
        }
        return result;
    }

    private void seal() {
        if (resolved != null) return;
        Map<String, Set<String>> groups = new HashMap<>();
        for (String output : outputs) groups.computeIfAbsent(root(output), ignored -> new LinkedHashSet<>()).add(output);
        groups.replaceAll((key, values) -> Collections.unmodifiableSet(values));
        resolved = new HashMap<>();
        for (String key : parent.keySet()) resolved.put(key, groups.getOrDefault(root(key), Set.of()));
    }
}

