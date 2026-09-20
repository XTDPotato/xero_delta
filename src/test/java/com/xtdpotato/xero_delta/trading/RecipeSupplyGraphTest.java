package com.xtdpotato.xero_delta.trading;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RecipeSupplyGraphTest {
    @Test void traversesBothWaysButOnlyListsRecipeOutputs() {
        var graph = new RecipeSupplyGraph();
        graph.addRecipe("planks", List.of("log"));
        graph.addRecipe("sticks", List.of("planks"));
        graph.addRecipe("table", List.of("planks", "planks", "planks", "planks"));
        graph.addRecipe("unrelated", List.of("other_raw"));
        assertEquals(Set.of("planks","sticks","table"), graph.outputsFor("log"));
        assertEquals(Set.of("planks","sticks","table"), graph.outputsFor("table"));
        assertFalse(graph.outputsFor("table").contains("log"));
        assertTrue(graph.outputsFor("no_recipe_or_use").isEmpty());
    }
    @Test void alternativesAndCompressionCyclesDoNotMultiplySupply() {
        var graph = new RecipeSupplyGraph();
        graph.addRecipe("ingot", List.of("ore","block"));
        graph.addRecipe("block", List.of("ingot","ingot","ingot"));
        graph.addRecipe("tool", List.of("ingot","alternative_ingot"));
        assertEquals(Set.of("ingot","block","tool"), graph.outputsFor("ore"));
        assertEquals(3, graph.outputsFor("alternative_ingot").size());
    }
    @Test void deepModRecipeChainsHaveNoRecursionLimitOrStackOverflow() {
        var graph = new RecipeSupplyGraph();
        for (int i=1; i<=10_000; i++) graph.addRecipe("item"+i, List.of("item"+(i-1)));
        assertEquals(10_000, graph.outputsFor("item0").size());
        assertEquals(graph.outputsFor("item0"), graph.outputsFor("item10000"));
    }
}

