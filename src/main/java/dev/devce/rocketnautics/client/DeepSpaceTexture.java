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
    private static final int TEX_SIZE = 1024;

    private static final int[] warpSampling = new int[TEX_SIZE * TEX_SIZE];

    static {
        int dataSize = 256;
        for (int x = 0; x < TEX_SIZE; x++) {
            for (int y = 0; y < TEX_SIZE; y++) {
                double u = (x / (double)TEX_SIZE) * dataSize;
                double v = (y / (double)TEX_SIZE) * dataSize;

                double nx = x * 0.05;
                double ny = y * 0.05;
                double warpX = (Math.sin(nx) + 0.5 * Math.sin(nx * 2.1)) * 1.5;
                double warpY = (Math.cos(ny) + 0.5 * Math.cos(ny * 2.1)) * 1.5;

                int sampleX = (int) Math.round(u + warpX);
                int sampleY = (int) Math.round(v + warpY);

                if (sampleX < 0) sampleX = 0;
                if (sampleX >= dataSize) sampleX = dataSize - 1;
                if (sampleY < 0) sampleY = 0;
                if (sampleY >= dataSize) sampleY = dataSize - 1;

                warpSampling[x + y * TEX_SIZE] = sampleX + sampleY * dataSize;
            }
        }
    }

    private final DynamicTexture tex; // keep this just to make sure nothing garbage-collector shaped happens to it
    private final ResourceLocation id;

    public DeepSpaceTexture(DynamicTexture tex, ResourceLocation id) {
        this.tex = tex;
        this.id = id;
    }

    public static DeepSpaceTexture construct(int renderID, byte[] renderData) {
        Minecraft mc = Minecraft.getInstance();

        NativeImage image = new NativeImage(TEX_SIZE, TEX_SIZE, false);

        for (int x = 0; x < TEX_SIZE; x++) {
            for (int y = 0; y < TEX_SIZE; y++) {
                image.setPixelRGBA(x, y, PlanetColors.getPackedColor(renderData[warpSampling[x + y * TEX_SIZE]]));
            }
        }

        DynamicTexture constructed = new DynamicTexture(image);
        ResourceLocation claimed = mc.getTextureManager().register("rocketnautics_deep_space_planet", constructed);
        constructed.setFilter(true, false);
        image.close();
        return new DeepSpaceTexture(constructed, claimed);
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
