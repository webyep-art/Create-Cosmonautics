package dev.devce.rocketnautics.mixin;

import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.devce.rocketnautics.SkyDataHandler;
import dev.devce.rocketnautics.client.StarBufferExposer;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import javax.annotation.Nullable;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin implements StarBufferExposer {
    @org.spongepowered.asm.mixin.Shadow
    protected abstract void renderSnowAndRain(net.minecraft.client.renderer.LightTexture pLightTexture, float pPartialTick, double pCamX, double pCamY, double pCamZ);

    @Shadow
    @Nullable
    private VertexBuffer starBuffer;

    @Override
    public VertexBuffer rocketnautics$starBuffer() {
        return starBuffer;
    }

    @Redirect(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getStarBrightness(F)F"))
    private float rocketnautics$boostStarBrightness(net.minecraft.client.multiplayer.ClientLevel instance, float partialTick) {
        float original = instance.getStarBrightness(partialTick);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return original;

        double y = mc.player.getY() + SkyDataHandler.getHeightOffsetForLevel(mc.level.dimension());
        if (y > 1000.0) {
            float factor = (float) Mth.clamp((y - 1000.0) / 1000.0, 0.0, 1.0);
            
            return Mth.lerp(factor, original, 1.0f);
        }
        return original;
    }

    @Redirect(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;getSkyColor(Lnet/minecraft/world/phys/Vec3;F)Lnet/minecraft/world/phys/Vec3;"))
    private net.minecraft.world.phys.Vec3 rocketnautics$forceBlackSky(net.minecraft.client.multiplayer.ClientLevel instance, net.minecraft.world.phys.Vec3 pos, float partialTick) {
        net.minecraft.world.phys.Vec3 color = instance.getSkyColor(pos, partialTick);

        double y = pos.y + SkyDataHandler.getHeightOffsetForLevel(Minecraft.getInstance().level.dimension());
        if (y > 1000.0) {
            float factor = (float) Mth.clamp((y - 1000.0) / 2000.0, 0.0, 1.0);
            
            return new net.minecraft.world.phys.Vec3(
                Mth.lerp(factor, color.x, 0.0),
                Mth.lerp(factor, color.y, 0.0),
                Mth.lerp(factor, color.z, 0.0)
            );
        }
        return color;
    }

    @Redirect(method = "renderSky", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;getSunriseColor(FF)[F"))
    private float[] rocketnautics$disableSunriseAtAltitude(net.minecraft.client.renderer.DimensionSpecialEffects instance, float angle, float partialTick) {
        float[] color = instance.getSunriseColor(angle, partialTick);
        if (color == null) return null;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getY() > 1000.0) {
            float factor = (float) Mth.clamp((mc.player.getY() - 1000.0) / 1000.0, 0.0, 1.0);
            if (factor >= 1.0f) return null;
            
            float[] faded = color.clone();
            faded[3] *= (1.0f - factor);
            return faded;
        }
        return color;
    }

    
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderClouds(Lcom/mojang/blaze3d/vertex/PoseStack;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;FDDD)V"))
    private void rocketnautics$fadeOutClouds(LevelRenderer instance, com.mojang.blaze3d.vertex.PoseStack pPoseStack, org.joml.Matrix4f pProjectionMatrix, org.joml.Matrix4f pCloudProjectionMatrix, float pPartialTick, double pCamX, double pCamY, double pCamZ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            double y = mc.player.getY();
            
            if (y > 2500.0) return;
        }
        instance.renderClouds(pPoseStack, pProjectionMatrix, pCloudProjectionMatrix, pPartialTick, pCamX, pCamY, pCamZ);
    }

    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSnowAndRain(Lnet/minecraft/client/renderer/LightTexture;FDDD)V"))
    private void rocketnautics$disableWeatherAtAltitude(LevelRenderer instance, net.minecraft.client.renderer.LightTexture pLightTexture, float pPartialTick, double pCamX, double pCamY, double pCamZ) {
        if (pCamY > 400.0) return;
        this.renderSnowAndRain(pLightTexture, pPartialTick, pCamX, pCamY, pCamZ);
    }
}
