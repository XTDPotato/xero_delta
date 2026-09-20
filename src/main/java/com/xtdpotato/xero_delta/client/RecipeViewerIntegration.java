package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.XeroDelta;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/** Optional recipe-viewer bridge that does not make REI a required dependency. */
public final class RecipeViewerIntegration {
    private static final String ENTRY_STACKS = "me.shedaniel.rei.api.common.util.EntryStacks";
    private static final String ENTRY_STACK = "me.shedaniel.rei.api.common.entry.EntryStack";
    private static final String VIEW_SEARCH_BUILDER = "me.shedaniel.rei.api.client.view.ViewSearchBuilder";

    private RecipeViewerIntegration() {
    }

    public static boolean openRecipes(ItemStack stack) {
        return open(stack, true);
    }

    public static boolean openUses(ItemStack stack) {
        return open(stack, false);
    }

    public static String viewerName() {
        if (ModList.get().isLoaded("roughlyenoughitems")) return "REI";
        if (ModList.get().isLoaded("jei")) return "JEI";
        if (ModList.get().isLoaded("emi")) return "EMI";
        return "";
    }

    public static boolean hasRecipe(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (stack == null || stack.isEmpty() || minecraft.level == null) return false;
        return minecraft.level.getRecipeManager().getRecipes().stream().anyMatch(holder -> {
            ItemStack result = holder.value().getResultItem(minecraft.level.registryAccess());
            return !result.isEmpty() && result.is(stack.getItem());
        });
    }

    private static boolean open(ItemStack stack, boolean recipes) {
        if (stack == null || stack.isEmpty()) return false;
        return switch (viewerName()) {
            case "REI" -> openReiView(stack, recipes ? "addRecipesFor" : "addUsagesFor");
            case "JEI" -> openJeiView(stack, recipes);
            case "EMI" -> openEmiView(stack, recipes);
            default -> false;
        };
    }

    private static boolean openReiView(ItemStack stack, String searchMethod) {
        try {
            Class<?> entryStacksClass = Class.forName(ENTRY_STACKS);
            Class<?> entryStackClass = Class.forName(ENTRY_STACK);
            Class<?> builderClass = Class.forName(VIEW_SEARCH_BUILDER);
            Object entry = entryStacksClass.getMethod("of", ItemStack.class).invoke(null, stack.copy());
            Object builder = builderClass.getMethod("builder").invoke(null);
            Method addSearch = builderClass.getMethod(searchMethod, entryStackClass);
            Object configured = addSearch.invoke(builder, entry);
            Object target = configured == null ? builder : configured;
            Object opened = builderClass.getMethod("open").invoke(target);
            return !(opened instanceof Boolean result) || result;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            XeroDelta.LOGGER.debug("Unable to open REI view through {}", searchMethod, exception);
            return false;
        }
    }

    private static boolean openJeiView(ItemStack stack, boolean recipes) {
        try {
            Class<?> internalClass = Class.forName("mezz.jei.common.Internal");
            Class<?> runtimeClass = Class.forName("mezz.jei.api.runtime.IJeiRuntime");
            Object runtime = internalClass.getMethod("getJeiRuntime").invoke(null);
            if (runtime == null) return false;
            Object recipesGui = runtimeClass.getMethod("getRecipesGui").invoke(runtime);
            Object helpers = runtimeClass.getMethod("getJeiHelpers").invoke(runtime);
            Class<?> helpersClass = Class.forName("mezz.jei.api.helpers.IJeiHelpers");
            Object focusFactory = helpersClass.getMethod("getFocusFactory").invoke(helpers);
            Class<?> roleClass = Class.forName("mezz.jei.api.recipe.RecipeIngredientRole");
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object role = Enum.valueOf((Class<? extends Enum>) roleClass,
                recipes ? "OUTPUT" : "INPUT");
            Object ingredientType = Class.forName("mezz.jei.api.constants.VanillaTypes")
                .getField("ITEM_STACK").get(null);
            Class<?> ingredientTypeClass =
                Class.forName("mezz.jei.api.ingredients.IIngredientType");
            Object focus = Class.forName("mezz.jei.api.recipe.IFocusFactory")
                .getMethod("createFocus", roleClass, ingredientTypeClass, Object.class)
                .invoke(focusFactory, role, ingredientType, stack.copy());
            Class<?> focusClass = Class.forName("mezz.jei.api.recipe.IFocus");
            Class.forName("mezz.jei.api.runtime.IRecipesGui")
                .getMethod("show", focusClass).invoke(recipesGui, focus);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            XeroDelta.LOGGER.debug("Unable to open JEI {} view",
                recipes ? "recipe" : "usage", exception);
            return false;
        }
    }

    private static boolean openEmiView(ItemStack stack, boolean recipes) {
        try {
            Class<?> emiStackClass = Class.forName("dev.emi.emi.api.stack.EmiStack");
            Object ingredient = emiStackClass.getMethod("of", ItemStack.class)
                .invoke(null, stack.copy());
            Class<?> ingredientClass = Class.forName("dev.emi.emi.api.stack.EmiIngredient");
            Class.forName("dev.emi.emi.api.EmiApi")
                .getMethod(recipes ? "displayRecipes" : "displayUses", ingredientClass)
                .invoke(null, ingredient);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            XeroDelta.LOGGER.debug("Unable to open EMI {} view",
                recipes ? "recipe" : "usage", exception);
            return false;
        }
    }
}
