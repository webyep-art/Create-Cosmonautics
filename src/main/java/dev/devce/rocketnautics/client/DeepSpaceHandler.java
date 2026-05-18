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
        poseStack.pushPose();

        Matrix4f matrix = poseStack.last().pose();
        matrix.identity();

        Quaternionf invRot = new Quaternionf(camera.rotation());
        invRot.conjugate();
        poseStack.mulPose(invRot);

        renderStars(event.getLevelRenderer(), poseStack, event.getProjectionMatrix());

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
            if (renderPlanet(planet.right(), planet.left(), poseStack, currentDate, renderDist)) {
                needRenderData.add(planet.right().id());
            }
            poseStack.popPose();
        }
        if (!needRenderData.isEmpty()) PacketDistributor.sendToServer(new PlanetRenderRequestPayload(needRenderData.toIntArray(), SkyHandler.getMaximumScale()));
        poseStack.popPose();
    }

    private static boolean renderPlanet(CubePlanet planet, Vector3D ourPosInPlanetFrame, PoseStack poseStack, AbsoluteDate date, float renderDist) {
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
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        render.right().setShaderTexture();

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

        // TODO clouds

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableCull();

        int layers = 20; // TODO make the number of layers a config option
        float ar = 0.2f, ag = 0.5f, ab = 1.0f;

        for (int i = 0; i < layers; i++) {
            float progress = i / (float) (layers - 1);
            // Progressive size from 1.01 to 1.4 (tighter range)
            float s = size * (1.01f + (float)Math.pow(progress, 1.2f) * 0.4f);
            // Adjusted alpha falloff for tighter layers
            float aa = (0.05f * (float)Math.pow(1.0f - progress, 2.0f)) * (float) 1;

            BufferBuilder atmBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

            // Render all 6 faces using matrix
            atmBuilder.addVertex(matrix, -s, s, -s).setColor(ar, ag, ab, aa);
            atmBuilder.addVertex(matrix, -s, s, s).setColor(ar, ag, ab, aa);
            atmBuilder.addVertex(matrix, s, s, s).setColor(ar, ag, ab, aa);
            atmBuilder.addVertex(matrix, s, s, -s).setColor(ar, ag, ab, aa);
            atmBuilder.addVertex(matrix, -s, -s, -s).setColor(ar, ag, ab, aa);
            atmBuilder.addVertex(matrix, s, -s, -s).setColor(ar, ag, ab, aa);
            atmBuilder.addVertex(matrix, s, -s, s).setColor(ar, ag, ab, aa);
            atmBuilder.addVertex(matrix, -s, -s, s).setColor(ar, ag, ab, aa);
            atmBuilder.addVertex(matrix, s, s, -s).setColor(ar, ag, ab, aa);
            atmBuilder.addVertex(matrix, -s, s, -s).setColor(ar, ag, ab, aa);
            atmBuilder.addVertex(matrix, -s, -s, -s).setColor(ar, ag, ab, aa);
            atmBuilder.addVertex(matrix, s, -s, -s).setColor(ar, ag, ab, aa);
            atmBuilder.addVertex(matrix, -s, s, s).setColor(ar, ag, ab, aa);
            atmBuilder.addVertex(matrix, s, s, s).setColor(ar, ag, ab, aa);
            atmBuilder.addVertex(matrix, s, -s, s).setColor(ar, ag, ab, aa);
            atmBuilder.addVertex(matrix, -s, -s, s).setColor(ar, ag, ab, aa);
            atmBuilder.addVertex(matrix, -s, s, s).setColor(ar, ag, ab, aa);
            atmBuilder.addVertex(matrix, -s, s, -s).setColor(ar, ag, ab, aa);
            atmBuilder.addVertex(matrix, -s, -s, -s).setColor(ar, ag, ab, aa);
            atmBuilder.addVertex(matrix, -s, -s, s).setColor(ar, ag, ab, aa);
            atmBuilder.addVertex(matrix, s, s, -s).setColor(ar, ag, ab, aa);
            atmBuilder.addVertex(matrix, s, s, s).setColor(ar, ag, ab, aa);
            atmBuilder.addVertex(matrix, s, -s, s).setColor(ar, ag, ab, aa);
            atmBuilder.addVertex(matrix, s, -s, -s).setColor(ar, ag, ab, aa);

            BufferUploader.drawWithShader(atmBuilder.buildOrThrow());
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
                .map(planet -> Pair.of(planet.posInMyFrame(date, posInFrame, receivedPosition.getFrame()), planet))
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

        // u,v of 0,0 corresponds to negX,negZ (u is x, v is z)

        // align top of texture for top/bottom faces with the north face

        // TOP face - CCW from above
        bufferbuilder.addVertex(matrix, -size, size, -size).setColor(r, g, b, a).setUv(0.0f, 0.0f);
        bufferbuilder.addVertex(matrix, -size, size, size).setColor(r, g, b, a).setUv(0.0f, 1.0f);
        bufferbuilder.addVertex(matrix, size, size, size).setColor(r, g, b, a).setUv(1.0f, 1.0f);
        bufferbuilder.addVertex(matrix, size, size, -size).setColor(r, g, b, a).setUv(1.0f, 0.0f);

        // BOTTOM - CCW from below
        bufferbuilder.addVertex(matrix, -size, -size, -size).setColor(r, g, b, a).setUv(0.0f, 0.0f);
        bufferbuilder.addVertex(matrix, size, -size, -size).setColor(r, g, b, a).setUv(1.0f, 0.0f);
        bufferbuilder.addVertex(matrix, size, -size, size).setColor(r, g, b, a).setUv(1.0f, 1.0f);
        bufferbuilder.addVertex(matrix, -size, -size, size).setColor(r, g, b, a).setUv(0.0f, 1.0f);

        // align top of texture for horizontal faces with the top face

        // NORTH (Z = -size) - CCW from North
        bufferbuilder.addVertex(matrix, size, size, -size).setColor(r, g, b, a).setUv(0.0f, 0.0f);
        bufferbuilder.addVertex(matrix, size, -size, -size).setColor(r, g, b, a).setUv(0.0f, 1.0f);
        bufferbuilder.addVertex(matrix, -size, -size, -size).setColor(r, g, b, a).setUv(1.0f, 1.0f);
        bufferbuilder.addVertex(matrix, -size, size, -size).setColor(r, g, b, a).setUv(1.0f, 0.0f);

        // SOUTH (Z = size) - CCW from South
        bufferbuilder.addVertex(matrix, -size, size, size).setColor(r, g, b, a).setUv(0.0f, 0.0f);
        bufferbuilder.addVertex(matrix, -size, -size, size).setColor(r, g, b, a).setUv(0.0f, 1.0f);
        bufferbuilder.addVertex(matrix, size, -size, size).setColor(r, g, b, a).setUv(1.0f, 1.0f);
        bufferbuilder.addVertex(matrix, size, size, size).setColor(r, g, b, a).setUv(1.0f, 0.0f);

        // WEST (X = -size) - CCW from West
        bufferbuilder.addVertex(matrix, -size, size, -size).setColor(r, g, b, a).setUv(0.0f, 0.0f);
        bufferbuilder.addVertex(matrix, -size, -size, -size).setColor(r, g, b, a).setUv(0.0f, 1.0f);
        bufferbuilder.addVertex(matrix, -size, -size, size).setColor(r, g, b, a).setUv(1.0f, 1.0f);
        bufferbuilder.addVertex(matrix, -size, size, size).setColor(r, g, b, a).setUv(1.0f, 0.0f);

        // EAST (X = size) - CCW from East
        bufferbuilder.addVertex(matrix, size, size, size).setColor(r, g, b, a).setUv(0.0f, 0.0f);
        bufferbuilder.addVertex(matrix, size, -size, size).setColor(r, g, b, a).setUv(0.0f, 1.0f);
        bufferbuilder.addVertex(matrix, size, -size, -size).setColor(r, g, b, a).setUv(1.0f, 1.0f);
        bufferbuilder.addVertex(matrix, size, size, -size).setColor(r, g, b, a).setUv(1.0f, 0.0f);

        // TODO clouds

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

    private static void renderStars(LevelRenderer renderer, PoseStack poseStack, Matrix4f projectionMatrix) {
        VertexBuffer starBuffer = ((StarBufferExposer) renderer).rocketnautics$starBuffer();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        FogRenderer.setupNoFog();
        starBuffer.bind();
        starBuffer.drawWithShader(poseStack.last().pose(), projectionMatrix, GameRenderer.getPositionShader());
        VertexBuffer.unbind();
    }
}
