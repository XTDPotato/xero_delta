package com.xtdpotato.xero_delta.api;

import com.xtdpotato.xero_delta.grid.GridBackingStore;

public interface GridMenuAccessor {
    GridBackingStore deltaSafetyBox$getGridStore();
    void deltaSafetyBox$setGridStore(GridBackingStore store);
}