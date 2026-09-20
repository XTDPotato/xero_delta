package com.xtdpotato.xero_delta.trading;
import com.xtdpotato.xero_delta.network.TradingSyncPacket;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RecipeMarketListingTest {
    @Test void legacyWorldListingsCanStillReceiveAnAutomaticPublicNumber() {
        var market=new TradingMarketData();
        var listing=new TradingListing(UUID.randomUUID(),TradingMarketData.WORLD_ACCOUNT_ID,"world",
            Items.STICK.getDefaultInstance(),1,1,0,null,true,100);
        assertFalse(RecipeWorldMarketData.isRecipeListing(listing));
        assertNotNull(market.putListing(listing));
    }
    private static TradingListing auto(String key,int count) {
        return new TradingListing(RecipeWorldMarketData.listingId(key),TradingMarketData.WORLD_ACCOUNT_ID,
            "world",Items.OAK_PLANKS.getDefaultInstance().copyWithCount(count),count,count,0,
            RecipeWorldMarketData.publicListingId(key),true,Long.MAX_VALUE/4);
    }
    @Test void automaticSupplyDoesNotUseTheOrdinaryListingQuota() {
        var market=new TradingMarketData();
        market.setMaxMarketListings(1);
        assertNotNull(market.putListing(auto("first",3)));
        assertTrue(market.hasListingCapacity());
        assertNotNull(market.putListing(new TradingListing(UUID.randomUUID(),UUID.randomUUID(),"player",
            Items.STICK.getDefaultInstance(),1,1,0,"normal",false,100)));
        assertFalse(market.hasListingCapacity());
        assertNotNull(market.putListing(auto("second",7)));
    }
    @Test void globalPacketCapacityReservesRoomForOrdinaryListings() {
        var market=new TradingMarketData();
        market.setMaxMarketListings(TradingRules.MAX_SYNC_LISTINGS-1);
        assertNotNull(market.putListing(auto("first",3)));
        assertNull(market.putListing(auto("second",7)));
        assertTrue(market.hasListingCapacity());
    }
    @Test void largeStockAndPublicNumberRoundTripThroughTheActualMarketPacket() {
        var listing=auto("planks",10_000);
        var view=new TradingSyncPacket.ListingView(listing.id(),listing.sellerId(),"world",
            listing.stack(),listing.price(),listing.itemValue(),0,listing.durationTicks(),false,
            listing.publicId(),true);
        var packet=new TradingSyncPacket(0,72,255,6,12,5,List.of(view),
            List.of(),List.of(),List.of(),Set.of(),"",true,0,"");
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        try {
            TradingSyncPacket.STREAM_CODEC.encode(buffer,packet);
            var decoded=TradingSyncPacket.STREAM_CODEC.decode(buffer);
            assertEquals(10_000,decoded.listings().getFirst().stack().getCount());
            assertEquals(listing.publicId(),decoded.listings().getFirst().publicId());
            assertEquals(10_000,decoded.listings().getFirst().price());
            assertFalse(decoded.listings().getFirst().expired());
        } finally { buffer.release(); }
    }
}
