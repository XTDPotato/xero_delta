package com.xtdpotato.xero_delta.data;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Resolves the carried base position and the ground point used by a drop animation. */
public final class CarryPositionResolver {
    public static final double CARRIED_BASE_LIFT = 0.50D;

    private CarryPositionResolver() {
    }

    public static Vec3 shoulderBase(Entity carrier, float partialTick) {
        Vec3 position = interpolatedPosition(carrier, partialTick);
        var offset = CarryPoseMath.shoulderOffset(interpolatedYaw(carrier, partialTick));
        return position.add(offset.x(), CARRIED_BASE_LIFT, offset.z());
    }

    public static Vec3 dropPosition(Entity carrier, float partialTick) {
        Vec3 carrierPosition = interpolatedPosition(carrier, partialTick);
        var offset = CarryPoseMath.dropOffset(interpolatedYaw(carrier, partialTick));
        double x = carrierPosition.x + offset.x();
        double z = carrierPosition.z + offset.z();
        Vec3 start = new Vec3(x, carrierPosition.y + carrier.getEyeHeight() + 0.50D, z);
        Vec3 end = new Vec3(x, carrierPosition.y - 3.0D, z);
        var hit = carrier.level().clip(new ClipContext(start, end,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, carrier));
        double y = hit.getType() == HitResult.Type.BLOCK
            ? hit.getLocation().y + 0.01D : carrierPosition.y;
        return new Vec3(x, y, z);
    }

    public static Vec3 interpolatedPosition(Entity entity, float partialTick) {
        return new Vec3(
            Mth.lerp(partialTick, entity.xo, entity.getX()),
            Mth.lerp(partialTick, entity.yo, entity.getY()),
            Mth.lerp(partialTick, entity.zo, entity.getZ()));
    }

    private static float interpolatedYaw(Entity entity, float partialTick) {
        return Mth.rotLerp(partialTick, entity.yRotO, entity.getYRot());
    }
}
