package com.xtdpotato.xero_delta.block;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NearbyWarehouseTest {
    @Test
    void usesExistingFiveBlockCubeIncludingItsBoundary() {
        BlockPos center = new BlockPos(100, 64, -100);
        assertTrue(PersonalWarehouseBlock.isNearby(center, center.offset(5, -5, 5)::equals));
        assertFalse(PersonalWarehouseBlock.isNearby(center, center.offset(6, 0, 0)::equals));
        assertFalse(PersonalWarehouseBlock.isNearby(center, ignored -> false));
    }
}
