

// Webhook test commit
package dev.devce.rocketnautics.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.devce.rocketnautics.RocketConfig;
import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.SkyDataHandler;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.content.orbit.universe.PlanetExtras;
import dev.devce.rocketnautics.network.PlanetMapRequestPayload;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles custom sky rendering for high altitudes and the space dimension.
 * This includes atmospheric fog color adjustments, procedural planet rendering,
 * and dynamic planet map texture management.
 */
@EventBusSubscriber(modid = RocketNautics.MODID, value = Dist.CLIENT)
public class SkyHandler {
    public static final float SKYBOX_DISTANCE = 100; // minecraft renders everything in the default skybox at 100 blocks out
    public static boolean debugSonicBoom = false;
    
    /**
     * Adjusts the fog color towards black as the player ascends into space.
     */
    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        double y = mc.gameRenderer.getMainCamera().getPosition().y + SkyDataHandler.getHeightOffsetForLevel(mc.level.dimension());
        if (y > 1000.0) {
            // Gradually fade fog to black above 1000m
            float factor = (float) Mth.clamp((y - 1000.0) / 1000.0, 0.0, 1.0);
            
            event.setRed(Mth.lerp(factor, event.getRed(), 0.0f));
            event.setGreen(Mth.lerp(factor, event.getGreen(), 0.0f));
            event.setBlue(Mth.lerp(factor, event.getBlue(), 0.0f));
        }
    }

    /**
     * Renders the custom planet geometry after the vanilla sky has been drawn.
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            renderVaporCones(event);
            return;
        }

        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || DeepSpaceHelper.isDeepSpace()) return;

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.mulPose(event.getModelViewMatrix());
        Camera camera = event.getCamera();

        DeepSpaceHandler.renderUniverseForLevel(mc.level.dimension(), camera.getPosition(), event.getPoseStack(), event.getPartialTick().getGameTimeDeltaTicks(), event.getPartialTick().getGameTimeDeltaPartialTick(true), camera);

        double camY = camera.getPosition().y + SkyDataHandler.getHeightOffsetForLevel(mc.level.dimension());
        if (camY < 1000.0) return;

        // Determine visibility based on altitude
        float visibility = (float) Mth.clamp((camY - 1000.0) / 500.0, 0.0, 1.0);
        if (visibility <= 0) return;

        float celestialAngle = mc.level.getTimeOfDay(event.getPartialTick().getGameTimeDeltaTicks());
        
        // Render custom high-fidelity space stars rotating naturally with the camera!
        renderSpaceStars(poseStack, visibility, camera, celestialAngle);

        Matrix4f matrix = poseStack.last().pose();

        double camX = camera.getPosition().x;
        double camZ = camera.getPosition().z;

        // Ensure all procedural textures are initialized
        ensurePlanetTexObj();
        ensureCloudTexture();
        ensureHaloTexture();

        // Request map updates from server if player moved too far
        updatePlanetTex(camX, camY, camZ);
        
        // Cross-fade between old and new planet map textures
        if (texFade > 0) {
            texFade = Math.max(0, texFade - event.getPartialTick().getRealtimeDeltaTicks() / 20);
        }

        // Render planet with layered effects (Map + Clouds + Halo)
        renderPlanet(PLANET_TEXTURE_OBJ_LAST, camX, camY, camZ, matrix, texFade * visibility, celestialAngle);
        renderPlanet(PLANET_TEXTURE_OBJ, camX, camY, camZ, matrix, (1 - texFade) * visibility, celestialAngle);
        poseStack.popPose();
    }

    /**
     * Renders the planet quad with Map, Clouds, and Halo layers.
     */
    private static void renderPlanet(PlanetRenderInfo planet, double camX, double camY, double camZ, Matrix4f matrix, float visibility, float celestialAngle) {
        if (visibility <= 0) return;
        PlanetExtras extras = Optional.ofNullable(Minecraft.getInstance().level).flatMap(DeepSpaceHelper::getExtrasForDimension).orElse(null);

        float prettyness = computePrettyness(planet, camY);

        // Determine quad size based on altitude and scale factor
        double trueSize = SkyDataHandler.toTrueSize(planet.getPowerSize());
        double optimalSize = camY * (2 << SkyDataHandler.SCALE_FACTOR);
        double result = Math.min(prettyness > 0 ? optimalSize : trueSize, SkyDataHandler.toTrueSize(SkyDataHandler.MAX_POWER_SIZE));
        float parallaxFactor = (float) Math.min(SKYBOX_DISTANCE / Math.max(1, result), SKYBOX_DISTANCE / Math.max(1, Math.pow(2, SkyDataHandler.targetSizeForHeightContinuous(camY) + 1)));
        float size = (float) (result * parallaxFactor);

        // Calculate relative position based on parallax
        float relX = (float) ((planet.getCenterX() - camX) * parallaxFactor);
        float relY = -SKYBOX_DISTANCE / 8; // Render "below" the player
        float relZ = (float) ((planet.getCenterZ() - camZ) * parallaxFactor);
        
        relX = Mth.lerp(prettyness, relX, 0);
        relZ = Mth.lerp(prettyness, relZ, 0);

        // Setup rendering state
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        // --- Layer 1: Planet Surface Map ---
        if (planet.getTexID() != null) {
            RenderSystem.setShaderTexture(0, planet.getTexID());
        } else {
            RenderSystem.setShaderTexture(0, ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "textures/environment/planet_map.png"));
        }
        RenderSystem.disableCull();

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        float r = 1.0f, g = 1.0f, b = 1.0f;
        bufferbuilder.addVertex(matrix, relX - size, relY, relZ - size).setColor(r, g, b, visibility).setUv(0.0f, 0.0f);
        bufferbuilder.addVertex(matrix, relX - size, relY, relZ + size).setColor(r, g, b, visibility).setUv(0.0f, 1.0f);
        bufferbuilder.addVertex(matrix, relX + size, relY, relZ + size).setColor(r, g, b, visibility).setUv(1.0f, 1.0f);
        bufferbuilder.addVertex(matrix, relX + size, relY, relZ - size).setColor(r, g, b, visibility).setUv(1.0f, 0.0f);
        BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());

        if (extras != null && extras.clouds()) {
            // --- Layer 1.8: Cloud Shadows (Floating offset shadow) ---
            double theta = 2.0 * Math.PI * celestialAngle;
            float lx = (float) -Math.sin(theta);
            if (CLOUD_TEXTURE_ID != null) {
                RenderSystem.setShaderTexture(0, CLOUD_TEXTURE_ID);
                long factor = 1000L * SkyDataHandler.toTrueSize(planet.getPowerSize() / 2);
                float timeOffset = (System.currentTimeMillis() % (20L * factor)) / (float) factor;

                float shadowShift = lx * size * 0.08f; // Dynamic shadow offset based on solar angle

                BufferBuilder shadowBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                // Deep transparent dark space shadow color
                float sr = 0.01f, sg = 0.02f, sb = 0.08f, sa = visibility * 0.48f;
                shadowBuilder.addVertex(matrix, relX - size + shadowShift, relY, relZ - size).setColor(sr, sg, sb, sa).setUv(0.0f + timeOffset, 0.0f);
                shadowBuilder.addVertex(matrix, relX - size + shadowShift, relY, relZ + size).setColor(sr, sg, sb, sa).setUv(0.0f + timeOffset, 1.0f);
                shadowBuilder.addVertex(matrix, relX + size + shadowShift, relY, relZ + size).setColor(sr, sg, sb, sa).setUv(1.0f + timeOffset, 1.0f);
                shadowBuilder.addVertex(matrix, relX + size + shadowShift, relY, relZ - size).setColor(sr, sg, sb, sa).setUv(1.0f + timeOffset, 0.0f);
                BufferUploader.drawWithShader(shadowBuilder.buildOrThrow());
            }

            // --- Layer 2: Scrolling Clouds ---
            if (CLOUD_TEXTURE_ID != null) {
                RenderSystem.setShaderTexture(0, CLOUD_TEXTURE_ID);
                long factor = 1000L * SkyDataHandler.toTrueSize(planet.getPowerSize() / 2);
                float timeOffset = (System.currentTimeMillis() % (20L * factor)) / (float) factor;

                BufferBuilder cloudBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                cloudBuilder.addVertex(matrix, relX - size, relY, relZ - size).setColor(1.0f, 1.0f, 1.0f, visibility).setUv(0.0f + timeOffset, 0.0f);
                cloudBuilder.addVertex(matrix, relX - size, relY, relZ + size).setColor(1.0f, 1.0f, 1.0f, visibility).setUv(0.0f + timeOffset, 1.0f);
                cloudBuilder.addVertex(matrix, relX + size, relY, relZ + size).setColor(1.0f, 1.0f, 1.0f, visibility).setUv(1.0f + timeOffset, 1.0f);
                cloudBuilder.addVertex(matrix, relX + size, relY, relZ - size).setColor(1.0f, 1.0f, 1.0f, visibility).setUv(1.0f + timeOffset, 0.0f);
                BufferUploader.drawWithShader(cloudBuilder.buildOrThrow());
            }
        }

        // --- Layer 2.5: Pixelated Light, Shadow & Atmospheric Crescent Glow Overlay ---
        ensureLightOverlayTexture(celestialAngle);
        if (LIGHT_OVERLAY_TEXTURE_ID != null) {
            RenderSystem.setShaderTexture(0, LIGHT_OVERLAY_TEXTURE_ID);
            BufferBuilder lightBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            lightBuilder.addVertex(matrix, relX - size, relY, relZ - size).setColor(1.0f, 1.0f, 1.0f, visibility).setUv(0.0f, 0.0f);
            lightBuilder.addVertex(matrix, relX - size, relY, relZ + size).setColor(1.0f, 1.0f, 1.0f, visibility).setUv(0.0f, 1.0f);
            lightBuilder.addVertex(matrix, relX + size, relY, relZ + size).setColor(1.0f, 1.0f, 1.0f, visibility).setUv(1.0f, 1.0f);
            lightBuilder.addVertex(matrix, relX + size, relY, relZ - size).setColor(1.0f, 1.0f, 1.0f, visibility).setUv(1.0f, 0.0f);
            BufferUploader.drawWithShader(lightBuilder.buildOrThrow());
        }

        // --- Layer 3: Atmospheric Halo (Glow) ---
        if (HALO_TEXTURE_ID != null) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE); // Additive blend
            RenderSystem.setShaderTexture(0, HALO_TEXTURE_ID);

            // Calculate dynamic atmosphere coloring based on time of day (Coriolis/Solar synchronization)
            double tAngle = celestialAngle * 2.0 * Math.PI;
            float sunIntensity = (float) Math.cos(tAngle);
            float sideIntensity = (float) Math.abs(Math.sin(tAngle));
            
            float haloR, haloG, haloB;
            if (sunIntensity > 0) {
                // Day: interpolate from bright cyan/sky blue to fiery orange sunset
                float t = sideIntensity; // 0 at noon -> 1 at sunset/sunrise
                haloR = Mth.lerp(t, 0.40f, 1.00f);
                haloG = Mth.lerp(t, 0.70f, 0.42f);
                haloB = Mth.lerp(t, 1.00f, 0.15f);
            } else {
                // Night: interpolate from fiery orange sunset to deep space violet at midnight
                float t = -sunIntensity; // 0 at sunset/sunrise -> 1 at midnight
                haloR = Mth.lerp(t, 1.00f, 0.18f);
                haloG = Mth.lerp(t, 0.42f, 0.08f);
                haloB = Mth.lerp(t, 0.15f, 0.45f);
            }

            float haloSize = size * 1.3f;
            BufferBuilder haloBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            haloBuilder.addVertex(matrix, relX - haloSize, relY, relZ - haloSize).setColor(haloR, haloG, haloB, visibility).setUv(0.0f, 0.0f);
            haloBuilder.addVertex(matrix, relX - haloSize, relY, relZ + haloSize).setColor(haloR, haloG, haloB, visibility).setUv(0.0f, 1.0f);
            haloBuilder.addVertex(matrix, relX + haloSize, relY, relZ + haloSize).setColor(haloR, haloG, haloB, visibility).setUv(1.0f, 1.0f);
            haloBuilder.addVertex(matrix, relX + haloSize, relY, relZ - haloSize).setColor(haloR, haloG, haloB, visibility).setUv(1.0f, 0.0f);
            BufferUploader.drawWithShader(haloBuilder.buildOrThrow());

            RenderSystem.defaultBlendFunc();
        }

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        
        double y = mc.gameRenderer.getMainCamera().getPosition().y + SkyDataHandler.getHeightOffsetForLevel(mc.level.dimension());
        if (y > 1000.0) {
            float factor = (float) Mth.clamp((y - 1000.0) / 1000.0, 0.0, 1.0);
            
            float start = event.getNearPlaneDistance();
            float end = event.getFarPlaneDistance();
            
            event.setNearPlaneDistance(Mth.lerp(factor, start, 1000.0f));
            event.setFarPlaneDistance(Mth.lerp(factor, end, 2000.0f));
            event.setCanceled(true);
        }
    }

    private static PlanetRenderInfo PLANET_TEXTURE_OBJ_LAST = null;
    private static PlanetRenderInfo PLANET_TEXTURE_OBJ = null;
    private static float texFade = 0;
    private static boolean awaitUpdate = false;

    
    private static float computePrettyness(PlanetRenderInfo planet, double camY) {
        double continuousSize = SkyDataHandler.targetSizeForHeightContinuous(camY);
        if (continuousSize <= getMaximumScale() || continuousSize <= planet.getPowerSize()) {
            return 0;
        } else if (continuousSize >= SkyDataHandler.MAX_POWER_SIZE) {
            
            
            return 1;
        } else {
            return (float) (1 - 1 / (1 + continuousSize - planet.getPowerSize()));
        }
    }

    public static int getMaximumScale() {
        return RocketConfig.CLIENT.planetRenderMaximumScale.getAsInt();
    }

    private static void updatePlanetTex(double camX, double camY, double camZ) {
        if (awaitUpdate) return;
        
        boolean clamped = SkyDataHandler.targetSizeForHeightContinuous(camY) > getMaximumScale();
        double currentSize = clamped ? camY * (2 << SkyDataHandler.SCALE_FACTOR) : SkyDataHandler.toTrueSize(PLANET_TEXTURE_OBJ.getPowerSize());
        boolean violateX = Math.abs(camX - PLANET_TEXTURE_OBJ.getCenterX()) > currentSize * 3/5;
        boolean violateZ = Math.abs(camZ - PLANET_TEXTURE_OBJ.getCenterZ()) > currentSize * 3/5;
        boolean violateScale = PLANET_TEXTURE_OBJ.getPowerSize() != Math.min(SkyDataHandler.targetSizeForHeight(camY), getMaximumScale());
        if (violateX || violateZ || violateScale) {
            awaitUpdate = true;
            PacketDistributor.sendToServer(new PlanetMapRequestPayload(SkyDataHandler.targetSizeForHeight(camY)));
        }
    }

    private static void ensurePlanetTexObj() {
        if (PLANET_TEXTURE_OBJ == null) {
            Minecraft mc = Minecraft.getInstance();

            int size = 1024;
            NativeImage image = new NativeImage(size, size, false);

            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    int color = (255 << 24) | (80 << 16) | (40 << 8) | 10;
                    image.setPixelRGBA(x, y, color);
                }
            }

            DynamicTexture tex = new DynamicTexture(image);
            ResourceLocation id = mc.getTextureManager().register("rocketnautics_planet_main", tex);
            tex.setFilter(true, false);
            PLANET_TEXTURE_OBJ = new PlanetRenderInfo(id, tex);
            image.close();
        }
        if (PLANET_TEXTURE_OBJ_LAST == null) {
            Minecraft mc = Minecraft.getInstance();

            int size = 1024;
            NativeImage image = new NativeImage(size, size, false);

            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    int color = (255 << 24) | (80 << 16) | (40 << 8) | 10;
                    image.setPixelRGBA(x, y, color);
                }
            }

            DynamicTexture tex = new DynamicTexture(image);
            ResourceLocation id = mc.getTextureManager().register("rocketnautics_planet_last", tex);
            tex.setFilter(true, false);
            PLANET_TEXTURE_OBJ_LAST = new PlanetRenderInfo(id, tex);
            image.close();
        }
    }

    public static void updatePlanetTexture(int powerSize, int centerX, int centerZ, byte[] mapDataPosXPosZ, byte[] mapDataPosXNegZ, byte[] mapDataNegXPosZ, byte[] mapDataNegXNegZ) {
        PlanetRenderInfo updating = PLANET_TEXTURE_OBJ_LAST;
        PLANET_TEXTURE_OBJ_LAST = PLANET_TEXTURE_OBJ;
        PLANET_TEXTURE_OBJ = updating;
        texFade = 1;

        PLANET_TEXTURE_OBJ.setPowerSize(powerSize);
        PLANET_TEXTURE_OBJ.setCenterX(centerX);
        PLANET_TEXTURE_OBJ.setCenterZ(centerZ);
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (PLANET_TEXTURE_OBJ == null) return;

            NativeImage image = composePlanetTexture(512, (x, y) -> getBiomeAt(x, y, mapDataPosXPosZ, mapDataPosXNegZ, mapDataNegXPosZ, mapDataNegXNegZ));

            PLANET_TEXTURE_OBJ.getTexture().setPixels(image);
            PLANET_TEXTURE_OBJ.getTexture().upload();
            PLANET_TEXTURE_OBJ.getTexture().setFilter(false, false);
            image.close();
            awaitUpdate = false;
        });
    }

    public static NativeImage composePlanetTexture(int renderDataSize, RenderDataSelector selector) {
        int texSize = 1024;
        int virtualSize = 256; // virtual size for a gorgeous clean retro pixel art style
        int blockSize = texSize / virtualSize; // 4x4 blocks in the 1024x1024 texture

        NativeImage image = new NativeImage(texSize, texSize, false);
        byte[] virtualBiomes = new byte[virtualSize * virtualSize];

        // Pass 1: Compute smoothed metaball-like biomes on the virtual grid
        for (int vy = 0; vy < virtualSize; vy++) {
            for (int vx = 0; vx < virtualSize; vx++) {
                double u = (vx / (double)virtualSize) * renderDataSize;
                double v = (vy / (double)virtualSize) * renderDataSize;

                // Smooth wave distortion for organic borders
                double nx = u * 0.1;
                double ny = v * 0.1;
                double warpX = (Math.sin(nx) + 0.5 * Math.sin(nx * 2.1)) * 1.5;
                double warpY = (Math.cos(ny) + 0.5 * Math.cos(ny * 2.1)) * 1.5;
                double wu = u + warpX;
                double wv = v + warpY;

                int cx = (int) Math.round(wu);
                int cy = (int) Math.round(wv);

                float[] influence = new float[PlanetColors.getReservedCount()];
                double radius = 3.2;
                double radiusSq = radius * radius;

                for (int dx = -3; dx <= 3; dx++) {
                    for (int dy = -3; dy <= 3; dy++) {
                        int gx = cx + dx;
                        int gy = cy + dy;
                        byte biome = selector.select(Mth.clamp(gx, 0, renderDataSize - 1), Mth.clamp(gy, 0, renderDataSize - 1));

                        double distX = wu - (gx + 0.5);
                        double distY = wv - (gy + 0.5);
                        double dSq = distX * distX + distY * distY;

                        if (dSq < radiusSq) {
                            // Smooth drop-off weight function: (1 - d^2 / R^2)^3
                            double ratio = dSq / radiusSq;
                            double term = 1.0 - ratio;
                            double weight = term * term * term;

                            // Boost landmasses slightly for tighter shores
                            if (!PlanetColors.isWater(biome)) {
                                influence[biome] += (float) (weight * 1.1);
                            } else {
                                influence[biome] += (float) weight;
                            }
                        }
                    }
                }

                int bestBiome = PlanetColors.FALLBACK;
                float maxInfluence = -1;
                for (int b = 0; b < PlanetColors.getReservedCount(); b++) {
                    if (influence[b] > maxInfluence) {
                        maxInfluence = influence[b];
                        bestBiome = b;
                    }
                }
                virtualBiomes[vx + vy * virtualSize] = (byte) bestBiome;
            }
        }

        // Pass 2: Color, Shade, and Draw the 3D-embossed pixel art planet
        for (int vy = 0; vy < virtualSize; vy++) {
            for (int vx = 0; vx < virtualSize; vx++) {
                byte colorIdx = virtualBiomes[vx + vy * virtualSize];

                int[] unpacked = PlanetColors.getUnpackedColorARGB(colorIdx);
                int r = unpacked[1];
                int g = unpacked[2];
                int b = unpacked[3];

                // Dynamic 3D pixel-art coastline shading
                if (PlanetColors.isWater(colorIdx)) {
                    // Shadow cast ONTO water from top-left land
                    int tlx = vx - 1;
                    int tly = vy - 1;
                    if (tlx >= 0 && tly >= 0) {
                        byte neighborBiome = virtualBiomes[tlx + tly * virtualSize];
                        if (!PlanetColors.isWater(neighborBiome)) {
                            r = (int) (r * 0.75);
                            g = (int) (g * 0.75);
                            b = (int) (b * 0.75);
                        }
                    }
                } else {
                    // Border depth cast ONTO neighboring bottom-right water
                    int brx = vx + 1;
                    int bry = vy + 1;
                    if (brx < virtualSize && bry < virtualSize) {
                        byte neighborBiome = virtualBiomes[brx + bry * virtualSize];
                        if (PlanetColors.isWater(neighborBiome)) {
                            r = (int) (r * 0.85);
                            g = (int) (g * 0.85);
                            b = (int) (b * 0.85);
                        }
                    }
                }

                int color = (unpacked[0] << 24) | (b << 16) | (g << 8) | r;
                for (int bx = 0; bx < blockSize; bx++) {
                    for (int by = 0; by < blockSize; by++) {
                        image.setPixelRGBA(vx * blockSize + bx, vy * blockSize + by, color);
                    }
                }
            }
        }
        return image;
    }

    public static ResourceLocation CLOUD_TEXTURE_ID = null;
    private static DynamicTexture CLOUD_TEXTURE_OBJ = null;

    private static ResourceLocation SONIC_BOOM_TEXTURE_ID = null;
    private static DynamicTexture SONIC_BOOM_TEXTURE_OBJ = null;

    public static void ensureCloudTexture() {
        if (CLOUD_TEXTURE_ID != null) return;
        Minecraft mc = Minecraft.getInstance();
        
        int size = 512; // High-resolution canvas for micro-eddies and swirls
        NativeImage image = new NativeImage(size, size, false);
        
        java.util.Random rand = new java.util.Random(1337L);
        Noise2D noiseGen = new Noise2D(64, rand);

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                float u = (x / (float)size) * 8.0f;
                float v = (y / (float)size) * 8.0f;
                
                // 1. Coriolis Cyclonic Spiral Vortices (Twists coordinates in opposite directions)
                // Cyclone 1 (North, Counter-Clockwise)
                {
                    double cu = 2.5;
                    double cv = 2.5;
                    double du = u - cu;
                    double dv = v - cv;
                    if (du > 4.0) du -= 8.0;
                    if (du < -4.0) du += 8.0;
                    if (dv > 4.0) dv -= 8.0;
                    if (dv < -4.0) dv += 8.0;
                    double dist = Math.sqrt(du * du + dv * dv);
                    if (dist < 2.2) {
                        double strength = 1.9 * (1.0 - dist / 2.2);
                        double angle = strength / (dist + 0.15);
                        double cosA = Math.cos(angle);
                        double sinA = Math.sin(angle);
                        u = (float) (cu + (du * cosA - dv * sinA));
                        v = (float) (cv + (du * sinA + dv * cosA));
                    }
                }
                
                // Cyclone 2 (South, Clockwise)
                {
                    double cu = 5.5;
                    double cv = 5.5;
                    double du = u - cu;
                    double dv = v - cv;
                    if (du > 4.0) du -= 8.0;
                    if (du < -4.0) du += 8.0;
                    if (dv > 4.0) dv -= 8.0;
                    if (dv < -4.0) dv += 8.0;
                    double dist = Math.sqrt(du * du + dv * dv);
                    if (dist < 2.2) {
                        double strength = -1.9 * (1.0 - dist / 2.2);
                        double angle = strength / (dist + 0.15);
                        double cosA = Math.cos(angle);
                        double sinA = Math.sin(angle);
                        u = (float) (cu + (du * cosA - dv * sinA));
                        v = (float) (cv + (du * sinA + dv * cosA));
                    }
                }
                
                // 2. Double-level Domain Warping (Creates fluid-like turbulence, wisps, and eddies)
                float warpX = noiseGen.sample(u * 2.0f + 1.2f, v * 2.0f + 3.4f) * 1.5f;
                float warpY = noiseGen.sample(u * 2.0f + 5.6f, v * 2.0f + 7.8f) * 1.5f;
                
                float tu = u + warpX;
                float tv = v + warpY;
                
                float swirlX = noiseGen.sample(tu * 4.0f + 2.1f, tv * 4.0f + 4.3f) * 0.8f;
                float swirlY = noiseGen.sample(tu * 4.0f + 6.5f, tv * 4.0f + 8.7f) * 0.8f;
                
                float finalU = tu + swirlX;
                float finalV = tv + swirlY;
                
                // 3. Multi-octave fBm value noise (4 octaves for rich atmospheric texture)
                float noise = 0;
                float amplitude = 0.5f;
                float frequency = 1.0f;
                for (int o = 0; o < 4; o++) {
                    noise += noiseGen.sample(finalU * frequency, finalV * frequency) * amplitude;
                    amplitude *= 0.5f;
                    frequency *= 2.0f;
                }
                
                // 4. High-frequency Wind Erosion (eats away edges to create wispy, frayed details)
                float erode = noiseGen.sample(finalU * 12.0f, finalV * 12.0f) * 0.22f
                            + noiseGen.sample(finalU * 24.0f, finalV * 24.0f) * 0.08f;
                
                // Subtract erosion primarily at the edges of the cloud formations
                float erodedNoise = noise - (1.0f - noise) * erode;
                
                float threshold = 0.20f;
                float density = 0.0f;
                if (erodedNoise > threshold) {
                    density = (erodedNoise - threshold) / (1.0f - threshold);
                }
                
                int alpha = 0;
                if (density > 0) {
                    // Smoothstep the density to make centers solid white and edges dissolve sharply
                    float smoothDensity = density * density * (3.0f - 2.0f * density);
                    alpha = (int) (smoothDensity * 245.0f); // Solid white centers (max opacity 245)
                }

                int color = (alpha << 24) | (255 << 16) | (255 << 8) | 255;
                image.setPixelRGBA(x, y, color);
            }
        }
        
        CLOUD_TEXTURE_OBJ = new DynamicTexture(image);
        CLOUD_TEXTURE_ID = mc.getTextureManager().register("rocketnautics_clouds", CLOUD_TEXTURE_OBJ);
        CLOUD_TEXTURE_OBJ.setFilter(false, false); // Keep it crisp and pixelated to perfectly match the planet style!
        image.close();
    }

    private static void ensureSonicBoomTexture() {
        if (SONIC_BOOM_TEXTURE_ID != null) return;
        Minecraft mc = Minecraft.getInstance();
        
        int size = 128; // Perfect resolution for high fidelity crisp pixel-art clouds!
        NativeImage image = new NativeImage(size, size, false);
        
        java.util.Random rand = new java.util.Random(9999L);
        Noise2D noiseGen = new Noise2D(32, rand);
        
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                // Seamless horizontal mapping
                float u = (x / (float)size) * 4.0f;
                // Vertical mapping along the cone length
                float v = (y / (float)size) * 4.0f;
                
                float normV = y / (float)size; // 0.0 to 1.0
                
                // Fluid/wind domain warping
                float warpX = noiseGen.sample(u * 2.0f + 0.5f, v * 2.0f + 1.5f) * 0.8f;
                float warpY = noiseGen.sample(u * 2.0f + 2.5f, v * 2.0f + 3.5f) * 0.8f;
                
                float tu = u + warpX;
                float tv = v + warpY;
                
                // fBm value noise for cloud puffs
                float noise = 0;
                float amplitude = 0.6f;
                float frequency = 1.0f;
                for (int o = 0; o < 3; o++) {
                    noise += noiseGen.sample(tu * frequency, tv * frequency) * amplitude;
                    amplitude *= 0.5f;
                    frequency *= 2.0f;
                }
                
                // Supersonic pressure shockwave envelope
                float envelope = 0.0f;
                if (normV < 0.1f) {
                    envelope = normV / 0.1f;
                } else if (normV >= 0.1f && normV < 0.35f) {
                    envelope = 1.0f;
                } else {
                    envelope = 1.0f - (normV - 0.35f) / (1.0f - 0.35f);
                }
                
                float intensity = noise * envelope;
                
                // Wind shear streaks
                float streaks = noiseGen.sample(tu * 8.0f, tv * 0.5f) * 0.15f;
                intensity += streaks * (1.0f - normV);
                
                // Pixelated Cloud palette quantization
                int r = 255;
                int g = 255;
                int b = 255;
                int alpha = 0;
                
                if (intensity > 0.65f) {
                    // 1. Extreme compression core / peak highlight
                    r = 255;
                    g = 255;
                    b = 255;
                    alpha = 245;
                } else if (intensity > 0.48f) {
                    // 2. High-density vapor / cloud body
                    r = 240;
                    g = 248;
                    b = 255;
                    alpha = 210;
                } else if (intensity > 0.32f) {
                    // 3. Ionized shock glow / cyan mist
                    r = 160;
                    g = 215;
                    b = 255;
                    alpha = 145;
                } else if (intensity > 0.20f) {
                    // 4. Stylized pixel-art cloud dither border
                    boolean dither = ((x + y) % 2 == 0);
                    if (dither) {
                        r = 130;
                        g = 190;
                        b = 245;
                        alpha = 85;
                    } else {
                        alpha = 0;
                    }
                } else {
                    alpha = 0;
                }
                
                // Ensure the core shockwave ring remains prominent
                if (normV >= 0.12f && normV <= 0.28f && alpha > 0) {
                    alpha = Math.min(255, (int)(alpha * 1.15f));
                }
                
                int color = (alpha << 24) | (b << 16) | (g << 8) | r;
                image.setPixelRGBA(x, y, color);
            }
        }
        
        SONIC_BOOM_TEXTURE_OBJ = new DynamicTexture(image);
        SONIC_BOOM_TEXTURE_ID = mc.getTextureManager().register("rocketnautics_sonic_boom", SONIC_BOOM_TEXTURE_OBJ);
        SONIC_BOOM_TEXTURE_OBJ.setFilter(false, false); // Keep it completely pixelated & crisp!
        image.close();
    }

    private static byte getBiomeAt(int gx, int gy, byte[] posPos, byte[] posNeg, byte[] negPos, byte[] negNeg) {
        int dataSize = 256;
        if (gx >= dataSize) {
            int sx = gx - dataSize;
            if (gy >= dataSize) {
                return posPos[sx + (gy - dataSize) * dataSize];
            } else {
                return posNeg[sx + gy * dataSize];
            }
        } else {
            if (gy >= dataSize) {
                return negPos[gx + (gy - dataSize) * dataSize];
            } else {
                return negNeg[gx + gy * dataSize];
            }
        }
    }

    private static class Noise2D {
        private final float[] grid;
        private final int size;

        public Noise2D(int size, java.util.Random rand) {
            this.size = size;
            this.grid = new float[size * size];
            for (int i = 0; i < grid.length; i++) {
                grid[i] = rand.nextFloat();
            }
        }

        public float get(int x, int y) {
            x = (x % size + size) % size;
            y = (y % size + size) % size;
            return grid[x + y * size];
        }

        public float sample(float x, float y) {
            int x0 = (int) Math.floor(x);
            int y0 = (int) Math.floor(y);
            float tx = x - x0;
            float ty = y - y0;

            float v00 = get(x0, y0);
            float v10 = get(x0 + 1, y0);
            float v01 = get(x0, y0 + 1);
            float v11 = get(x0 + 1, y0 + 1);

            float sx = tx * tx * (3 - 2 * tx);
            float sy = ty * ty * (3 - 2 * ty);

            float nx0 = v00 + sx * (v10 - v00);
            float nx1 = v01 + sx * (v11 - v01);

            return nx0 + sy * (nx1 - nx0);
        }
    }

    public static ResourceLocation HALO_TEXTURE_ID = null;
    private static boolean haloV5 = false;

    public static void ensureHaloTexture() {
        if (HALO_TEXTURE_ID != null && haloV5) return;
        Minecraft mc = Minecraft.getInstance();
        int size = 256;
        NativeImage image = new NativeImage(size, size, false);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                double dx = (x - size / 2.0) / (size / 2.0);
                double dy = (y - size / 2.0) / (size / 2.0);
                
                double dist = Math.max(Math.abs(dx), Math.abs(dy));
                
                int r = 0, g = 0, b = 0, a = 0;
                double planetRadius = 1.0 / 1.3;
                
                if (dist <= planetRadius) {
                    double normalizedDist = dist / planetRadius; 
                    double opticalDepth = Math.pow(normalizedDist, 3.0);
                    
                    r = 40; g = 120; b = 255;
                    r += (int) (opticalDepth * 215); 
                    g += (int) (opticalDepth * 135); 
                    b += (int) (opticalDepth * 0);   
                    
                    r = Math.min(255, r); g = Math.min(255, g); b = Math.min(255, b);
                    a = 80 + (int) (opticalDepth * 175);
                } else if (dist <= 1.0) {
                    double gradient = (dist - planetRadius) / (1.0 - planetRadius);
                    double fade = Math.pow(1.0 - gradient, 1.0);
                    
                    r = (int) (140 * fade);
                    g = (int) (220 * fade);
                    b = (int) (255 * fade);
                    
                    a = (int) (255 * fade);
                }
                
                int color = (a << 24) | (b << 16) | (g << 8) | r;
                image.setPixelRGBA(x, y, color);
            }
        }
        DynamicTexture dynamicTexture = new DynamicTexture(image);
        HALO_TEXTURE_ID = mc.getTextureManager().register("rocketnautics_halo_v5", dynamicTexture);
        haloV5 = true;
        image.close();
    }

    public static ResourceLocation LIGHT_OVERLAY_TEXTURE_ID = null;
    private static DynamicTexture LIGHT_OVERLAY_TEXTURE_OBJ = null;
    private static float lastUpdatedAngle = -1;

    public static void ensureLightOverlayTexture(float celestialAngle) {
        if (LIGHT_OVERLAY_TEXTURE_ID != null && Math.abs(celestialAngle - lastUpdatedAngle) < 0.001f) {
            return;
        }
        
        lastUpdatedAngle = celestialAngle;
        Minecraft mc = Minecraft.getInstance();
        
        int size = 128;
        NativeImage image = new NativeImage(size, size, false);
        
        double theta = 2.0 * Math.PI * celestialAngle;
        double lx = -Math.sin(theta);
        double ly = Math.cos(theta);
        
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double nx = (x - size / 2.0) / (size / 2.0);
                double nz = (y - size / 2.0) / (size / 2.0);
                double rSq = nx * nx + nz * nz;
                
                if (rSq > 1.0) {
                    image.setPixelRGBA(x, y, 0);
                    continue;
                }
                
                double ny = Math.sqrt(1.0 - rSq);
                double dSun = nx * lx + ny * ly;
                
                int r = 0, g = 0, b = 0, a = 0;
                
                // --- 1. Pixelated Toon/Cell Shading Shadow + Specular Highlight ---
                if (dSun > 0.65) {
                    // Brilliant star-facing sunlight overlay (warm tint)
                    r = 255;
                    g = 245;
                    b = 200;
                    a = 55;
                } else if (dSun > 0.45) {
                    // Dithered bright border to clear light
                    if ((x + y) % 2 == 0) {
                        r = 255;
                        g = 245;
                        b = 200;
                        a = 35;
                    }
                } else if (dSun > 0.05) {
                    // Direct illumination - clear, shows raw planet texture
                    r = 0; g = 0; b = 0; a = 0;
                } else if (dSun > -0.12) {
                    // Soft dithered terminator line
                    if ((x + y) % 2 == 0) {
                        r = 6;
                        g = 8;
                        b = 25;
                        a = 130;
                    }
                } else {
                    // Deep dark indigo space shadow
                    r = 4;
                    g = 5;
                    b = 18;
                    a = 230;
                }
                
                // --- 2. Pixelated Atmospheric Edge Glow ---
                double rDist = Math.sqrt(rSq);
                if (rDist >= 0.83) {
                    double edgeFactor = (rDist - 0.83) / 0.17; // 0.0 to 1.0
                    edgeFactor = edgeFactor * edgeFactor;
                    
                    if (dSun > -0.2) {
                        // Sunlight atmospheric glow: brilliant cyan/blue
                        double intensity = edgeFactor * Math.max(0.0, (dSun + 0.2) / 1.2);
                        int gr = 80;
                        int gg = 195;
                        int gb = 255;
                        int ga = (int) (225.0 * intensity);
                        
                        // Pixelate the atmosphere glow!
                        if (ga > 20) {
                            r = (int) (r * (1.0 - intensity) + gr * intensity);
                            g = (int) (g * (1.0 - intensity) + gg * intensity);
                            b = (int) (b * (1.0 - intensity) + gb * intensity);
                            a = Math.max(a, ga);
                        }
                    } else {
                        // Shadow-side atmospheric glow: cool space violet (dithered/pixelated)
                        double intensity = edgeFactor * Math.max(0.0, (-dSun - 0.2) / 1.2);
                        int gr = 120;
                        int gg = 75;
                        int gb = 230;
                        int ga = (int) (180.0 * intensity);
                        
                        if (ga > 20) {
                            r = (int) (r * (1.0 - intensity) + gr * intensity);
                            g = (int) (g * (1.0 - intensity) + gg * intensity);
                            b = (int) (b * (1.0 - intensity) + gb * intensity);
                            a = Math.max(a, ga);
                        }
                    }
                }
                
                int color = (a << 24) | (b << 16) | (g << 8) | r;
                image.setPixelRGBA(x, y, color);
            }
        }
        
        if (LIGHT_OVERLAY_TEXTURE_OBJ == null) {
            LIGHT_OVERLAY_TEXTURE_OBJ = new DynamicTexture(image);
            LIGHT_OVERLAY_TEXTURE_ID = mc.getTextureManager().register("rocketnautics_light_overlay", LIGHT_OVERLAY_TEXTURE_OBJ);
        } else {
            LIGHT_OVERLAY_TEXTURE_OBJ.setPixels(image);
            LIGHT_OVERLAY_TEXTURE_OBJ.upload();
        }
        LIGHT_OVERLAY_TEXTURE_OBJ.setFilter(false, false); // Keep it crisp and pixelated!
        image.close();
    }

    private static class SpaceStar {
        float x, y, z;
        float r, g, b, size;
        float twinkleSpeed;
        float twinkleOffset;
        int type;
    }

    private static class Constellation {
        String name;
        int[] starIndices;
        int[][] connections;
    }

    private static SpaceStar[] SPACE_STARS = null;
    private static java.util.List<Constellation> CONSTELLATIONS = null;

    private static void ensureSpaceStars() {
        if (SPACE_STARS != null) return;
        SPACE_STARS = new SpaceStar[1600];
        CONSTELLATIONS = new java.util.ArrayList<>();
        
        java.util.Random rand = new java.util.Random(13374242L);
        
        // Define cluster centers
        float[][] clusterCenters = {
            {0.5f, 0.4f, 0.76f},      // Cluster 1 (Pleiades-like Blue)
            {-0.65f, -0.3f, 0.69f},   // Cluster 2 (Hyades Golden)
            {0.1f, -0.8f, -0.59f},    // Cluster 3 (Jewel Box Mixed)
            {-0.4f, 0.75f, -0.53f},   // Cluster 4 (Butterfly White)
            {0.68f, -0.48f, 0.55f}    // Cluster 5 (Pulsar Purple)
        };
        
        int starIdx = 0;
        
        // 1. Generate Constellations (Ursa Major, Orion, Cassiopeia, Southern Cross)
        // Ursa Major (Big Dipper) - 7 stars (North Sky, -Z)
        {
            Constellation c = new Constellation();
            c.name = "Ursa Major";
            c.starIndices = new int[7];
            float[][] coords = {
                { 0.25f, 0.45f, -0.86f}, // Alkaid
                { 0.18f, 0.50f, -0.84f}, // Mizar
                { 0.12f, 0.53f, -0.83f}, // Alioth
                { 0.04f, 0.50f, -0.86f}, // Megrez
                { 0.05f, 0.40f, -0.91f}, // Phecda
                {-0.08f, 0.35f, -0.93f}, // Merak
                {-0.07f, 0.45f, -0.89f}  // Dubhe
            };
            for (int j = 0; j < 7; j++) {
                SpaceStar star = new SpaceStar();
                float len = (float) Math.sqrt(coords[j][0]*coords[j][0] + coords[j][1]*coords[j][1] + coords[j][2]*coords[j][2]);
                star.x = coords[j][0]/len; star.y = coords[j][1]/len; star.z = coords[j][2]/len;
                star.r = 0.85f; star.g = 0.93f; star.b = 1.0f; // Sparkling navigation white-blue
                star.size = 2.1f; // Large and highly noticeable
                star.type = 2; // Cross star
                star.twinkleSpeed = 0.6f;
                star.twinkleOffset = j * 2.0f;
                SPACE_STARS[starIdx] = star;
                c.starIndices[j] = starIdx++;
            }
            c.connections = new int[][]{
                {0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 5}, {5, 6}, {6, 3}
            };
            CONSTELLATIONS.add(c);
        }
        
        // Orion - 8 stars (Equatorial East, +X)
        {
            Constellation c = new Constellation();
            c.name = "Orion";
            c.starIndices = new int[8];
            float[][] coords = {
                { 0.75f, 0.45f, -0.48f}, // Betelgeuse (Red supergiant!)
                { 0.82f, 0.48f, -0.31f}, // Bellatrix
                { 0.78f, 0.35f, -0.51f}, // Alnilam (Belt 1)
                { 0.76f, 0.33f, -0.55f}, // Alnitak (Belt 2)
                { 0.80f, 0.37f, -0.47f}, // Mintaka (Belt 3)
                { 0.79f, 0.22f, -0.58f}, // Rigel (Blue supergiant!)
                { 0.85f, 0.25f, -0.46f}, // Saiph
                { 0.84f, 0.55f, -0.40f}  // Meissa (Head)
            };
            for (int j = 0; j < 8; j++) {
                SpaceStar star = new SpaceStar();
                float len = (float) Math.sqrt(coords[j][0]*coords[j][0] + coords[j][1]*coords[j][1] + coords[j][2]*coords[j][2]);
                star.x = coords[j][0]/len; star.y = coords[j][1]/len; star.z = coords[j][2]/len;
                if (j == 0) {
                    star.r = 1.0f; star.g = 0.38f; star.b = 0.15f; // Betelgeuse fiery red-orange
                    star.size = 2.5f; // Massive and brilliant!
                } else if (j == 5) {
                    star.r = 0.5f; star.g = 0.80f; star.b = 1.0f; // Rigel electric blue
                    star.size = 2.4f; // Extremely brilliant!
                } else {
                    star.r = 0.8f; star.g = 0.90f; star.b = 1.0f;
                    star.size = 1.9f;
                }
                star.type = 2;
                star.twinkleSpeed = 0.5f;
                star.twinkleOffset = j * 3.0f;
                SPACE_STARS[starIdx] = star;
                c.starIndices[j] = starIdx++;
            }
            c.connections = new int[][]{
                {0, 2}, {1, 4},
                {2, 3}, {3, 4},
                {2, 6}, {4, 5},
                {0, 7}, {1, 7}
            };
            CONSTELLATIONS.add(c);
        }
        
        // Cassiopeia - 5 stars (North Sky, -Z, opposite Big Dipper)
        {
            Constellation c = new Constellation();
            c.name = "Cassiopeia";
            c.starIndices = new int[5];
            float[][] coords = {
                {-0.30f, 0.60f, -0.74f},
                {-0.22f, 0.65f, -0.73f},
                {-0.15f, 0.62f, -0.77f},
                {-0.08f, 0.68f, -0.72f},
                { 0.00f, 0.70f, -0.71f}
            };
            for (int j = 0; j < 5; j++) {
                SpaceStar star = new SpaceStar();
                float len = (float) Math.sqrt(coords[j][0]*coords[j][0] + coords[j][1]*coords[j][1] + coords[j][2]*coords[j][2]);
                star.x = coords[j][0]/len; star.y = coords[j][1]/len; star.z = coords[j][2]/len;
                star.r = 0.95f; star.g = 0.9f; star.b = 1.0f;
                star.size = 2.0f;
                star.type = 2;
                star.twinkleSpeed = 0.7f;
                star.twinkleOffset = j * 1.5f;
                SPACE_STARS[starIdx] = star;
                c.starIndices[j] = starIdx++;
            }
            c.connections = new int[][]{
                {0, 1}, {1, 2}, {2, 3}, {3, 4}
            };
            CONSTELLATIONS.add(c);
        }
        
        // Southern Cross (Crux) - 5 stars (South Sky, +Z)
        {
            Constellation c = new Constellation();
            c.name = "Crux";
            c.starIndices = new int[5];
            float[][] coords = {
                { 0.00f, 0.30f,  0.95f}, // Acrux (Bottom)
                { 0.08f, 0.38f,  0.92f}, // Mimosa (Left)
                { 0.01f, 0.46f,  0.89f}, // Gacrux (Top)
                {-0.07f, 0.38f,  0.92f}, // Imai (Right)
                { 0.02f, 0.37f,  0.92f}  // Ginan (Center)
            };
            for (int j = 0; j < 5; j++) {
                SpaceStar star = new SpaceStar();
                float len = (float) Math.sqrt(coords[j][0]*coords[j][0] + coords[j][1]*coords[j][1] + coords[j][2]*coords[j][2]);
                star.x = coords[j][0]/len; star.y = coords[j][1]/len; star.z = coords[j][2]/len;
                star.r = 0.85f; star.g = 0.9f; star.b = 1.0f;
                star.size = 2.1f;
                star.type = 2;
                star.twinkleSpeed = 0.8f;
                star.twinkleOffset = j * 2.5f;
                SPACE_STARS[starIdx] = star;
                c.starIndices[j] = starIdx++;
            }
            c.connections = new int[][]{
                {0, 2}, {1, 3}
            };
            CONSTELLATIONS.add(c);
        }

        // 2. Generate Clustered and Background Stars
        for (int i = starIdx; i < SPACE_STARS.length; i++) {
            SpaceStar star = new SpaceStar();
            
            boolean inCluster = (i % 3 == 0); // 33% of remaining stars belong to clusters!
            if (inCluster) {
                int centerIdx = rand.nextInt(clusterCenters.length);
                float[] cc = clusterCenters[centerIdx];
                
                float spread = 0.04f + rand.nextFloat() * 0.05f;
                star.x = cc[0] + (float) rand.nextGaussian() * spread;
                star.y = cc[1] + (float) rand.nextGaussian() * spread;
                star.z = cc[2] + (float) rand.nextGaussian() * spread;
                
                float len = (float) Math.sqrt(star.x * star.x + star.y * star.y + star.z * star.z);
                star.x /= len; star.y /= len; star.z /= len;
                
                star.r = 1.0f; star.g = 1.0f; star.b = 1.0f;
                if (centerIdx == 0) {
                    // Cluster 1 (Pleiades): Icy bright cyan/blue
                    star.r = 0.5f; star.g = 0.85f; star.b = 1.0f;
                } else if (centerIdx == 1) {
                    // Cluster 2 (Hyades): Warm golden/orange
                    star.r = 1.0f; star.g = 0.75f; star.b = 0.4f;
                } else if (centerIdx == 4) {
                    // Cluster 5 (Pulsar): Mystic neon purple/pink
                    star.r = 0.9f; star.g = 0.5f; star.b = 1.0f;
                } else {
                    star.r = 0.95f; star.g = 0.95f; star.b = 1.0f;
                }
                
                float sizeRoll = rand.nextFloat();
                star.size = 0.35f + sizeRoll * 0.7f;
                star.type = (sizeRoll > 0.99f) ? 2 : ((sizeRoll > 0.8f) ? 1 : 0);
            } else {
                double theta = rand.nextDouble() * 2.0 * Math.PI;
                double phi = Math.acos(2.0 * rand.nextDouble() - 1.0);
                
                star.x = (float) (Math.sin(phi) * Math.cos(theta));
                star.y = (float) (Math.sin(phi) * Math.sin(theta));
                star.z = (float) (Math.cos(phi));
                
                star.r = 1.0f; star.g = 1.0f; star.b = 1.0f;
                double colorRoll = rand.nextDouble();
                if (colorRoll < 0.15) {
                    star.r = 0.65f + rand.nextFloat() * 0.1f;
                    star.g = 0.80f + rand.nextFloat() * 0.1f;
                    star.b = 1.00f;
                } else if (colorRoll < 0.30) {
                    star.r = 0.95f; star.g = 0.95f; star.b = 1.00f;
                } else if (colorRoll < 0.42) {
                    star.r = 1.00f;
                    star.g = 0.90f + rand.nextFloat() * 0.08f;
                    star.b = 0.65f + rand.nextFloat() * 0.08f;
                } else if (colorRoll < 0.52) {
                    star.r = 1.00f;
                    star.g = 0.60f + rand.nextFloat() * 0.12f;
                    star.b = 0.40f + rand.nextFloat() * 0.12f;
                }
                
                float sizeRoll = rand.nextFloat();
                star.size = 0.25f + sizeRoll * 0.75f;
                star.type = (sizeRoll > 0.992f) ? 2 : ((sizeRoll > 0.85f) ? 1 : 0);
            }
            
            star.twinkleSpeed = 0.3f + rand.nextFloat() * 1.5f;
            star.twinkleOffset = rand.nextFloat() * 100.0f;
            
            SPACE_STARS[i] = star;
        }
    }

    private static void drawPixelSquare(BufferBuilder buffer, Matrix4f matrix, float x, float y, float z, float size, 
                                        float ux, float uy, float uz, float vx, float vy, float vz, 
                                        float r, float g, float b, float alpha) {
        buffer.addVertex(matrix, x - size * ux - size * vx, y - size * uy - size * vy, z - size * uz - size * vz).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, x - size * ux + size * vx, y - size * uy + size * vy, z - size * uz + size * vz).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, x + size * ux + size * vx, y + size * uy + size * vy, z + size * uz + size * vz).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, x + size * ux - size * vx, y + size * uy - size * vy, z + size * uz - size * vz).setColor(r, g, b, alpha);
    }

    private static ResourceLocation STAR_CUBE_TEXTURE_ID = null;
    private static void ensureStarCubeTexture() {
        if (STAR_CUBE_TEXTURE_ID != null) return;
        Minecraft mc = Minecraft.getInstance();
        int texSize = 32;
        NativeImage image = new NativeImage(texSize, texSize, false);
        java.util.Random rand = new java.util.Random(1337L);
        for (int x = 0; x < texSize; x++) {
            for (int y = 0; y < texSize; y++) {
                double nx = x / (double)texSize;
                double ny = y / (double)texSize;
                
                // Turbulent high contrast sine waves
                double val = Math.sin(nx * 12.0 + Math.cos(ny * 8.0) * 4.0) 
                           + Math.cos(ny * 12.0 + Math.sin(nx * 8.0) * 4.0);
                val = (val + 2.0) / 4.0;
                
                if (rand.nextDouble() < 0.12) {
                    val = val * 0.7 + 0.3;
                }
                
                int brightness = (int) (155 + val * 100);
                brightness = Math.max(0, Math.min(255, brightness));
                
                // Pure opaque pixel
                int color = (255 << 24) | (brightness << 16) | (brightness << 8) | brightness;
                image.setPixelRGBA(x, y, color);
            }
        }
        DynamicTexture dynamicTexture = new DynamicTexture(image);
        STAR_CUBE_TEXTURE_ID = mc.getTextureManager().register("rocketnautics_star_cube_tex", dynamicTexture);
        dynamicTexture.setFilter(false, false); // Crisp pixelated look!
        image.close();
    }

    // --- Star Plasma Texture (animated, for orbital view of the Sun) ---
    public static ResourceLocation STAR_PLASMA_TEXTURE_ID = null;
    private static DynamicTexture STAR_PLASMA_TEXTURE_OBJ = null;
    private static long lastPlasmaUpdateTick = -999;

    // --- Fast, high-quality, lightweight 3D Perlin Noise with FBM and Domain Warping ---
    private static class StarNoise {
        private static final int[] p = new int[512];
        private static final int[] permutation = {
            151,160,137,91,90,15,131,13,201,95,96,53,194,233,7,225,140,36,103,30,69,142,8,99,37,240,21,10,23,
            190,6,148,247,120,234,75,0,26,197,62,94,252,219,203,117,35,11,32,57,177,33,88,237,149,56,87,174,20,
            125,136,171,168,68,175,74,165,71,134,139,48,27,166,77,146,158,231,83,111,229,122,60,211,133,230,220,
            105,92,41,55,46,245,40,244,102,143,54,65,25,63,161,1,216,80,73,209,76,132,187,208,89,18,169,200,196,
            135,130,116,188,159,86,164,100,109,198,173,186,3,64,52,217,226,250,124,123,5,202,38,147,118,126,255,
            82,85,212,207,206,59,227,47,16,58,17,182,189,28,42,223,183,170,213,119,248,152,2,44,154,163,70,221,
            153,101,155,167,43,172,9,129,22,39,253,19,98,108,110,79,113,224,232,178,185,112,104,218,246,97,228,
            251,34,242,193,238,210,144,12,191,179,162,241,81,51,145,235,249,14,239,107,49,192,214,31,181,199,106,
            157,184,84,204,176,115,121,50,45,127,4,150,254,138,236,205,93,222,114,67,29,24,72,243,141,128,195,78,
            66,215,61,156,180
        };
        static {
            for (int i = 0; i < 256; i++) {
                p[256 + i] = p[i] = permutation[i];
            }
        }
        private static double fade(double t) {
            return t * t * t * (t * (t * 6 - 15) + 10);
        }
        private static double lerp(double t, double a, double b) {
            return a + t * (b - a);
        }
        private static double grad(int hash, double x, double y, double z) {
            int h = hash & 15;
            double u = h < 8 ? x : y;
            double v = h < 4 ? y : h == 12 || h == 14 ? x : z;
            return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
        }
        public static double noise(double x, double y, double z) {
            int X = (int) Math.floor(x) & 255;
            int Y = (int) Math.floor(y) & 255;
            int Z = (int) Math.floor(z) & 255;
            x -= Math.floor(x);
            y -= Math.floor(y);
            z -= Math.floor(z);
            double u = fade(x);
            double v = fade(y);
            double w = fade(z);
            int A = p[X] + Y;
            int AA = p[A] + Z;
            int AB = p[A + 1] + Z;
            int B = p[X + 1] + Y;
            int BA = p[B] + Z;
            int BB = p[B + 1] + Z;
            return lerp(w, lerp(v, lerp(u, grad(p[AA], x, y, z),
                                           grad(p[BA], x - 1, y, z)),
                                   lerp(u, grad(p[AB], x, y - 1, z),
                                           grad(p[BB], x - 1, y - 1, z))),
                           lerp(v, lerp(u, grad(p[AA + 1], x, y, z - 1),
                                           grad(p[BA + 1], x - 1, y, z - 1)),
                                   lerp(u, grad(p[AB + 1], x, y - 1, z - 1),
                                           grad(p[BB + 1], x - 1, y - 1, z - 1))));
        }
        
        public static double fbm(double x, double y, double z, int octaves) {
            double total = 0.0;
            double amplitude = 1.0;
            double frequency = 1.0;
            double maxValue = 0.0;
            for (int i = 0; i < octaves; i++) {
                total += amplitude * noise(x * frequency, y * frequency, z * frequency);
                maxValue += amplitude;
                amplitude *= 0.5;
                frequency *= 2.0;
            }
            return total / maxValue;
        }

        // Domain warping for turbulent fluid/plasma storm patterns!
        public static double warpedNoise(double x, double y, double z) {
            double qx = fbm(x, y, z, 3);
            double qy = fbm(x + 5.2, y + 1.3, z + 2.8, 3);
            double qz = fbm(x + 1.8, y + 6.7, z + 1.1, 3);

            double rx = fbm(x + 4.0 * qx + 1.7, y + 4.0 * qy + 9.2, z + 4.0 * qz + 0.15, 3);
            double ry = fbm(x + 4.0 * qx + 8.3, y + 4.0 * qy + 2.8, z + 4.0 * qz + 1.26, 3);
            double rz = fbm(x + 4.0 * qx + 2.1, y + 4.0 * qy + 5.9, z + 4.0 * qz + 4.3, 3);

            return fbm(x + 4.0 * rx, y + 4.0 * ry, z + 4.0 * rz, 4);
        }
    }

    /** Call once per frame when the star is visible. Regenerates the boiling plasma every tick. */
    public static void ensureStarPlasmaTexture() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long tick = mc.level.getGameTime();
        if (STAR_PLASMA_TEXTURE_ID != null && tick == lastPlasmaUpdateTick) return;
        lastPlasmaUpdateTick = tick;

        int size = 64; // 64x64 pixelated plasma
        NativeImage image = new NativeImage(size, size, false);

        // Map animation time
        double t = (tick % 10000) * 0.05;

        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                float fx = x / (float) size;
                float fy = y / (float) size;

                // 2D to 3D Torus mapping for seamless wrapping in X and Y directions
                double angleX = fx * 2.0 * Math.PI;
                double angleY = fy * 2.0 * Math.PI;
                double r1 = 1.0;
                double r2 = 0.5;
                double nx = (r1 + r2 * Math.cos(angleY)) * Math.cos(angleX);
                double ny = (r1 + r2 * Math.cos(angleY)) * Math.sin(angleX);
                double nz = r2 * Math.sin(angleY);

                // Sample domain warped noise
                double scale = 1.8;
                double plasmaVal = StarNoise.warpedNoise(nx * scale, ny * scale, nz * scale + t);

                // Remap from [-1, 1] to [0, 1]
                float plasma = (float) ((plasmaVal + 1.0) * 0.5);

                // Pixelate: snap to 12 brightness bands for beautiful premium shading
                plasma = (float)(Math.floor(plasma * 12.0f)) / 12.0f;

                // Color map: deep burning red sun spots -> flame orange -> golden yellow -> blinding white-cyan
                int r, g, b;
                if (plasma < 0.15f) {
                    float p = plasma / 0.15f;
                    r = (int)(95 + p * 60);
                    g = (int)(2 + p * 8);
                    b = (int)(p * 2);
                } else if (plasma < 0.4f) {
                    float p = (plasma - 0.15f) / 0.25f;
                    r = 155 + (int)(p * 100);
                    g = 10 + (int)(p * 90);
                    b = 0;
                } else if (plasma < 0.7f) {
                    float p = (plasma - 0.4f) / 0.3f;
                    r = 255;
                    g = 100 + (int)(p * 120);
                    b = (int)(p * 20);
                } else if (plasma < 0.9f) {
                    float p = (plasma - 0.7f) / 0.2f;
                    r = 255;
                    g = 220 + (int)(p * 35);
                    b = 20 + (int)(p * 180);
                } else {
                    float p = (plasma - 0.9f) / 0.1f;
                    r = 255;
                    g = 255;
                    b = 200 + (int)(p * 55);
                }

                r = Math.min(255, Math.max(0, r));
                g = Math.min(255, Math.max(0, g));
                b = Math.min(255, Math.max(0, b));

                // NativeImage uses ABGR layout: alpha=255 (fully opaque)
                int color = (255 << 24) | (b << 16) | (g << 8) | r;
                image.setPixelRGBA(x, y, color);
            }
        }

        if (STAR_PLASMA_TEXTURE_OBJ == null) {
            // DynamicTexture takes ownership of the image — do NOT close it here
            STAR_PLASMA_TEXTURE_OBJ = new DynamicTexture(image);
            STAR_PLASMA_TEXTURE_ID = mc.getTextureManager().register("rocketnautics_star_plasma", STAR_PLASMA_TEXTURE_OBJ);
            STAR_PLASMA_TEXTURE_OBJ.setFilter(false, false); // Pixelated!
        } else {
            // Replace the pixels via setPixels and re-upload
            STAR_PLASMA_TEXTURE_OBJ.setPixels(image);
            STAR_PLASMA_TEXTURE_OBJ.upload();
        }
    }

    private static boolean drawRotatingCubeFace(BufferBuilder buffer, Matrix4f matrix, 
                                                float cx, float cy, float cz, 
                                                float size, float spinAngle,
                                                float ux, float uy, float uz, 
                                                float vx, float vy, float vz,
                                                float wx, float wy, float wz,
                                                int faceIdx, float r, float g, float b, float alpha) {
        float[][] cubeVertices = {
            // +Z face
            {-0.5f, -0.5f,  0.5f}, { 0.5f, -0.5f,  0.5f}, { 0.5f,  0.5f,  0.5f}, {-0.5f,  0.5f,  0.5f},
            // -Z face
            {-0.5f,  0.5f, -0.5f}, { 0.5f,  0.5f, -0.5f}, { 0.5f, -0.5f, -0.5f}, {-0.5f, -0.5f, -0.5f},
            // +X face
            { 0.5f, -0.5f, -0.5f}, { 0.5f,  0.5f, -0.5f}, { 0.5f,  0.5f,  0.5f}, { 0.5f, -0.5f,  0.5f},
            // -X face
            {-0.5f, -0.5f,  0.5f}, {-0.5f,  0.5f,  0.5f}, {-0.5f,  0.5f, -0.5f}, {-0.5f, -0.5f, -0.5f},
            // +Y face
            {-0.5f,  0.5f,  0.5f}, { 0.5f,  0.5f,  0.5f}, { 0.5f,  0.5f, -0.5f}, {-0.5f,  0.5f, -0.5f},
            // -Y face
            {-0.5f, -0.5f, -0.5f}, { 0.5f, -0.5f, -0.5f}, { 0.5f, -0.5f,  0.5f}, {-0.5f, -0.5f,  0.5f}
        };
        
        float cosY = (float) Math.cos(spinAngle);
        float sinY = (float) Math.sin(spinAngle);
        float cosX = (float) Math.cos(spinAngle * 0.5f);
        float sinX = (float) Math.sin(spinAngle * 0.5f);
        
        // Calculate the rotated normal for backface culling in camera-aligned space
        float[][] localNormals = {
            {0, 0, 1},   // +Z
            {0, 0, -1},  // -Z
            {1, 0, 0},   // +X
            {-1, 0, 0},  // -X
            {0, 1, 0},   // +Y
            {0, -1, 0}   // -Y
        };
        
        float lnx = localNormals[faceIdx][0];
        float lny = localNormals[faceIdx][1];
        float lnz = localNormals[faceIdx][2];
        
        float nx1 = lnx * cosY - lnz * sinY;
        float nz1 = lnx * sinY + lnz * cosY;
        float ny1 = lny;
        
        float nx2 = nx1;
        float ny2 = ny1 * cosX - nz1 * sinX;
        float nz2 = ny1 * sinX + nz1 * cosX;
        
        // Only draw faces pointing towards camera (nz2 > 0)
        if (nz2 <= 0.0f) {
            return false;
        }
        
        for (int i = 0; i < 4; i++) {
            int idx = faceIdx * 4 + i;
            float lx = cubeVertices[idx][0] * size;
            float ly = cubeVertices[idx][1] * size;
            float lz = cubeVertices[idx][2] * size;
            
            float x1 = lx * cosY - lz * sinY;
            float z1 = lx * sinY + lz * cosY;
            float y1 = ly;
            
            float x2 = x1;
            float y2 = y1 * cosX - z1 * sinX;
            float z2 = y1 * sinX + z1 * cosX;
            
            float rx = cx + x2 * ux + y2 * vx + z2 * wx;
            float ry = cy + x2 * uy + y2 * vy + z2 * wy;
            float rz = cz + x2 * uz + y2 * vz + z2 * wz;
            
            buffer.addVertex(matrix, rx, ry, rz).setColor(r, g, b, alpha);
        }
        return true;
    }

    private static boolean drawTexturedRotatingCubeFace(BufferBuilder buffer, Matrix4f matrix, 
                                                        float cx, float cy, float cz, 
                                                        float size, float spinAngle,
                                                        float ux, float uy, float uz, 
                                                        float vx, float vy, float vz,
                                                        float wx, float wy, float wz,
                                                        int faceIdx, float r, float g, float b, float alpha) {
        float[][] cubeVertices = {
            // +Z face
            {-0.5f, -0.5f,  0.5f}, { 0.5f, -0.5f,  0.5f}, { 0.5f,  0.5f,  0.5f}, {-0.5f,  0.5f,  0.5f},
            // -Z face
            {-0.5f,  0.5f, -0.5f}, { 0.5f,  0.5f, -0.5f}, { 0.5f, -0.5f, -0.5f}, {-0.5f, -0.5f, -0.5f},
            // +X face
            { 0.5f, -0.5f, -0.5f}, { 0.5f,  0.5f, -0.5f}, { 0.5f,  0.5f,  0.5f}, { 0.5f, -0.5f,  0.5f},
            // -X face
            {-0.5f, -0.5f,  0.5f}, {-0.5f,  0.5f,  0.5f}, {-0.5f,  0.5f, -0.5f}, {-0.5f, -0.5f, -0.5f},
            // +Y face
            {-0.5f,  0.5f,  0.5f}, { 0.5f,  0.5f,  0.5f}, { 0.5f,  0.5f, -0.5f}, {-0.5f,  0.5f, -0.5f},
            // -Y face
            {-0.5f, -0.5f, -0.5f}, { 0.5f, -0.5f, -0.5f}, { 0.5f, -0.5f,  0.5f}, {-0.5f, -0.5f,  0.5f}
        };
        
        float[][] uvCoords = {
            {0.0f, 1.0f}, {1.0f, 1.0f}, {1.0f, 0.0f}, {0.0f, 0.0f}
        };
        
        float cosY = (float) Math.cos(spinAngle);
        float sinY = (float) Math.sin(spinAngle);
        float cosX = (float) Math.cos(spinAngle * 0.5f);
        float sinX = (float) Math.sin(spinAngle * 0.5f);
        
        float[][] localNormals = {
            {0, 0, 1},   // +Z
            {0, 0, -1},  // -Z
            {1, 0, 0},   // +X
            {-1, 0, 0},  // -X
            {0, 1, 0},   // +Y
            {0, -1, 0}   // -Y
        };
        
        float lnx = localNormals[faceIdx][0];
        float lny = localNormals[faceIdx][1];
        float lnz = localNormals[faceIdx][2];
        
        float nx1 = lnx * cosY - lnz * sinY;
        float nz1 = lnx * sinY + lnz * cosY;
        float ny1 = lny;
        
        float nx2 = nx1;
        float ny2 = ny1 * cosX - nz1 * sinX;
        float nz2 = ny1 * sinX + nz1 * cosX;
        
        if (nz2 <= 0.0f) {
            return false;
        }
        
        for (int i = 0; i < 4; i++) {
            int idx = faceIdx * 4 + i;
            float lx = cubeVertices[idx][0] * size;
            float ly = cubeVertices[idx][1] * size;
            float lz = cubeVertices[idx][2] * size;
            
            float x1 = lx * cosY - lz * sinY;
            float z1 = lx * sinY + lz * cosY;
            float y1 = ly;
            
            float x2 = x1;
            float y2 = y1 * cosX - z1 * sinX;
            float z2 = y1 * sinX + z1 * cosX;
            
            float rx = cx + x2 * ux + y2 * vx + z2 * wx;
            float ry = cy + x2 * uy + y2 * vy + z2 * wy;
            float rz = cz + x2 * uz + y2 * vz + z2 * wz;
            
            buffer.addVertex(matrix, rx, ry, rz)
                  .setUv(uvCoords[i][0], uvCoords[i][1])
                  .setColor(r, g, b, alpha);
        }
        return true;
    }

    public static void renderSpaceStars(PoseStack poseStack, float visibility, Camera camera, float celestialAngle) {
        ensureSpaceStars();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        
        boolean isUsingSpyglass = false;
        if (mc.player != null && mc.player.isUsingItem() && mc.player.getUseItem().is(net.minecraft.world.item.Items.SPYGLASS)) {
            isUsingSpyglass = true;
        }
        
        Vector3f lookVec = new Vector3f(0.0f, 0.0f, -1.0f).rotate(camera.rotation());
        
        Tesselator tesselator = Tesselator.getInstance();
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        
        poseStack.pushPose();
        
        // Rotate the entire starfield slowly around the X axis in sync with the celestial sphere (Sun/Moon orbit)
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(celestialAngle * 360.0f));
        
        Matrix4f matrix = poseStack.last().pose();
        float radius = SKYBOX_DISTANCE;
                // --- Draw Stars (Constellation lines are completely removed so players can discover them organically!) ---
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean mainBufferHasVertices = false;
        
        for (SpaceStar star : SPACE_STARS) {
            // Camera-relative coordinates — no precision loss!
            float px = star.x * radius;
            float py = star.y * radius;
            float pz = star.z * radius;
            
            float twinkle = 0.62f + 0.38f * (float) Math.sin((System.currentTimeMillis() / 1000.0) * star.twinkleSpeed + star.twinkleOffset);
            float alpha = visibility * twinkle;
            
            // Prevent stars from overlapping the Sun (directly overhead in rotated sky-space, Y = 1.0) 
            // and the Moon (directly below in rotated sky-space, Y = -1.0)
            float absY = Math.abs(star.y);
            if (absY > 0.94f) {
                float fade = (0.98f - absY) / 0.04f;
                alpha *= Mth.clamp(fade, 0.0f, 1.0f);
            }
            
            if (alpha <= 0) continue;
            
            float ux, uy, uz;
            if (Math.abs(star.x) < 0.99f) {
                float len = (float) Math.sqrt(star.y * star.y + star.z * star.z);
                ux = 0; uy = -star.z / len; uz = star.y / len;
            } else {
                float len = (float) Math.sqrt(star.x * star.x + star.y * star.y);
                ux = -star.y / len; uy = star.x / len; uz = 0;
            }
            
            float vx = star.y * uz - star.z * uy;
            float vy = star.z * ux - star.x * uz;
            float vz = star.x * uy - star.y * ux;
            
            float s = star.size * 0.1f;
            
            boolean isTargeted = false;
            if (isUsingSpyglass && star.type == 2) {
                // Determine current rotated star direction in world space
                float angle = (celestialAngle * 360.0f) * ((float)Math.PI / 180.0f);
                float rx = star.x;
                float ry = star.y * (float)Math.cos(angle) - star.z * (float)Math.sin(angle);
                float rz = star.y * (float)Math.sin(angle) + star.z * (float)Math.cos(angle);
                
                float dot = lookVec.x() * rx + lookVec.y() * ry + lookVec.z() * rz;
                if (dot > 0.999f) { // Aiming directly at the star through spyglass
                    isTargeted = true;
                }
            }
            
            if (isTargeted) {
                // Pause non-textured rendering safely
                if (mainBufferHasVertices) {
                    BufferUploader.drawWithShader(buffer.buildOrThrow());
                    mainBufferHasVertices = false;
                } else {
                    // Discard empty builder cleanly to avoid state leaks
                    buffer.build();
                }
                
                // Ensure dynamic turbulent texture is loaded
                ensureStarCubeTexture();
                
                float spin = (System.currentTimeMillis() % 4000) / 4000.0f * (float)Math.PI * 2.0f;
                float cubeSize = s * 2.3f;
                
                float wx = star.x;
                float wy = star.y;
                float wz = star.z;
                
                // Draw 100% Opaque, Textured, Solid star core!
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                RenderSystem.setShaderTexture(0, STAR_CUBE_TEXTURE_ID);
                BufferBuilder texBuffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                boolean texHasVertices = false;
                
                for (int face = 0; face < 6; face++) {
                    float shade = 1.0f;
                    if (face == 0 || face == 1) shade = 0.85f; // Front/back
                    if (face == 2 || face == 3) shade = 0.70f; // Left/right
                    if (face == 4 || face == 5) shade = 0.98f; // Top/bottom
                    
                    if (drawTexturedRotatingCubeFace(texBuffer, matrix, px, py, pz, cubeSize, spin,
                                                     ux, uy, uz, vx, vy, vz, wx, wy, wz,
                                                     face, star.r * shade, star.g * shade, star.b * shade, 1.0f)) {
                        texHasVertices = true;
                    }
                }
                
                if (texHasVertices) {
                    BufferUploader.drawWithShader(texBuffer.buildOrThrow());
                } else {
                    texBuffer.build();
                }
                
                // Resume non-textured shader for glowing shells
                RenderSystem.setShader(GameRenderer::getPositionColorShader);
                buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                
                // Render 5 layers of high-fidelity volumetric glowing shells!
                float[] glowSizes = {1.22f, 1.48f, 1.78f, 2.12f, 2.50f};
                float[] glowAlphas = {0.45f, 0.32f, 0.20f, 0.10f, 0.04f};
                for (int layer = 0; layer < 5; layer++) {
                    float sizeMult = glowSizes[layer];
                    float alphaMult = glowAlphas[layer];
                    for (int face = 0; face < 6; face++) {
                        float shade = 1.0f;
                        if (face == 0 || face == 1) shade = 0.82f;
                        if (face == 2 || face == 3) shade = 0.65f;
                        if (face == 4 || face == 5) shade = 0.95f;
                        
                        if (drawRotatingCubeFace(buffer, matrix, px, py, pz, cubeSize * sizeMult, spin,
                                                 ux, uy, uz, vx, vy, vz, wx, wy, wz,
                                                 face, star.r * shade, star.g * shade, star.b * shade, alphaMult)) {
                            mainBufferHasVertices = true;
                        }
                    }
                }
            } else if (star.type == 2) {
                float L = s * 7.5f; // Extremely long tips!
                float W = s * 0.45f; // Thin, elegant inner corners!
                
                // Quad 1: Top-Right
                buffer.addVertex(matrix, px, py, pz).setColor(star.r, star.g, star.b, alpha);
                buffer.addVertex(matrix, px + L * ux, py + L * uy, pz + L * uz).setColor(star.r, star.g, star.b, 0.0f);
                buffer.addVertex(matrix, px + W * ux + W * vx, py + W * uy + W * vy, pz + W * uz + W * vz).setColor(star.r, star.g, star.b, alpha * 0.9f);
                buffer.addVertex(matrix, px + L * vx, py + L * vy, pz + L * vz).setColor(star.r, star.g, star.b, 0.0f);
                
                // Quad 2: Top-Left
                buffer.addVertex(matrix, px, py, pz).setColor(star.r, star.g, star.b, alpha);
                buffer.addVertex(matrix, px + L * vx, py + L * vy, pz + L * vz).setColor(star.r, star.g, star.b, 0.0f);
                buffer.addVertex(matrix, px - W * ux + W * vx, py - W * uy + W * vy, pz - W * uz + W * vz).setColor(star.r, star.g, star.b, alpha * 0.9f);
                buffer.addVertex(matrix, px - L * ux, py - L * uy, pz - L * uz).setColor(star.r, star.g, star.b, 0.0f);
                
                // Quad 3: Bottom-Left
                buffer.addVertex(matrix, px, py, pz).setColor(star.r, star.g, star.b, alpha);
                buffer.addVertex(matrix, px - L * ux, py - L * uy, pz - L * uz).setColor(star.r, star.g, star.b, 0.0f);
                buffer.addVertex(matrix, px - W * ux - W * vx, py - W * uy - W * vy, pz - W * uz - W * vz).setColor(star.r, star.g, star.b, alpha * 0.9f);
                buffer.addVertex(matrix, px - L * vx, py - L * vy, pz - L * vz).setColor(star.r, star.g, star.b, 0.0f);
                
                // Quad 4: Bottom-Right
                buffer.addVertex(matrix, px, py, pz).setColor(star.r, star.g, star.b, alpha);
                buffer.addVertex(matrix, px - L * vx, py - L * vy, pz - L * vz).setColor(star.r, star.g, star.b, 0.0f);
                buffer.addVertex(matrix, px + W * ux - W * vx, py + W * uy - W * vy, pz + W * uz - W * vz).setColor(star.r, star.g, star.b, alpha * 0.9f);
                buffer.addVertex(matrix, px + L * ux, py + L * uy, pz + L * uz).setColor(star.r, star.g, star.b, 0.0f);
                mainBufferHasVertices = true;
            } else {
                buffer.addVertex(matrix, px - s * ux - s * vx, py - s * uy - s * vy, pz - s * uz - s * vz).setColor(star.r, star.g, star.b, alpha);
                buffer.addVertex(matrix, px - s * ux + s * vx, py - s * uy + s * vy, pz - s * uz + s * vz).setColor(star.r, star.g, star.b, alpha);
                buffer.addVertex(matrix, px + s * ux + s * vx, py + s * uy + s * vy, pz + s * uz + s * vz).setColor(star.r, star.g, star.b, alpha);
                buffer.addVertex(matrix, px + s * ux - s * vx, py + s * uy - s * vy, pz + s * uz - s * vz).setColor(star.r, star.g, star.b, alpha);
                mainBufferHasVertices = true;
            }
        }
        
        if (mainBufferHasVertices) {
            BufferUploader.drawWithShader(buffer.buildOrThrow());
        } else {
            buffer.build();
        }
        
        poseStack.popPose();
        
        // Restore standard rendering state
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
    }

    private static HighWindSoundInstance windSoundInstance = null;
    private static boolean clearedOtherSounds = false;

    private static class ShipSpeedTracker {
        public Vector3d lastPos;
        public Vector3d velocity = new Vector3d();
        public double speed = 0;
        public float sonicBoomTimer = 0;
        public boolean wasSupersonic = false;
    }

    private static final Map<UUID, ShipSpeedTracker> CLIENT_SPEED_TRACKERS = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onPlaySound(net.neoforged.neoforge.client.event.sound.PlaySoundEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        double camY = mc.gameRenderer.getMainCamera().getPosition().y + SkyDataHandler.getHeightOffsetForLevel(mc.level.dimension());

        // At high altitudes (between 1000m and 2000m), mute ALL sounds except our high wind loop!
        if (camY >= 1000.0 && camY < 2000.0) {
            net.minecraft.client.resources.sounds.SoundInstance sound = event.getSound();
            if (sound != null) {
                String path = sound.getLocation().getPath();
                if (!path.equals("high_wind")) {
                    event.setSound(null);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            windSoundInstance = null;
            return;
        }

        double camY = mc.gameRenderer.getMainCamera().getPosition().y + SkyDataHandler.getHeightOffsetForLevel(mc.level.dimension());

        // Manage the high wind looping sound
        if (camY >= 800.0 && camY <= 2200.0) {
            if (windSoundInstance == null || !mc.getSoundManager().isActive(windSoundInstance)) {
                windSoundInstance = new HighWindSoundInstance();
                mc.getSoundManager().play(windSoundInstance);
            }
        } else {
            if (windSoundInstance != null) {
                windSoundInstance = null;
            }
        }

        // Stop all currently playing sounds once when crossing into the high wind zone
        if (camY >= 1000.0 && camY < 2000.0) {
            if (!clearedOtherSounds) {
                mc.getSoundManager().stop(); // clear all active sounds
                clearedOtherSounds = true;
            }
        } else {
            clearedOtherSounds = false;
        }

        // Track client-side speeds and supersonic states for the 3D vapor cone animation
        SubLevelContainer container = SubLevelContainer.getContainer(mc.level);
        if (container != null) {
            for (SubLevel subLevel : container.getAllSubLevels()) {
                UUID uuid = subLevel.getUniqueId();
                Vector3d currentPos = subLevel.logicalPose().position();
                if (currentPos != null) {
                    ShipSpeedTracker tracker = CLIENT_SPEED_TRACKERS.computeIfAbsent(uuid, k -> {
                        ShipSpeedTracker t = new ShipSpeedTracker();
                        t.lastPos = new Vector3d(currentPos);
                        return t;
                    });

                    // Calculate speed in blocks per second (20 ticks per second)
                    Vector3d vel = new Vector3d(currentPos).sub(tracker.lastPos).mul(20.0);
                    
                    // Filter abnormal acceleration (drags via physics gun / teleport)
                    boolean abnormalAcceleration = false;
                    if (tracker.velocity != null) {
                        double accel = new Vector3d(vel).sub(tracker.velocity).length() * 20.0; // accel in m/s^2 (20 ticks/sec)
                        if (accel > 150.0) {
                            abnormalAcceleration = true;
                        }
                    } else {
                        abnormalAcceleration = true;
                    }
                    
                    tracker.velocity = vel;
                    tracker.speed = vel.length();
                    tracker.lastPos.set(currentPos);

                    double mach1 = dev.devce.rocketnautics.RocketConfig.SERVER.sonicBoomSpeedThreshold.get();
                    double machReset = mach1 * 0.85;

                    if (tracker.speed >= mach1) {
                        if (!tracker.wasSupersonic) {
                            tracker.wasSupersonic = true;
                            if (!abnormalAcceleration) {
                                tracker.sonicBoomTimer = 1.0f; // Trigger cone!
                            }
                        }
                    } else if (tracker.speed < machReset) {
                        if (tracker.wasSupersonic) {
                            tracker.wasSupersonic = false;
                        }
                    }
                }
            }
        }
    }

    private static void renderVaporCones(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // Animate the timers framerate-independently
        float delta = (float) event.getPartialTick().getRealtimeDeltaTicks() * 0.05f; 

        float maxTimer = 0.0f;
        Vector3d activeShipPos = null;

        SubLevelContainer container = SubLevelContainer.getContainer(mc.level);
        if (container != null) {
            for (SubLevel subLevel : container.getAllSubLevels()) {
                UUID uuid = subLevel.getUniqueId();
                ShipSpeedTracker tracker = CLIENT_SPEED_TRACKERS.get(uuid);
                if (tracker != null) {
                    if (debugSonicBoom) {
                        tracker.sonicBoomTimer -= delta;
                        if (tracker.sonicBoomTimer <= 0.0f) tracker.sonicBoomTimer = 1.0f;
                    } else if (tracker.sonicBoomTimer > 0.0f) {
                        tracker.sonicBoomTimer = Math.max(0.0f, tracker.sonicBoomTimer - delta);
                    }
                    
                    if (tracker.sonicBoomTimer > 0.0f) {
                        Vector3d renderVel = (tracker.velocity != null && tracker.velocity.lengthSquared() > 0.1) 
                                ? tracker.velocity : new Vector3d(1, 0, 0);
                        renderVaporCone(event.getPoseStack(), subLevel.logicalPose().position(), renderVel, tracker.sonicBoomTimer, event.getPartialTick().getGameTimeDeltaTicks());
                        if (tracker.sonicBoomTimer > maxTimer) {
                            maxTimer = tracker.sonicBoomTimer;
                            activeShipPos = subLevel.logicalPose().position();
                        }
                    }
                }
            }
        }

        // Dynamically update Veil post-processing shockwave shader when a supersonic ship is in view
        if (activeShipPos != null && maxTimer > 0.0f) {
            updateShockwavePostProcessing(event, activeShipPos, maxTimer);
        } else {
            try {
                var renderer = foundry.veil.api.client.render.VeilRenderSystem.renderer();
                if (renderer != null && renderer.getPostProcessingManager() != null) {
                    renderer.getPostProcessingManager().remove(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "shockwave"));
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void updateShockwavePostProcessing(RenderLevelStageEvent event, Vector3d shipPos, float timer) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        try {
            // Check if Veil renderer and post-processing manager are active and ready
            var renderer = foundry.veil.api.client.render.VeilRenderSystem.renderer();
            if (renderer == null) return;
            var postManager = renderer.getPostProcessingManager();
            if (postManager == null) return;

            ResourceLocation shockwaveId = ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "shockwave");

            // Compute relative position from ship to camera
            Camera camera = mc.gameRenderer.getMainCamera();
            float rx = (float) (shipPos.x() - camera.getPosition().x);
            float ry = (float) (shipPos.y() - camera.getPosition().y);
            float rz = (float) (shipPos.z() - camera.getPosition().z);

            // Transform world relative coordinate to camera space (eye space) using conjugate rotation
            Quaternionf viewRot = camera.rotation().conjugate(new Quaternionf());
            Vector3f eyePos = viewRot.transform(new Vector3f(rx, ry, rz));

            // If the ship is behind the camera eye-plane, don't distort
            if (eyePos.z > 0.0f) {
                postManager.remove(shockwaveId);
                return;
            }

            // Project eye-space coordinates into clip space using active projection matrix
            Matrix4f proj = renderer.getCameraMatrices().getProjectionMatrix();
            org.joml.Vector4f clipPos = new org.joml.Vector4f(eyePos.x, eyePos.y, eyePos.z, 1.0f);
            proj.transform(clipPos);

            if (clipPos.w <= 0.0f) {
                postManager.remove(shockwaveId);
                return;
            }

            // Perform perspective divide to get normalized device coordinates (NDC)
            float ndcX = clipPos.x / clipPos.w;
            float ndcY = clipPos.y / clipPos.w;

            // Map NDC [-1, 1] coordinates to screen-space [0, 1] coordinates
            float screenX = (ndcX + 1.0f) * 0.5f;
            float screenY = (1.0f - ndcY) * 0.5f;

            // Prevent rendering if coordinate is far out of viewport bounds
            if (screenX < -0.5f || screenX > 1.5f || screenY < -0.5f || screenY > 1.5f) {
                postManager.remove(shockwaveId);
                return;
            }

            // Ensure the shockwave pipeline is active in Veil
            postManager.add(shockwaveId);

            var pipeline = postManager.getPipeline(shockwaveId);
            if (pipeline != null) {
                // Animate progress/radius, thickness expansion, and decaying force
                float progress = 1.0f - timer;
                float radius = progress * 1.5f; // expands outward
                float force = timer * 0.06f; // decays as shockwave diffuses
                float thickness = 0.08f + progress * 0.05f; // thickens slightly as it travels
                float aspect = (float) mc.getWindow().getWidth() / mc.getWindow().getHeight();

                pipeline.getUniformSafe("Center").setVector(screenX, screenY);
                pipeline.getUniformSafe("Radius").setFloat(radius);
                pipeline.getUniformSafe("Thickness").setFloat(thickness);
                pipeline.getUniformSafe("Force").setFloat(force);
                pipeline.getUniformSafe("AspectRatio").setFloat(aspect);
            }
        } catch (Throwable t) {
            // Gracefully ignore to prevent client-side crashes if API behaves unexpectedly
        }
    }

    private static void renderVaporCone(PoseStack poseStack, Vector3d shipPos, Vector3d velocity, float timer, float partialTick) {
        ensureSonicBoomTexture();
        if (SONIC_BOOM_TEXTURE_OBJ != null) {
            SONIC_BOOM_TEXTURE_OBJ.setFilter(false, false);
        }
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        
        // Position of the ship relative to the camera
        double relX = shipPos.x() - camera.getPosition().x;
        double relY = shipPos.y() - camera.getPosition().y;
        double relZ = shipPos.z() - camera.getPosition().z;

        poseStack.pushPose();
        poseStack.translate(relX, relY, relZ);

        Vector3d dir = new Vector3d(velocity);
        double velLength = dir.length();
        if (velLength > 0.001) {
            dir.normalize();
        } else {
            dir.set(0, 1, 0);
        }

        // Perpendicular axes to draw the cone surface
        Vector3d u = new Vector3d();
        Vector3d v = new Vector3d();
        if (Math.abs(dir.x) > 0.9) {
            u.set(0, 1, 0);
        } else {
            u.set(1, 0, 0);
        }
        dir.cross(u, v).normalize();
        dir.cross(v, u).normalize();

        // Expansion scale and opacity
        float progress = 1.0f - timer;
        float scale = 1.0f + progress * 5.0f; // Expands up to 6x size
        float alpha = timer * 0.8f; // Smooth fade out

        // Enable blending and setup shader
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull(); // Double-sided rendering so it is visible inside the cockpit!
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        
        if (SONIC_BOOM_TEXTURE_ID != null) {
            RenderSystem.setShaderTexture(0, SONIC_BOOM_TEXTURE_ID);
        } else {
            RenderSystem.setShaderTexture(0, ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "textures/environment/planet_map.png"));
        }

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        // Prandtl-Glauert vapor cone profile (dome that flares out and tapers back)
        double[][] profile = {
            { 1.0, 0.1 },   // Nose
            { -0.5, 0.8 },  // Mid-front
            { -2.0, 3.2 },  // Shock crest/peak
            { -3.0, 2.8 },  // Rear expansion
            { -4.5, 1.5 },  // Tapering tail
            { -6.0, 0.2 }   // Tail tip
        };

        int segments = 32;
        Matrix4f matrix = poseStack.last().pose();

        for (int r = 0; r < profile.length - 1; r++) {
            double z1 = profile[r][0] * scale;
            double rad1 = profile[r][1] * scale;
            double z2 = profile[r + 1][0] * scale;
            double rad2 = profile[r + 1][1] * scale;

            // Scroll texture coordinates down the cone to represent extreme wind forces!
            float tOffset = (System.currentTimeMillis() % 2000L) / 2000.0f;

            for (int s = 0; s < segments; s++) {
                double angle1 = (2.0 * Math.PI * s) / segments;
                double angle2 = (2.0 * Math.PI * (s + 1)) / segments;

                double cos1_1 = Math.cos(angle1);
                double sin1_1 = Math.sin(angle1);
                double cos1_2 = Math.cos(angle2);
                double sin1_2 = Math.sin(angle2);

                // Ring 1 vertices
                float x1_1 = (float) (dir.x * z1 + (u.x * cos1_1 + v.x * sin1_1) * rad1);
                float y1_1 = (float) (dir.y * z1 + (u.y * cos1_1 + v.y * sin1_1) * rad1);
                float z1_1 = (float) (dir.z * z1 + (u.z * cos1_1 + v.z * sin1_1) * rad1);

                float x1_2 = (float) (dir.x * z1 + (u.x * cos1_2 + v.x * sin1_2) * rad1);
                float y1_2 = (float) (dir.y * z1 + (u.y * cos1_2 + v.y * sin1_2) * rad1);
                float z1_2 = (float) (dir.z * z1 + (u.z * cos1_2 + v.z * sin1_2) * rad1);

                // Ring 2 vertices
                float x2_1 = (float) (dir.x * z2 + (u.x * cos1_1 + v.x * sin1_1) * rad2);
                float y2_1 = (float) (dir.y * z2 + (u.y * cos1_1 + v.y * sin1_1) * rad2);
                float z2_1 = (float) (dir.z * z2 + (u.z * cos1_1 + v.z * sin1_1) * rad2);

                float x2_2 = (float) (dir.x * z2 + (u.x * cos1_2 + v.x * sin1_2) * rad2);
                float y2_2 = (float) (dir.y * z2 + (u.y * cos1_2 + v.y * sin1_2) * rad2);
                float z2_2 = (float) (dir.z * z2 + (u.z * cos1_2 + v.z * sin1_2) * rad2);

                float uMin = (float) s / segments;
                float uMax = (float) (s + 1) / segments;
                float vMin = (float) r / profile.length + tOffset;
                float vMax = (float) (r + 1) / profile.length + tOffset;

                buffer.addVertex(matrix, x1_1, y1_1, z1_1).setColor(1.0f, 1.0f, 1.0f, alpha).setUv(uMin, vMin);
                buffer.addVertex(matrix, x1_2, y1_2, z1_2).setColor(1.0f, 1.0f, 1.0f, alpha).setUv(uMax, vMin);
                buffer.addVertex(matrix, x2_2, y2_2, z2_2).setColor(1.0f, 1.0f, 1.0f, alpha).setUv(uMax, vMax);
                buffer.addVertex(matrix, x2_1, y2_1, z2_1).setColor(1.0f, 1.0f, 1.0f, alpha).setUv(uMin, vMax);
            }
        }

        BufferUploader.drawWithShader(buffer.buildOrThrow());

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();

        poseStack.popPose();
    }

    public static ResourceLocation NEBULA_TEXTURE_ID = null;
    private static DynamicTexture NEBULA_TEXTURE_OBJ = null;

    public static void ensureNebulaTexture() {
        if (NEBULA_TEXTURE_ID != null) return;
        Minecraft mc = Minecraft.getInstance();
        int size = 256;
        NativeImage image = new NativeImage(size, size, false);
        
        java.util.Random rand = new java.util.Random(42L);
        Noise2D noiseGen = new Noise2D(64, rand);
        
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float u = (x / (float)size) * 3.0f;
                float v = (y / (float)size) * 3.0f;
                
                float warpX = noiseGen.sample(u * 1.5f + 0.5f, v * 1.5f + 1.2f) * 1.2f;
                float warpY = noiseGen.sample(u * 1.5f + 2.3f, v * 1.5f + 0.8f) * 1.2f;
                
                float tu = u + warpX;
                float tv = v + warpY;
                
                float n1 = noiseGen.sample(tu, tv);
                float n2 = noiseGen.sample(tu * 2.0f + 5.1f, tv * 2.0f + 3.7f) * 0.5f;
                float n = n1 + n2; // 0.0 to 1.5
                
                double dx = (x - size / 2.0) / (size / 2.0);
                double dy = (y - size / 2.0) / (size / 2.0);
                double dist = Math.sqrt(dx * dx + dy * dy);
                float vignette = (float) Math.max(0, 1.0 - dist * 1.2);
                n *= vignette;
                
                int r = 0, g = 0, b = 0, alpha = 0;
                if (n > 0.15f) {
                    float density = (n - 0.15f) / 1.35f;
                    // Gorgeous Deep Space Nebula Colors: Neon Purple, Cosmic Violet, Magenta, and Cyan!
                    r = (int) (120 + density * 135);
                    g = (int) (20 + density * 180);
                    b = (int) (200 + density * 55);
                    alpha = (int) (density * 130); // max opacity 130 for beautiful transparency
                }
                
                int color = (alpha << 24) | (b << 16) | (g << 8) | r;
                image.setPixelRGBA(x, y, color);
            }
        }
        NEBULA_TEXTURE_OBJ = new DynamicTexture(image);
        NEBULA_TEXTURE_ID = mc.getTextureManager().register("rocketnautics_nebula", NEBULA_TEXTURE_OBJ);
        NEBULA_TEXTURE_OBJ.setFilter(true, false); // Bilinear filtering for soft gas clouds!
        image.close();
    }

    public static void renderCosmicNebula(PoseStack poseStack, Camera camera, float celestialAngle) {
        ensureNebulaTexture();
        if (NEBULA_TEXTURE_ID == null) return;
        
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, NEBULA_TEXTURE_ID);
        
        poseStack.pushPose();
        
        // Rotate extremely slowly, independent of stars
        float slowAngle = (System.currentTimeMillis() % 1200000) / 1200000.0f * 360.0f;
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(slowAngle));
        
        Matrix4f matrix = poseStack.last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        
        float size = SKYBOX_DISTANCE + 0.5f; // slightly larger than stars so stars sit in/in front of the nebula, but inside far clip plane!
        float alpha = 0.45f; // soft overall visibility
        
        // Render large textured quads on 4 vertical sides and top/bottom to form a beautiful cosmic envelope
        // Front
        buffer.addVertex(matrix, -size, size, -size).setUv(0, 0).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, -size, -size, -size).setUv(0, 1).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, size, -size, -size).setUv(1, 1).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, size, size, -size).setUv(1, 0).setColor(1.0f, 1.0f, 1.0f, alpha);
        
        // Back
        buffer.addVertex(matrix, size, size, size).setUv(0, 0).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, size, -size, size).setUv(0, 1).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, -size, -size, size).setUv(1, 1).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, -size, size, size).setUv(1, 0).setColor(1.0f, 1.0f, 1.0f, alpha);
        
        // Left
        buffer.addVertex(matrix, -size, size, size).setUv(0, 0).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, -size, -size, size).setUv(0, 1).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, -size, -size, -size).setUv(1, 1).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, -size, size, -size).setUv(1, 0).setColor(1.0f, 1.0f, 1.0f, alpha);
        
        // Right
        buffer.addVertex(matrix, size, size, -size).setUv(0, 0).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, size, -size, -size).setUv(0, 1).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, size, -size, size).setUv(1, 1).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, size, size, size).setUv(1, 0).setColor(1.0f, 1.0f, 1.0f, alpha);
        
        // Top
        buffer.addVertex(matrix, -size, size, size).setUv(0, 0).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, -size, size, -size).setUv(0, 1).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, size, size, -size).setUv(1, 1).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, size, size, size).setUv(1, 0).setColor(1.0f, 1.0f, 1.0f, alpha);

        // Bottom
        buffer.addVertex(matrix, -size, -size, -size).setUv(0, 0).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, -size, -size, size).setUv(0, 1).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, size, -size, size).setUv(1, 1).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, size, -size, -size).setUv(1, 0).setColor(1.0f, 1.0f, 1.0f, alpha);

        BufferUploader.drawWithShader(buffer.buildOrThrow());
        
        poseStack.popPose();
        
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
    }
}
