package com.xtdpotato.xero_delta.client;

import com.xtdpotato.xero_delta.block.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import com.xtdpotato.xero_delta.network.PlayerStatusPacket;

/** Latest server-authoritative values displayed by the independent player panel. */
public final class PlayerStatusClientState {
    public static final PlayerStatusClientState INSTANCE = new PlayerStatusClientState();

    private volatile long balance;
    private volatile double weightKg;
    private volatile double speedPenalty;
    private volatile double healthPenalty;
    private volatile boolean layoutEnabled;
    private volatile boolean allowChangeBc;
    private volatile boolean layoutClick = true;
    private volatile float head;
    private volatile float chest;
    private volatile float abdomen;
    private volatile float leftArm;
    private volatile float rightArm;
    private volatile float leftLeg;
    private volatile float rightLeg;
    private volatile float wholeBody;

    private PlayerStatusClientState() {
    }

    public void update(PlayerStatusPacket packet) {
        balance = packet.balance();
        weightKg = packet.weightKg();
        speedPenalty = packet.speedPenalty();
        healthPenalty = packet.healthPenalty();
        layoutEnabled = packet.layoutEnabled();
        allowChangeBc = packet.allowChangeBc();
        layoutClick = packet.layoutClick();
        head = packet.head();
        chest = packet.chest();
        abdomen = packet.abdomen();
        leftArm = packet.leftArm();
        rightArm = packet.rightArm();
        leftLeg = packet.leftLeg();
        rightLeg = packet.rightLeg();
        wholeBody = packet.wholeBody();
    }

    public long balance() { return balance; }
    public double weightKg() { return weightKg; }
    public double speedPenalty() { return speedPenalty; }
    public double healthPenalty() { return healthPenalty; }
    public boolean layoutEnabled() { return layoutEnabled; }
    public boolean allowChangeBc() { return allowChangeBc; }

    public boolean canChangeBc() {
        if (allowChangeBc) return true;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) return false;
        BlockPos center = minecraft.player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(
            center.offset(-5, -5, -5), center.offset(5, 5, 5))) {
            if (minecraft.level.getBlockState(pos).is(ModBlocks.PERSONAL_WAREHOUSE.get())) {
                return true;
            }
        }
        return false;
    }
    public boolean layoutClick() { return layoutClick; }
    public float head() { return head; }
    public float chest() { return chest; }
    public float abdomen() { return abdomen; }
    public float leftArm() { return leftArm; }
    public float rightArm() { return rightArm; }
    public float leftLeg() { return leftLeg; }
    public float rightLeg() { return rightLeg; }
    public float wholeBody() { return wholeBody; }

    public boolean overloaded() { return weightKg >= 88.0D; }
    public boolean encumbered() { return weightKg >= 50.0D; }
}
