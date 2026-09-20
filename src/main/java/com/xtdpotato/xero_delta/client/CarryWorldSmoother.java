package com.xtdpotato.xero_delta.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.WeakHashMap;

/** Keeps server-authoritative carry teleports out of the rendered motion. */
final class CarryWorldSmoother {
    private static final Map<Entity, MotionState> STATES = new WeakHashMap<>();

    private CarryWorldSmoother() {
    }

    static void apply(Entity carried, Entity carrier, byte phase, float progress,
                      float partialTick, PoseStack pose) {
        Vec3 raw = interpolatedPosition(carried, partialTick);
        MotionState state = STATES.computeIfAbsent(carried, ignored -> new MotionState(raw));
        if (carrier == null) {
            state.observe(raw);
            return;
        }
        if (phase == 0) {
            // Passenger/entity metadata can arrive before the carry-state packet.
            // Hold the previous render point so that one packet-order frame cannot
            // replace the pickup anchor with the carrier's feet.
            pose.translate(state.lastRendered.x - raw.x,
                state.lastRendered.y - raw.y, state.lastRendered.z - raw.z);
            return;
        }

        Vec3 attached = shoulderBase(carrier, partialTick);
        if (state.phase != phase) {
            if (phase == 1) state.pickupAnchor = state.lastRendered;
            if (phase == 3) {
                state.dropAnchor = state.phase == 2 ? state.lastRendered : attached;
                state.dropTarget = dropPosition(carrier, partialTick);
            }
            state.phase = phase;
        }

        Vec3 desired = switch (phase) {
            case 1 -> state.pickupAnchor.lerp(attached, progress);
            case 3 -> state.dropAnchor.lerp(state.dropTarget, 1.0F - progress);
            default -> attached;
        };
        pose.translate(desired.x - raw.x, desired.y - raw.y, desired.z - raw.z);
        state.lastRendered = desired;
    }

    /*
     * This renderer deliberately keeps its carry math client-local. The crash
     * report showed a class-loader failure while resolving the shared data
     * helper during world rendering; using the small vanilla-only equivalent
     * here prevents a visual effect from taking down the client.
     */
    private static Vec3 interpolatedPosition(Entity entity, float partialTick) {
        return new Vec3(
            Mth.lerp(partialTick, entity.xo, entity.getX()),
            Mth.lerp(partialTick, entity.yo, entity.getY()),
            Mth.lerp(partialTick, entity.zo, entity.getZ()));
    }

    private static Vec3 shoulderBase(Entity carrier, float partialTick) {
        Vec3 position = interpolatedPosition(carrier, partialTick);
        float yaw = Mth.rotLerp(partialTick, carrier.yRotO, carrier.getYRot());
        double radians = Math.toRadians(yaw);
        double forwardX = -Math.sin(radians);
        double forwardZ = Math.cos(radians);
        return position.add(-forwardZ * 0.48D, 0.50D, forwardX * 0.48D);
    }

    private static Vec3 dropPosition(Entity carrier, float partialTick) {
        Vec3 position = interpolatedPosition(carrier, partialTick);
        float yaw = Mth.rotLerp(partialTick, carrier.yRotO, carrier.getYRot());
        double radians = Math.toRadians(yaw);
        double x = position.x - Math.sin(radians) * 0.50D;
        double z = position.z + Math.cos(radians) * 0.50D;
        Vec3 start = new Vec3(x, position.y + carrier.getEyeHeight() + 0.50D, z);
        Vec3 end = new Vec3(x, position.y - 3.0D, z);
        HitResult hit = carrier.level().clip(new ClipContext(start, end,
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, carrier));
        double y = hit.getType() == HitResult.Type.BLOCK
            ? hit.getLocation().y + 0.01D : position.y;
        return new Vec3(x, y, z);
    }

    private static final class MotionState {
        private byte phase;
        private Vec3 pickupAnchor;
        private Vec3 dropAnchor;
        private Vec3 dropTarget;
        private Vec3 lastRendered;

        private MotionState(Vec3 position) {
            pickupAnchor = position;
            dropAnchor = position;
            dropTarget = position;
            lastRendered = position;
        }

        private void observe(Vec3 position) {
            phase = 0;
            pickupAnchor = position;
            dropAnchor = position;
            dropTarget = position;
            lastRendered = position;
        }
    }
}
