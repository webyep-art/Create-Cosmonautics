package dev.devce.rocketnautics.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalDoubleRef;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.devce.rocketnautics.RocketConfig;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {

    public LivingEntityMixin(EntityType<?> p_19870_, Level p_19871_) {
        super(p_19870_, p_19871_);
    }

    @WrapOperation(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(DDD)V", ordinal = 3))
    private void rocketnautics$adjustDragByPressure(LivingEntity instance, double x, double y, double z, Operation<Void> original, @Local(ordinal = 1) Vec3 vec35, @Local(ordinal = 1) float f3, @Local(ordinal = 1) double d2) {
        int limit = RocketConfig.SERVER.entitySpeedLimit.get();
        // note that delta movement is in m/t not m/s, so we multiply our speed squared by a correction factor of 400 (20 * 20)
        if (400 * (x * x + y * y + z * z) > limit * limit) {
            original.call(instance, x, y, z);
            return;
        }
        double pressure = DimensionPhysicsData.getAirPressure(level(), new Vector3d(instance.getX(), instance.getY(), instance.getZ()));
        double drag1 = 1 - (1 - f3) * pressure;
        double drag2 = 1 - 0.02 * pressure;
        original.call(instance, vec35.x * drag1, this instanceof FlyingAnimal ? d2 * drag1 : d2 * drag2, vec35.z * drag1);
    }

}
