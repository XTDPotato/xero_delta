package com.xtdpotato.xero_delta.trading;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class RecipeOwnedInventoryTest {
    @Test void movingContentsBetweenAContainerAndInventoryDoesNotAcquireThemAgain() {
        var full=Items.SHULKER_BOX.getDefaultInstance();
        full.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(
            List.of(Items.DIAMOND.getDefaultInstance().copyWithCount(3))));
        var before=new RecipeOwnedInventory();
        before.add(full);
        var after=new RecipeOwnedInventory();
        after.add(Items.SHULKER_BOX.getDefaultInstance());
        after.add(Items.DIAMOND.getDefaultInstance().copyWithCount(3));
        assertEquals(before.counts,after.counts);
    }
    @Test void splittingStacksAndChangingDurabilityDoNotCreateAcquisitions() {
        var before=new RecipeOwnedInventory();
        before.add(Items.OAK_PLANKS.getDefaultInstance().copyWithCount(8));
        var sword=Items.IRON_SWORD.getDefaultInstance();
        sword.setDamageValue(5);
        before.add(sword);
        var after=new RecipeOwnedInventory();
        after.add(Items.OAK_PLANKS.getDefaultInstance().copyWithCount(3));
        after.add(Items.OAK_PLANKS.getDefaultInstance().copyWithCount(5));
        var worn=sword.copy();
        worn.setDamageValue(20);
        after.add(worn);
        assertEquals(before.counts,after.counts);
    }
    @Test void aliasesAreCountedOnceButSeparateIdenticalStacksAreCountedTwice() {
        var counts=new RecipeOwnedInventory();
        var item=Items.DIAMOND.getDefaultInstance();
        counts.add(item);
        counts.add(item);
        counts.add(item.copy());
        assertEquals(2L,counts.counts.get(RecipeSupplyIndex.key(item)));
    }
}

