package dev.devce.rocketnautics.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.Function;

@OnlyIn(Dist.CLIENT)
public final class DeepSpaceTexture {
    private final DynamicTexture tex; // keep this just to make sure nothing garbage-collector shaped happens to it
    private final ResourceLocation id;

    public DeepSpaceTexture(DynamicTexture tex, ResourceLocation id) {
        this.tex = tex;
        this.id = id;
    }

    public static DeepSpaceTexture construct(int renderID, byte[] renderData) {
        Minecraft mc = Minecraft.getInstance();

        NativeImage image = SkyHandler.composePlanetTexture(256, (x, y) -> renderData[x + y * 256]);

        DynamicTexture constructed = new DynamicTexture(image);
        ResourceLocation claimed = mc.getTextureManager().register("rocketnautics_deep_space_planet", constructed);
        constructed.setFilter(false, false);
        image.close();
        return new DeepSpaceTexture(constructed, claimed);
    }

    public void retire() {
        Minecraft.getInstance().getTextureManager().release(id);
    }

    public ResourceLocation getId() {
        return id;
    }

    public void setShaderTexture() {
        RenderSystem.setShaderTexture(0, getId());
    }

    public RenderType attachType(Function<ResourceLocation, RenderType> renderType) {
        return renderType.apply(getId());
    }
}
