package dev.devce.rocketnautics.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.devce.rocketnautics.RocketConfig;
import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.content.orbit.universe.DeepSpacePosition;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import dev.devce.rocketnautics.network.PlanetRenderRequestPayload;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectAVLTreeMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntObjectPair;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ArrayListDeque;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.RotationConvention;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.orekit.frames.Frame;
import org.orekit.orbits.Orbit;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;
import org.orekit.utils.TimeStampedPVCoordinates;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

@EventBusSubscriber(modid = RocketNautics.MODID, value = Dist.CLIENT)
public final class DeepSpaceHandler {

    private static @Nullable UniverseDefinition UNIVERSE;
    private static final Int2ObjectAVLTreeMap<IntObjectPair<DeepSpaceTexture>> KNOWN_RENDER_DATA = new Int2ObjectAVLTreeMap<>();

    private static long receivedPositionTick = -1;
    private static final DeepSpacePosition receivedPosition = new DeepSpacePosition();

    private static final ArrayListDeque<Pair<AbsoluteDate, Orbit>> positionPredictions = new ArrayListDeque<>(100);
    private static final DeepSpacePosition nextPrediction = new DeepSpacePosition();

    public static void receiveUniverse(UniverseDefinition definition) {
        UNIVERSE = definition;
        receivedPosition.reset();
        nextPrediction.reset();
        receivedPositionTick = -1;
        KNOWN_RENDER_DATA.values().forEach(p -> p.right().retire());
        KNOWN_RENDER_DATA.clear();
    }

    public static boolean hasReceivedPosition() {
        return receivedPositionTick != -1;
    }

    public static void receivePosition(FriendlyByteBuf buf) {
        if (UNIVERSE != null) {
            receivedPositionTick = Minecraft.getInstance().levelRenderer.getTicks();
            receivedPosition.read(buf, UNIVERSE);
            positionPredictions.clear();
            receivedPosition.copyTo(nextPrediction);
        }
    }

    public static @Nullable UniverseDefinition getUniverse() {
        return UNIVERSE;
    }

    public static DeepSpacePosition getReceivedPosition() {
        return receivedPosition;
    }

    public static AbsoluteDate getRenderDate(float partial) {
        return getRenderDate(Minecraft.getInstance().levelRenderer.getTicks(), partial);
    }

    public static AbsoluteDate getRenderDate(long ticksSince, float partial) {
        return receivedPosition.getLocalUniverseTime().shiftedBy(receivedPosition.getTimescale() * ((double) partial + (ticksSince - receivedPositionTick)) / 20);
    }

    public static Iterator<Vector3D> getPositionPrediction(Frame frame, int upTo) {
        if (UNIVERSE == null) return Collections.emptyIterator();
        AbsoluteDate renderDate = getRenderDate(0);
//        while (positionPredictions.size() > 1 && positionPredictions.get(1).left().isBefore(renderDate)) {
//            positionPredictions.removeFirst();
//        }
        return new Iterator<>() {
            int index = 0;

            @Override
            public boolean hasNext() {
                return index < upTo && index < 10000;
            }

            @Override
            public Vector3D next() {
                while (positionPredictions.size() <= index) {
                    // at each step, compute the distance from our orbited body, compute our current speed,
                    // and determine the length of time it would take to travel that distance.
                    // finally, step this amount of time multiplied by our config value.
                    TimeStampedPVCoordinates coords = nextPrediction.getCurrentPVCoords();
                    if (coords.getDate().isAfterOrEqualTo(renderDate) | true) {
                        positionPredictions.addLast(Pair.of(coords.getDate(), nextPrediction.getCurrentOrbit()));
                    }
                    double distance = coords.getPosition().getNorm();
                    double speed = coords.getVelocity().getNorm();
                    int lookaheadTicks = (int) (RocketConfig.CLIENT.orbitPredictionStepFactor.get() * distance / speed);
                    nextPrediction.setTimescale(lookaheadTicks);
                    nextPrediction.propagate(UNIVERSE);
                }
                Pair<AbsoluteDate, Orbit> pair = positionPredictions.get(index);
                index++;
                return pair.right().getPosition(pair.left(), frame);
            }
        };
    }

    public static Stream<AbsoluteDate> getPredictionDates(int maximum) {
        return positionPredictions.stream().map(Pair::left).limit(maximum);
    }

