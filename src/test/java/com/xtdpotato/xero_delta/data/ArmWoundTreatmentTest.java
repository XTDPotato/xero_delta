package com.xtdpotato.xero_delta.data;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ArmWoundTreatmentTest {
    @Test void otherBodyWoundsNeverMakeCatEligible() {
        assertNull(PlayerInjuryManager.armWound(new PlayerInjuryManager.Snapshot(100,100,100,0,0,100,100,100)));
        assertNull(PlayerInjuryManager.armWound(new PlayerInjuryManager.Snapshot(0,0,0,99,99,0,0,0)));
    }
    @Test void oneArmIsSelectedAndSecondArmRemainsForNextUse() {
        assertEquals(PlayerInjuryManager.BodyPart.LEFT_ARM, PlayerInjuryManager.armWound(
            new PlayerInjuryManager.Snapshot(0,0,0,100,100,0,0,0)));
        assertEquals(PlayerInjuryManager.BodyPart.RIGHT_ARM, PlayerInjuryManager.armWound(
            new PlayerInjuryManager.Snapshot(0,0,0,0,100,0,0,0)));
    }
}

