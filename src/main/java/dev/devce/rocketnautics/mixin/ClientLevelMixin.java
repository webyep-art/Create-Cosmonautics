package dev.devce.rocketnautics.mixin;

import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.client.DeepSpaceHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.Vec3;
import org.orekit.time.AbsoluteDate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Supplier;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin extends Level {

    protected ClientLevelMixin(WritableLevelData p_270739_, ResourceKey<Level> p_270683_, RegistryAccess p_270200_, Holder<DimensionType> p_270240_, Supplier<ProfilerFiller> p_270692_, boolean p_270904_, boolean p_270470_, long p_270248_, int p_270466_) {
        super(p_270739_, p_270683_, p_270200_, p_270240_, p_270692_, p_270904_, p_270470_, p_270248_, p_270466_);
    }

    @Shadow
    @Final
    private ClientLevel.ClientLevelData clientLevelData;

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void rocketnautics$darkenSkyAtAltitude(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        if (DeepSpaceHelper.isDeepSpace(this)) {
            cir.setReturnValue(new Vec3(0, 0, 0));
            return;
        }
        if (DeepSpaceHandler.getUniverse() != null && DeepSpaceHelper.shouldOverrideLevelTime(this)) {
            cir.setReturnValue(new Vec3(0, 0, 0));
            return;
        }
        double y = pos.y; 

        if (y > 800) {
            float factor = (float) Mth.clamp((y - 1000.0) / 2000.0, 0.0, 1.0);
            if (factor <= 0.0f) return;

            Vec3 originalColor = cir.getReturnValue();
            double r = Mth.lerp(factor, originalColor.x, 0.0);
            double g = Mth.lerp(factor, originalColor.y, 0.0);
            double b = Mth.lerp(factor, originalColor.z, 0.0);
            
            cir.setReturnValue(new Vec3(r, g, b));
        }
    }

    // prevent flickering behavior due to server -> client sync packets
    @Inject(method = "setDayTime", at = @At("HEAD"), cancellable = true)
    private void cancelSetDayTime(long p_104747_, CallbackInfo ci) {
        if (DeepSpaceHandler.getUniverse() == null) return;
        if (DeepSpaceHelper.shouldOverrideLevelTime(this)) {
            ci.cancel();
        }
    }

    @Inject(method = "tickTime", at = @At("RETURN"))
    private void controlDayTime(CallbackInfo ci) {
        if (DeepSpaceHandler.getUniverse() == null) return;
        AbsoluteDate predictedDate = DeepSpaceHandler.getPredictedUniverseDate(0);
        if (predictedDate == null) return;
        DeepSpaceHelper.checkAndOverrideLevelTime(DeepSpaceHandler.getUniverse(), predictedDate, this, this.clientLevelData::setDayTime);
    }
}