    public static Iterator<Orbit> getPredictionOrbits() {
        return new Iterator<>() {
            int index = 0;
            Orbit previous = null;
            Orbit foundNext = null;

            private void ensureNext() {
                while (foundNext == null && index < positionPredictions.size()) {
                    Orbit find = positionPredictions.get(index).right();
                    if (find != previous) {
                        foundNext = find;
                        previous = find;
                    }
                    index++;
                }
            }

            @Override
            public boolean hasNext() {
                ensureNext();
                return foundNext != null;
            }

            @Override
            public Orbit next() {
                ensureNext();
                Orbit ret = foundNext;
                foundNext = null;
                return ret;
            }
        };
    }

    public static void receiveRenderData(int id, byte[] data, int powerScale) {
        KNOWN_RENDER_DATA.put(id, IntObjectPair.of(powerScale, DeepSpaceTexture.construct(id, data)));
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || UNIVERSE == null) return;
        if (!DeepSpaceData.isDeepSpace()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (receivedPositionTick == -1) return;
        Camera camera = mc.gameRenderer.getMainCamera();

        PoseStack poseStack = event.getPoseStack();

        // 1. Render custom cosmic nebula and HD space stars first (these handle camera rotation internally!)
        float celestialAngle = mc.level.getTimeOfDay(event.getPartialTick().getGameTimeDeltaTicks());
        SkyHandler.renderCosmicNebula(poseStack, camera, celestialAngle);
        SkyHandler.renderSpaceStars(poseStack, 1.0f, camera, celestialAngle);

        // 2. Prepare the pose stack for celestial bodies / planets rendering
        poseStack.pushPose();

        Matrix4f matrix = poseStack.last().pose();
        matrix.identity();

        Quaternionf invRot = new Quaternionf(camera.rotation());
        invRot.conjugate();
        poseStack.mulPose(invRot);

        int renderDist = mc.options.renderDistance().get();
        IntList needRenderData = new IntArrayList();
        AbsoluteDate currentDate = getRenderDate(event.getPartialTick().getGameTimeDeltaPartialTick(true));
        Vector3D posInFrame = receivedPosition.getPosition(currentDate);
        Iterator<Pair<Vector3D, CubePlanet>> iter = UNIVERSE.getPlanets().stream()
                .map(planet -> Pair.of(planet.posInMyFrame(currentDate, posInFrame, receivedPosition.getFrame()), planet))
                .sorted(Comparator.comparingDouble(p -> -p.left().getNormSq())).iterator(); // sort descending, we want to render furthest away first.
        while (iter.hasNext()) {
            Pair<Vector3D, CubePlanet> planet = iter.next();
            poseStack.pushPose();
            if (renderPlanet(planet.right(), planet.left(), poseStack, currentDate, renderDist, celestialAngle, event.getPartialTick().getGameTimeDeltaTicks())) {
                needRenderData.add(planet.right().id());
            }
            poseStack.popPose();
        }
        if (!needRenderData.isEmpty()) PacketDistributor.sendToServer(new PlanetRenderRequestPayload(needRenderData.toIntArray(), SkyHandler.getMaximumScale()));
        poseStack.popPose();
    }

    private static boolean renderPlanet(CubePlanet planet, Vector3D ourPosInPlanetFrame, PoseStack poseStack, AbsoluteDate date, float renderDist, float celestialAngle, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        IntObjectPair<DeepSpaceTexture> render = KNOWN_RENDER_DATA.get(planet.id());
        if (render == null || render.leftInt() != SkyHandler.getMaximumScale() || render.right() == null) {
            return true;
        }
        float parallaxFactor = (float) (renderDist / Math.max(1, ourPosInPlanetFrame.getNorm()));
        poseStack.translate(-ourPosInPlanetFrame.getX() * parallaxFactor, -ourPosInPlanetFrame.getY() * parallaxFactor, -ourPosInPlanetFrame.getZ() * parallaxFactor);
        poseStack.pushPose();
        poseStack.mulPose(DeepSpaceHelper.adapt(planet.getRotationAtTime(date)).get(new Quaternionf()));
        float size = (float) (planet.radius() * parallaxFactor);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        boolean isStar = !planet.clouds() && planet.frame().getName().equals("sol");
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        if (isStar) {
            SkyHandler.ensureStarPlasmaTexture();
            if (SkyHandler.STAR_PLASMA_TEXTURE_ID != null) {
                RenderSystem.setShaderTexture(0, SkyHandler.STAR_PLASMA_TEXTURE_ID);
            } else {
                render.right().setShaderTexture();
            }
        } else {
            render.right().setShaderTexture();
        }

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);

