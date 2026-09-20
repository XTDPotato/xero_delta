package com.xtdpotato.xero_delta.trading;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class RecipeWorldMarketDataTest {
    @Test void publicNumbersFitTheExistingTradingPacketLimit() {
        String first=RecipeWorldMarketData.publicListingId("some_mod:item{components}");
        assertTrue(first.length() <= 32);
        assertTrue(first.startsWith("WR-"));
        assertEquals(first,RecipeWorldMarketData.publicListingId("some_mod:item{components}"));
        assertNotEquals(first,RecipeWorldMarketData.publicListingId("some_mod:other_item"));
    }
    @Test void onlyPositiveAcquisitionsIncreaseCounts() {
        var data = new RecipeWorldMarketData();
        UUID player=UUID.randomUUID();
        assertEquals(Map.of("planks",3L), data.observe(player,Map.of("planks",3L)));
        assertTrue(data.observe(player,Map.of("planks",3L)).isEmpty());
        assertEquals(Map.of("planks",2L), data.observe(player,Map.of("planks",5L)));
        assertTrue(data.observe(player,Map.of("planks",1L)).isEmpty());
        assertEquals(Map.of("planks",4L), data.observe(player,Map.of("planks",5L)));
    }
    @Test void purchasesAreRebasedWithoutSuppressingLaterLoot() {
        var data=new RecipeWorldMarketData();
        UUID player=UUID.randomUUID();
        data.observe(player,Map.of("planks",2L));
        data.rebase(player,Map.of("planks",7L));
        assertTrue(data.observe(player,Map.of("planks",7L)).isEmpty());
        assertEquals(Map.of("planks",1L),data.observe(player,Map.of("planks",8L)));
    }
    @Test void changingAmmoEnergyOrOtherModComponentsIsNotAnAcquisition() {
        Map<String,Long> before=Map.of("other_mod:gun{ammo0}",1L);
        assertTrue(RecipeWorldMarketData.positiveDifference(before,
            Map.of("other_mod:gun{ammo30}",1L)).isEmpty());
        assertEquals(Map.of("other_mod:gun{ammo30}",1L),
            RecipeWorldMarketData.positiveDifference(before,Map.of("other_mod:gun{ammo30}",2L)));
    }
    @Test void stockAndPlayerBaselineSurviveSaveAndReload() {
        var data=new RecipeWorldMarketData();
        UUID player=UUID.randomUUID();
        data.observe(player,Map.of("planks",10L));
        data.add("planks",Items.OAK_PLANKS.getDefaultInstance(),10);
        assertEquals("planks",data.purchased(RecipeWorldMarketData.listingId("planks"),3));
        var registries=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var restored=RecipeWorldMarketData.load(data.save(new CompoundTag(),registries),registries);
        assertEquals(7,restored.supply("planks").remaining());
        assertTrue(restored.observe(player,Map.of("planks",10L)).isEmpty());
        restored.add("planks",Items.OAK_PLANKS.getDefaultInstance(),2);
        assertEquals(9,restored.supply("planks").remaining());
        restored.purchased(RecipeWorldMarketData.listingId("planks"),9);
        assertEquals(0,restored.supply("planks").remaining());
    }
    @Test void independentPlayersContributeToTheSameFiniteWorldStock() {
        var data=new RecipeWorldMarketData();
        for (int i=0;i<2;i++) {
            long gained=data.observe(UUID.randomUUID(),Map.of("planks",4L)).get("planks");
            data.add("planks",Items.OAK_PLANKS.getDefaultInstance(),gained);
        }
        assertEquals(8,data.supply("planks").remaining());
        data.removeSupply(RecipeWorldMarketData.listingId("planks"));
        assertNull(data.supply("planks"));
    }
    @Test void additionsCannotOverflowIntoNegativeStock() {
        assertEquals(Long.MAX_VALUE,RecipeWorldMarketData.saturatedAdd(Long.MAX_VALUE-1,5));
    }
}
