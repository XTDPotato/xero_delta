package com.xtdpotato.xero_delta.trading;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RecipeSupplyIndexTest {
    private static RecipeHolder<?> recipe(String name,ItemStack output,Item... inputs) {
        return new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("example_machine_mod",name),
            new MachineRecipe(output,Ingredient.of(inputs)));
    }
    private static RecipeSupplyIndex capture(RecipeHolder<?>... recipes) {
        return RecipeSupplyIndex.capture(List.of(recipes),
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }
    @Test void readsCustomRecipeTypesAndEveryIngredientAlternative() {
        var index=capture(
            recipe("sawing",Items.OAK_PLANKS.getDefaultInstance(),Items.OAK_LOG,Items.BIRCH_LOG),
            recipe("assembly",Items.CRAFTING_TABLE.getDefaultInstance(),Items.OAK_PLANKS));
        var expected=Set.of(RecipeSupplyIndex.key(Items.OAK_PLANKS.getDefaultInstance()),
            RecipeSupplyIndex.key(Items.CRAFTING_TABLE.getDefaultInstance()));
        assertEquals(expected,index.related(Items.BIRCH_LOG.getDefaultInstance()));
        assertEquals(expected,index.related(Items.CRAFTING_TABLE.getDefaultInstance()));
        assertFalse(index.graph.hasRecipe(RecipeSupplyIndex.key(Items.BIRCH_LOG.getDefaultInstance())));
    }
    @Test void componentBearingModOutputsRemainDistinct() {
        var first=Items.DIAMOND.getDefaultInstance();
        first.set(DataComponents.CUSTOM_NAME,Component.literal("first variant"));
        var second=Items.DIAMOND.getDefaultInstance();
        second.set(DataComponents.CUSTOM_NAME,Component.literal("second variant"));
        var index=capture(recipe("first",first,Items.COAL),recipe("second",second,Items.COAL));
        assertEquals(2,index.related(Items.COAL.getDefaultInstance()).size());
        assertNotEquals(RecipeSupplyIndex.key(first),RecipeSupplyIndex.key(second));
    }
    @Test void unusableDynamicOutputsDoNotHideOtherModsRecipes() {
        var index=capture(recipe("dynamic",ItemStack.EMPTY,Items.OAK_LOG),
            recipe("working",Items.STICK.getDefaultInstance(),Items.OAK_LOG));
        assertEquals(Set.of(RecipeSupplyIndex.key(Items.STICK.getDefaultInstance())),
            index.related(Items.OAK_LOG.getDefaultInstance()));
    }
    private record MachineRecipe(ItemStack result,Ingredient input) implements Recipe<CraftingInput> {
        private static final RecipeType<MachineRecipe> TYPE=new RecipeType<>() {};
        public boolean matches(CraftingInput input,Level level) { return false; }
        public ItemStack assemble(CraftingInput input,HolderLookup.Provider registries) { return result.copy(); }
        public boolean canCraftInDimensions(int width,int height) { return true; }
        public ItemStack getResultItem(HolderLookup.Provider registries) { return result; }
        public NonNullList<Ingredient> getIngredients() { return NonNullList.of(Ingredient.EMPTY,input); }
        public RecipeSerializer<?> getSerializer() { return RecipeSerializer.SHAPELESS_RECIPE; }
        public RecipeType<?> getType() { return TYPE; }
    }
}