        Matrix4f matrix = poseStack.last().pose();

        // align top of texture for top/bottom faces with the north face

        // TOP face - CCW from above
        bufferbuilder.addVertex(matrix, -size, size, -size).setUv(0.0f, 0.0f);
        bufferbuilder.addVertex(matrix, -size, size, size).setUv(0.0f, 1.0f);
        bufferbuilder.addVertex(matrix, size, size, size).setUv(1.0f, 1.0f);
        bufferbuilder.addVertex(matrix, size, size, -size).setUv(1.0f, 0.0f);

        // BOTTOM - CCW from below
        bufferbuilder.addVertex(matrix, -size, -size, -size).setUv(0.0f, 0.0f);
        bufferbuilder.addVertex(matrix, size, -size, -size).setUv(1.0f, 0.0f);
        bufferbuilder.addVertex(matrix, size, -size, size).setUv(1.0f, 1.0f);
        bufferbuilder.addVertex(matrix, -size, -size, size).setUv(0.0f, 1.0f);

        // align top of texture for horizontal faces with the top face

        // NORTH (Z = -size) - CCW from North
        bufferbuilder.addVertex(matrix, size, size, -size).setUv(0.0f, 0.0f);
        bufferbuilder.addVertex(matrix, size, -size, -size).setUv(0.0f, 1.0f);
        bufferbuilder.addVertex(matrix, -size, -size, -size).setUv(1.0f, 1.0f);
        bufferbuilder.addVertex(matrix, -size, size, -size).setUv(1.0f, 0.0f);

        // SOUTH (Z = size) - CCW from South
        bufferbuilder.addVertex(matrix, -size, size, size).setUv(0.0f, 0.0f);
        bufferbuilder.addVertex(matrix, -size, -size, size).setUv(0.0f, 1.0f);
        bufferbuilder.addVertex(matrix, size, -size, size).setUv(1.0f, 1.0f);
        bufferbuilder.addVertex(matrix, size, size, size).setUv(1.0f, 0.0f);

        // WEST (X = -size) - CCW from West
        bufferbuilder.addVertex(matrix, -size, size, -size).setUv(0.0f, 0.0f);
        bufferbuilder.addVertex(matrix, -size, -size, -size).setUv(0.0f, 1.0f);
        bufferbuilder.addVertex(matrix, -size, -size, size).setUv(1.0f, 1.0f);
        bufferbuilder.addVertex(matrix, -size, size, size).setUv(1.0f, 0.0f);

        // EAST (X = size) - CCW from East
        bufferbuilder.addVertex(matrix, size, size, size).setUv(0.0f, 0.0f);
        bufferbuilder.addVertex(matrix, size, -size, size).setUv(0.0f, 1.0f);
        bufferbuilder.addVertex(matrix, size, -size, -size).setUv(1.0f, 1.0f);
        bufferbuilder.addVertex(matrix, size, size, -size).setUv(1.0f, 0.0f);

        BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());

        if (isStar) {
            // Render 3 extra rotating, pulsating plasma layers for a highly turbulent, volumetric 3D solar storm!
            // This prevents the "flat cube" look and creates amazing swirling interference patterns.
            long tick = mc.level.getGameTime();
            float baseTime = tick + partialTicks;
            
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            if (SkyHandler.STAR_PLASMA_TEXTURE_ID != null) {
                RenderSystem.setShaderTexture(0, SkyHandler.STAR_PLASMA_TEXTURE_ID);
            }
            
            for (int layer = 1; layer <= 3; layer++) {
                poseStack.pushPose();
                
                // Pulsate slightly differently for each layer
                float pulseFreq = 0.05f + layer * 0.02f;
                float pulseAmp = 0.005f + layer * 0.004f;
                float scale = 1.0f + (float) Math.sin(baseTime * pulseFreq) * pulseAmp;
                float layerSize = size * (1.0f + layer * 0.006f) * scale;
                
                // Rotate layers in different directions and speeds
                float rotX = baseTime * (0.012f * (layer == 2 ? -1.5f : 1.0f));
                float rotY = baseTime * (0.018f * (layer == 1 ? -1.2f : 1.5f));
                float rotZ = baseTime * (0.015f * (layer == 3 ? -1.0f : 1.3f));
                
                poseStack.mulPose(new Quaternionf().rotateXYZ(rotX, rotY, rotZ));
                
                // Draw the textured cube faces with additive opacity
                float alpha = 0.25f - (layer * 0.05f); // inner layer is more opaque, outer layer is softer
                BufferBuilder layerBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                
                // Render textured cube with color using our existing renderCubeFaces helper
                renderCubeFaces(layerBuilder, poseStack.last().pose(), layerSize, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, alpha);
                BufferUploader.drawWithShader(layerBuilder.buildOrThrow());
                
                poseStack.popPose();
            }
        }

        if (planet.clouds()) {
            SkyHandler.ensureCloudTexture();
            if (SkyHandler.CLOUD_TEXTURE_ID != null) {
                RenderSystem.setShaderTexture(0, SkyHandler.CLOUD_TEXTURE_ID);
                long factor = 1000L * 1000; // MUCH slower
                float timeOffset = (System.currentTimeMillis() % (20L * factor)) / (float) factor;
                
                // Cloud Shadows
                double theta = 2.0 * Math.PI * celestialAngle;
                float lx = (float) -Math.sin(theta);
                float shadowShift = lx * size * 0.08f;
                BufferBuilder shadowBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                float sr = 0.01f, sg = 0.02f, sb = 0.08f, sa = 0.48f;
                // Draw slightly larger cube for clouds
                float shadowSize = size * 1.01f;
                matrix.translate(shadowShift, 0, 0); // shift matrix for shadow
                renderCubeFaces(shadowBuilder, matrix, shadowSize, timeOffset, 0.0f, sr, sg, sb, sa);
                BufferUploader.drawWithShader(shadowBuilder.buildOrThrow());
                matrix.translate(-shadowShift, 0, 0); // un-shift matrix

                // Scrolling Clouds
                BufferBuilder cloudBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                float cloudSize = size * 1.02f;
                renderCubeFaces(cloudBuilder, matrix, cloudSize, timeOffset, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f);
                BufferUploader.drawWithShader(cloudBuilder.buildOrThrow());

                // Scrolling Clouds Layer 2 (Upper, faster, different scroll offsets for parallax volumetric effect)
                BufferBuilder cloudBuilder2 = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                float cloudSize2 = size * 1.032f;
                float uOffset2 = -timeOffset * 1.4f;
                float vOffset2 = timeOffset * 0.5f;
                renderCubeFaces(cloudBuilder2, matrix, cloudSize2, uOffset2, vOffset2, 1.0f, 1.0f, 1.0f, 0.55f); // Soft, beautiful overlay
                BufferUploader.drawWithShader(cloudBuilder2.buildOrThrow());
            }

        }

        // Render gorgeous 3D dynamic, subdivided, cell-shaded and dithered shadow overlay for all non-star planets!
        if (!isStar) {
            Vector3D solPosInPlanetFrame = UNIVERSE.getFrameByName("sol").map(solFrame -> {
                try {
                    return solFrame.getStaticTransformTo(planet.orekitFrame(), date).transformPosition(Vector3D.ZERO);
                } catch (Exception e) {
                    return Vector3D.ZERO;
                }
            }).orElse(Vector3D.ZERO);

            Vector3D L = solPosInPlanetFrame.getNormSq() > 1e-6 ? solPosInPlanetFrame.normalize() : new Vector3D(1, 0, 0);
            
            float shadowSize = planet.clouds() ? size * 1.035f : size * 1.002f;
            
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            
            BufferBuilder shadowCubeBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            
            FaceDefinition[] shadowFaces = {
                // TOP
                new FaceDefinition(new Vector3D(0, shadowSize, 0), new Vector3D(shadowSize, 0, 0), new Vector3D(0, 0, shadowSize)),
                // BOTTOM
                new FaceDefinition(new Vector3D(0, -shadowSize, 0), new Vector3D(shadowSize, 0, 0), new Vector3D(0, 0, shadowSize)),
                // NORTH
                new FaceDefinition(new Vector3D(0, 0, -shadowSize), new Vector3D(-shadowSize, 0, 0), new Vector3D(0, -shadowSize, 0)),
                // SOUTH
                new FaceDefinition(new Vector3D(0, 0, shadowSize), new Vector3D(shadowSize, 0, 0), new Vector3D(0, -shadowSize, 0)),
                // WEST
                new FaceDefinition(new Vector3D(-shadowSize, 0, 0), new Vector3D(0, 0, shadowSize), new Vector3D(0, -shadowSize, 0)),
                // EAST
                new FaceDefinition(new Vector3D(shadowSize, 0, 0), new Vector3D(0, 0, -shadowSize), new Vector3D(0, -shadowSize, 0))
            };
            
            int G = 16;
            for (FaceDefinition face : shadowFaces) {
                for (int gv = 0; gv < G; gv++) {
                    for (int gu = 0; gu < G; gu++) {
                        Vector3D p1 = getPoint(face, gu, gv, G);
                        Vector3D p2 = getPoint(face, gu, gv + 1, G);
                        Vector3D p3 = getPoint(face, gu + 1, gv + 1, G);
                        Vector3D p4 = getPoint(face, gu + 1, gv, G);
                        
                        long c1 = computeColor(p1, L, gu, gv);
                        long c2 = computeColor(p2, L, gu, gv + 1);
                        long c3 = computeColor(p3, L, gu + 1, gv + 1);
                        long c4 = computeColor(p4, L, gu + 1, gv);
                        
                        shadowCubeBuilder.addVertex(matrix, (float)p1.getX(), (float)p1.getY(), (float)p1.getZ())
                                         .setColor((int)((c1 >> 24) & 255), (int)((c1 >> 16) & 255), (int)((c1 >> 8) & 255), (int)(c1 & 255));
                        shadowCubeBuilder.addVertex(matrix, (float)p2.getX(), (float)p2.getY(), (float)p2.getZ())
                                         .setColor((int)((c2 >> 24) & 255), (int)((c2 >> 16) & 255), (int)((c2 >> 8) & 255), (int)(c2 & 255));
                        shadowCubeBuilder.addVertex(matrix, (float)p3.getX(), (float)p3.getY(), (float)p3.getZ())
                                         .setColor((int)((c3 >> 24) & 255), (int)((c3 >> 16) & 255), (int)((c3 >> 8) & 255), (int)(c3 & 255));
                        shadowCubeBuilder.addVertex(matrix, (float)p4.getX(), (float)p4.getY(), (float)p4.getZ())
                                         .setColor((int)((c4 >> 24) & 255), (int)((c4 >> 16) & 255), (int)((c4 >> 8) & 255), (int)(c4 & 255));
                    }
                }
            }
            
            BufferUploader.drawWithShader(shadowCubeBuilder.buildOrThrow());
        }

        if (isStar || planet.clouds()) {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.disableCull();

            int layers = isStar ? 35 : 20; 
            
            float ar = 1.0f, ag = 1.0f, ab = 1.0f;
            if (!isStar) {
                double tAngle = celestialAngle * 2.0 * Math.PI;
                float sunIntensity = (float) Math.cos(tAngle);
                float sideIntensity = (float) Math.abs(Math.sin(tAngle));
                if (sunIntensity > 0) {
                    float t = sideIntensity;
                    ar = net.minecraft.util.Mth.lerp(t, 0.40f, 1.00f);
                    ag = net.minecraft.util.Mth.lerp(t, 0.70f, 0.42f);
                    ab = net.minecraft.util.Mth.lerp(t, 1.00f, 0.15f);
                } else {
                    float t = -sunIntensity;
                    ar = net.minecraft.util.Mth.lerp(t, 1.00f, 0.18f);
                    ag = net.minecraft.util.Mth.lerp(t, 0.42f, 0.08f);
                    ab = net.minecraft.util.Mth.lerp(t, 0.15f, 0.45f);
                }

                // Spectacular fiery orange sunset/sunrise halo effect at the terminator line!
                float terminatorFactor = Math.max(0.0f, 1.0f - (Math.abs(sunIntensity) / 0.45f));
                terminatorFactor = terminatorFactor * terminatorFactor * (3.0f - 2.0f * terminatorFactor);
                ar = net.minecraft.util.Mth.lerp(terminatorFactor, ar, 1.00f);
                ag = net.minecraft.util.Mth.lerp(terminatorFactor, ag, 0.48f);
                ab = net.minecraft.util.Mth.lerp(terminatorFactor, ab, 0.12f);
            }

            for (int i = 0; i < layers; i++) {
                float progress = i / (float) (layers - 1);
                float s;
                float aa;
                float lr = ar, lg = ag, lb = ab;

                if (isStar) {
                    // Solar corona: much wider glow, extremely beautiful color gradient:
                    // From white-hot yellow (inner) to golden-orange to fiery-red to violet/magenta (outer "veil" flare!)
                    s = size * (1.005f + (float)Math.pow(progress, 1.5f) * 0.7f); // wider glow
                    
                    // Opacity curve: fade out smoothly
                    aa = (0.28f * (float)Math.pow(1.0f - progress, 2.5f));
                    
                    // Color gradient from progress 0 (inner) to 1 (outer)
                    if (progress < 0.2f) {
                        // White-hot yellow-gold
                        float t = progress / 0.2f;
                        lr = 1.0f;
                        lg = net.minecraft.util.Mth.lerp(t, 0.95f, 0.8f);
                        lb = net.minecraft.util.Mth.lerp(t, 0.8f, 0.1f);
                    } else if (progress < 0.5f) {
                        // Golden orange to fiery red
                        float t = (progress - 0.2f) / 0.3f;
                        lr = 1.0f;
                        lg = net.minecraft.util.Mth.lerp(t, 0.8f, 0.2f);
                        lb = net.minecraft.util.Mth.lerp(t, 0.1f, 0.0f);
                    } else if (progress < 0.8f) {
                        // Fiery red to deep violet/magenta (magical cosmic veil!)
                        float t = (progress - 0.5f) / 0.3f;
                        lr = net.minecraft.util.Mth.lerp(t, 1.0f, 0.7f);
                        lg = net.minecraft.util.Mth.lerp(t, 0.2f, 0.0f);
                        lb = net.minecraft.util.Mth.lerp(t, 0.0f, 0.6f);
                    } else {
                        // Outer corona fadeout to cosmic space violet
                        float t = (progress - 0.8f) / 0.2f;
                        lr = net.minecraft.util.Mth.lerp(t, 0.7f, 0.1f);
                        lg = 0.0f;
                        lb = net.minecraft.util.Mth.lerp(t, 0.6f, 0.2f);
                    }
                } else {
                    s = size * (1.01f + (float)Math.pow(progress, 1.2f) * 0.4f);
                    aa = (0.05f * (float)Math.pow(1.0f - progress, 2.0f)) * 1.0f;
                }

                BufferBuilder atmBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                renderCubeFaces(atmBuilder, matrix, s, 0, 0, lr, lg, lb, aa);
                BufferUploader.drawWithShader(atmBuilder.buildOrThrow());
            }
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        poseStack.popPose();
        return false;
    }

    public static Pair<Frame, List<CubePlanet>> renderHologram(Vector3D posInFrame, Frame posFrame, UniverseDefinition universe, AbsoluteDate date, double scale, double scaleTest, PoseStack poseStack, MultiBufferSource source) {
        Frame retFrame = posFrame;
        List<CubePlanet> renderedPlanets = new ArrayList<>();
        IntList needRenderData = new IntArrayList();
        Iterator<Pair<Vector3D, CubePlanet>> iter = universe.getPlanets().stream()
                .map(planet -> Pair.of(planet.posInMyFrame(date, posInFrame, posFrame), planet))
                .filter(pair -> pair.left().getNormSq() < scaleTest * scaleTest)
                .filter(pair -> pair.right().radius() < scaleTest).iterator();
        while (iter.hasNext()) {
            Pair<Vector3D, CubePlanet> planet = iter.next();
            if (planet.right().orekitFrame().getDepth() < retFrame.getDepth()) {
                retFrame = planet.right().orekitFrame();
            }
            poseStack.pushPose();
            if (renderHoloPlanet(planet.right(), planet.left(), poseStack, date, scale, source, 0.9f, 0.9f, 1.0f, 0.9f)) {
                needRenderData.add(planet.right().id());
            } else {
                renderedPlanets.add(planet.right());
            }
            poseStack.popPose();
        }
        if (!needRenderData.isEmpty()) PacketDistributor.sendToServer(new PlanetRenderRequestPayload(needRenderData.toIntArray(), SkyHandler.getMaximumScale()));
        return Pair.of(retFrame, renderedPlanets);
    }

    public static boolean renderHoloPlanet(CubePlanet planet, Vector3D ourPosInPlanetFrame, PoseStack poseStack, AbsoluteDate date, double holoScale, MultiBufferSource source, float r, float g, float b, float a) {
        IntObjectPair<DeepSpaceTexture> render = KNOWN_RENDER_DATA.get(planet.id());
        if (render == null || render.leftInt() != SkyHandler.getMaximumScale() || render.right() == null) {
            return true;
        }
        float scaleFactor = (float) (1 / holoScale);
        poseStack.translate(-ourPosInPlanetFrame.getX() * scaleFactor, -ourPosInPlanetFrame.getY() * scaleFactor, -ourPosInPlanetFrame.getZ() * scaleFactor);
        poseStack.pushPose();
        poseStack.mulPose(DeepSpaceHelper.adapt(planet.getRotationAtTime(date)).get(new Quaternionf()));
        float size = (float) (planet.radius() * scaleFactor);

        VertexConsumer bufferbuilder = source.getBuffer(render.right().attachType(HOLOGRAM_TYPE));

        Matrix4f matrix = poseStack.last().pose();

        // Draw base planet map
        renderCubeFaces(bufferbuilder, matrix, size, 0.0f, 0.0f, r, g, b, a);

        // Hologram Clouds
        SkyHandler.ensureCloudTexture();
        if (SkyHandler.CLOUD_TEXTURE_ID != null) {
            VertexConsumer cloudBuilder = source.getBuffer(HOLOGRAM_TYPE.apply(SkyHandler.CLOUD_TEXTURE_ID));
            long factor = 1000L * 10;
            float timeOffset = (System.currentTimeMillis() % (20L * factor)) / (float) factor;
            float cloudSize = size * 1.02f;
            renderCubeFaces(cloudBuilder, matrix, cloudSize, timeOffset, 0.0f, r, g, b, a);
        }

        // since we're constantly updating the linked texture, we need to draw it right now.
        if (source instanceof MultiBufferSource.BufferSource buf) {
            buf.endBatch();
        }

        poseStack.popPose();
        return false;
    }

    private static final Function<ResourceLocation, RenderType> HOLOGRAM_TYPE = Util.memoize(DeepSpaceHandler::getHologramType);

    private static RenderType getHologramType(ResourceLocation tex) {
        RenderType.CompositeState rendertype$state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                .createCompositeState(false);
        return RenderType.create("rocketnautics_hologram", DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS, 256, true, true, rendertype$state);
    }


    private static void renderCubeFaces(VertexConsumer bufferbuilder, Matrix4f matrix, float size, float uOffset, float vOffset, float r, float g, float b, float a) {
        // TOP face
        bufferbuilder.addVertex(matrix, -size, size, -size).setColor(r, g, b, a).setUv(0.0f + uOffset, 0.0f + vOffset);
        bufferbuilder.addVertex(matrix, -size, size, size).setColor(r, g, b, a).setUv(0.0f + uOffset, 1.0f + vOffset);
        bufferbuilder.addVertex(matrix, size, size, size).setColor(r, g, b, a).setUv(1.0f + uOffset, 1.0f + vOffset);
        bufferbuilder.addVertex(matrix, size, size, -size).setColor(r, g, b, a).setUv(1.0f + uOffset, 0.0f + vOffset);

        // BOTTOM
        bufferbuilder.addVertex(matrix, -size, -size, -size).setColor(r, g, b, a).setUv(0.0f + uOffset, 0.0f + vOffset);
        bufferbuilder.addVertex(matrix, size, -size, -size).setColor(r, g, b, a).setUv(1.0f + uOffset, 0.0f + vOffset);
        bufferbuilder.addVertex(matrix, size, -size, size).setColor(r, g, b, a).setUv(1.0f + uOffset, 1.0f + vOffset);
        bufferbuilder.addVertex(matrix, -size, -size, size).setColor(r, g, b, a).setUv(0.0f + uOffset, 1.0f + vOffset);

        // NORTH
        bufferbuilder.addVertex(matrix, size, size, -size).setColor(r, g, b, a).setUv(0.0f + uOffset, 0.0f + vOffset);
        bufferbuilder.addVertex(matrix, size, -size, -size).setColor(r, g, b, a).setUv(0.0f + uOffset, 1.0f + vOffset);
        bufferbuilder.addVertex(matrix, -size, -size, -size).setColor(r, g, b, a).setUv(1.0f + uOffset, 1.0f + vOffset);
        bufferbuilder.addVertex(matrix, -size, size, -size).setColor(r, g, b, a).setUv(1.0f + uOffset, 0.0f + vOffset);

        // SOUTH
        bufferbuilder.addVertex(matrix, -size, size, size).setColor(r, g, b, a).setUv(0.0f + uOffset, 0.0f + vOffset);
        bufferbuilder.addVertex(matrix, -size, -size, size).setColor(r, g, b, a).setUv(0.0f + uOffset, 1.0f + vOffset);
        bufferbuilder.addVertex(matrix, size, -size, size).setColor(r, g, b, a).setUv(1.0f + uOffset, 1.0f + vOffset);
        bufferbuilder.addVertex(matrix, size, size, size).setColor(r, g, b, a).setUv(1.0f + uOffset, 0.0f + vOffset);

        // WEST
        bufferbuilder.addVertex(matrix, -size, size, -size).setColor(r, g, b, a).setUv(0.0f + uOffset, 0.0f + vOffset);
        bufferbuilder.addVertex(matrix, -size, -size, -size).setColor(r, g, b, a).setUv(0.0f + uOffset, 1.0f + vOffset);
        bufferbuilder.addVertex(matrix, -size, -size, size).setColor(r, g, b, a).setUv(1.0f + uOffset, 1.0f + vOffset);
        bufferbuilder.addVertex(matrix, -size, size, size).setColor(r, g, b, a).setUv(1.0f + uOffset, 0.0f + vOffset);

        // EAST
        bufferbuilder.addVertex(matrix, size, size, size).setColor(r, g, b, a).setUv(0.0f + uOffset, 0.0f + vOffset);
        bufferbuilder.addVertex(matrix, size, -size, size).setColor(r, g, b, a).setUv(0.0f + uOffset, 1.0f + vOffset);
        bufferbuilder.addVertex(matrix, size, -size, -size).setColor(r, g, b, a).setUv(1.0f + uOffset, 1.0f + vOffset);
        bufferbuilder.addVertex(matrix, size, size, -size).setColor(r, g, b, a).setUv(1.0f + uOffset, 0.0f + vOffset);
    }

    private static class FaceDefinition {
        final Vector3D center;
        final Vector3D U;
        final Vector3D V;
        
        FaceDefinition(Vector3D center, Vector3D U, Vector3D V) {
            this.center = center;
            this.U = U;
            this.V = V;
        }
    }

    private static Vector3D getPoint(FaceDefinition face, int gu, int gv, int G) {
        double u = -1.0 + 2.0 * gu / G;
        double v = -1.0 + 2.0 * gv / G;
        return face.center.add(face.U.scalarMultiply(u)).add(face.V.scalarMultiply(v));
    }

    private static long computeColor(Vector3D P, Vector3D L, int gx, int gy) {
        double d = P.normalize().dotProduct(L);
        
        int r = 0, g = 0, b = 0, a = 0;
        
        if (d > 0.65) {
            // Brilliant star-facing sunlight highlight
            r = 255;
            g = 245;
            b = 200;
            a = 45;
        } else if (d > 0.45) {
            // Dithered bright border to direct light
            if ((gx + gy) % 2 == 0) {
                r = 255;
                g = 245;
                b = 200;
                a = 25;
            } else {
                a = 0;
            }
        } else if (d > 0.05) {
            // Direct illumination
            a = 0;
        } else if (d > -0.12) {
            // Soft dithered terminator line
            if ((gx + gy) % 2 == 0) {
                r = 6;
                g = 8;
                b = 25;
                a = 120;
            } else {
                a = 0;
            }
        } else {
            // Deep dark indigo space shadow
            r = 4;
            g = 5;
            b = 18;
            a = 220;
        }
        
        return ((long)r << 24) | ((long)g << 16) | ((long)b << 8) | a;
    }
}
