package dev.devce.rocketnautics.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.content.orbit.DeepSpaceInstance;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubLevelPhysicsSystem.class, remap = false)
public abstract class SubLevelPhysicsSystemMixin {

    @Shadow
    public abstract RigidBodyHandle getPhysicsHandle(@NotNull ServerSubLevel subLevel);

    // we need this correction to occur immediately before the Rapier engine does a physics tick, so we mixin.
    @Inject(method = "tickPipelinePhysics", at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;applyQueuedForces(Ldev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem;Ldev/ryanhcode/sable/api/physics/handle/RigidBodyHandle;D)V", shift = At.Shift.AFTER))
    private void applyVelocityToOrbit(ServerSubLevelContainer container, CallbackInfo ci, @Local(name = "subLevel") ServerSubLevel subLevel) {
        if (!DeepSpaceData.isDeepSpace(container.getLevel())) return;
        Vector3dc position = subLevel.logicalPose().position();
        if (position == null) {
            return;
        }
        DeepSpaceInstance handling = DeepSpaceData.getInstance(container.getLevel().getServer()).getInstanceForPos((int) position.x(), (int) position.z());
        if (handling == null) return;
        Vector3dc center = handling.getCenter();
        Vector3d offset = position.sub(center, new Vector3d());
        RigidBodyHandle handle = this.getPhysicsHandle(subLevel);
        Vector3d correction = handle.getLinearVelocity(new Vector3d());
        if (correction.lengthSquared() <= 1e-10) {
            // just register our mass, skipping any calculation
            handling.applyVelocity(subLevel.getUniqueId(), correction.zero(), subLevel.getMassTracker().getMass());
            return;
        }
        double dot = offset.dot(correction);
        double radiusFactor = 4d / (handling.getSideLength() * handling.getSideLength());
        // part 1 of our correction is a damping factor based on the actual velocity and distance to instance edge.
        // part 2 of our correction is a reduction of the component moving away from the center.
        correction.mulAdd(Math.min(0.8, offset.lengthSquared() * radiusFactor),
                offset.mul(Math.max(0, dot * radiusFactor)));
        handling.applyVelocity(subLevel.getUniqueId(), correction, subLevel.getMassTracker().getMass());
        handle.addLinearAndAngularVelocity(correction.negate(), offset.zero());
    }
}
