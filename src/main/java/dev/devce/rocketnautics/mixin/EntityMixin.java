package dev.devce.rocketnautics.mixin;

import com.google.common.collect.ImmutableList;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.content.orbit.universe.PlanetDimensionData;
import dev.devce.rocketnautics.content.physics.GlobalSpacePhysicsHandler;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
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
        if (DeepSpaceHelper.getDataForDimension(level()).map(PlanetDimensionData::applyGravityCorrectionToEntities).orElse(false)) {
            double gravity = DimensionPhysicsData.getGravity(level()).y();
            double normalGravity = -11f;
            original *= gravity / normalGravity;
        }
        return original * (1 - GlobalSpacePhysicsHandler.calculateGravityFactor(level(), getY()));
    }

    @Inject(method = "collectColliders", at = @At(value = "RETURN", shift = At.Shift.BEFORE))
    private static void addDeepSpaceCollider(Entity p_344804_, Level p_345583_, List<VoxelShape> p_345198_, AABB p_345837_,
                                             CallbackInfoReturnable<List<VoxelShape>> cir, @Local ImmutableList.Builder<VoxelShape> builder) {
        if (p_344804_ == null || !DeepSpaceHelper.isDeepSpace(p_345583_)) return;
        builder.add(DeepSpaceData.getColliderForPosition(p_344804_.position()));
    }
}
