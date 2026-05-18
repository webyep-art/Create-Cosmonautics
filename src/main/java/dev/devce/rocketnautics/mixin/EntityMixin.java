package dev.devce.rocketnautics.mixin;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.content.orbit.DeepSpaceInstance;
import dev.devce.rocketnautics.content.physics.GlobalSpacePhysicsHandler;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Shadow
    public abstract Level level();

    @Shadow
    public abstract double getY();

    @Shadow
    public abstract double getX();

    @Shadow
    public abstract double getZ();

    @ModifyReturnValue(method = "getGravity", at = @At("RETURN"))
    private double rocketnautics$applyLowGravity(double original) {
        return original * (1 - GlobalSpacePhysicsHandler.calculateGravityFactor(level(), getY()));
    }

    @Inject(method = "collectColliders", at = @At(value = "RETURN", shift = At.Shift.BEFORE))
    private static void addDeepSpaceCollider(Entity p_344804_, Level p_345583_, List<VoxelShape> p_345198_, AABB p_345837_,
                                             CallbackInfoReturnable<List<VoxelShape>> cir, @Local ImmutableList.Builder<VoxelShape> builder) {
        if (p_344804_ == null || !DeepSpaceData.isDeepSpace(p_345583_)) return;
        builder.add(DeepSpaceData.getColliderForPosition(p_344804_.position()));
    }
}
