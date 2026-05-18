package dev.devce.rocketnautics.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.devce.rocketnautics.RocketConfig;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.client.DeepSpaceHandler;
import dev.devce.rocketnautics.content.blocks.HologramTableBlockEntity;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.content.orbit.universe.DeepSpacePosition;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FastColor;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.NonNull;
import org.orekit.frames.Frame;
import org.orekit.orbits.Orbit;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

@OnlyIn(Dist.CLIENT)
public class HologramTableRenderer extends SafeBlockEntityRenderer<HologramTableBlockEntity> {

    public HologramTableRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(HologramTableBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource bufferSource, int light, int overlay) {
        UniverseDefinition universe = DeepSpaceHandler.getUniverse();
        if (!DeepSpaceHandler.hasReceivedPosition() || universe == null || be.getLevel() == null) return;
        final int holoSize = be.getHoloSize();
        final double holoScale = be.getHoloScale();
        DeepSpacePosition position = null;
        AbsoluteDate renderDate;
        long renderTicks = Minecraft.getInstance().levelRenderer.getTicks();
        if (DeepSpaceData.isDeepSpace(be.getLevel())) {
            position = DeepSpaceHandler.getReceivedPosition();
            renderDate = DeepSpaceHandler.getRenderDate(partialTicks);
        } else {
            renderDate = DeepSpaceData.getTime(renderTicks).shiftedBy(partialTicks / 20);
        }
        Frame centerFrame;
        if (position != null) {
            centerFrame = position.getFrame();
        } else {
            CubePlanet inhabiting = universe.getPlanetByDimension(be.getLevel().dimension());
            if (inhabiting == null) return;
            centerFrame = inhabiting.orekitFrame();
        }
        Vector3D posInFrame = position != null ? position.getPosition(renderDate) : Vector3D.ZERO;
        ms.pushPose();
        ms.translate(0.5, holoSize / 2d + 1, 0.5);
        ms.pushPose();
        // remove our sub level's rotation
        ClientSubLevel subLevel = Sable.HELPER.getContainingClient(be);
        Minecraft minecraft = Minecraft.getInstance();
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (subLevel != null) {
            ms.mulPose(subLevel.renderPose().orientation().get(new Matrix4f()).invert());
        }
        // finish hologram rendering
        Pair<Frame, List<CubePlanet>> pair = DeepSpaceHandler.renderHologram(posInFrame, centerFrame, universe, renderDate, holoScale / holoSize, holoScale / 2, ms, bufferSource);
        Frame largestFrame = pair.left();
        if (position != null) {
            int steps = RocketConfig.CLIENT.orbitPredictionSteps.getAsInt();
            double fov = minecraft.gameRenderer.getFov(camera, partialTicks, true);
            float s = (float) (0.01 * Math.sqrt(holoSize) * Math.tan(Math.toRadians(fov) / 2));
            DebugRenderer.renderFilledBox(ms, bufferSource, -s, -s, -s, s, s, s, 0.0f, 1.0f, 0.8f, 0.8f);
            renderVelocityVector(position.getCurrentOrbit().getPVCoordinates(renderDate, largestFrame).getVelocity(), bufferSource, ms, camera);
//            ms.pushPose();
//            ms.translate(camera.getPosition().x, camera.getPosition().y, camera.getPosition().z);
//            DebugRenderer.renderFloatingText(ms, bufferSource, String.format("Speed: %.2e", velocity.getNorm()), 0, -sPos * 5, 0, FastColor.ARGB32.color(255, 255, 255, 255));
//            DebugRenderer.renderFloatingText(ms, bufferSource, String.format("Velocity: %.2e, %.2e, %.2e", velocity.getX(), velocity.getY(), velocity.getZ()), 0, -sPos * 10, 0, FastColor.ARGB32.color(255, 255, 255, 255));
//            ms.popPose();
            if (steps > 0) {
                // TODO make the speed and size of the cycle configurable
                float cycle = (((renderTicks + partialTicks) * getCycleSpeed()) % getCycleLength());
                float cycle2 = (((renderTicks + partialTicks) * getCycleSpeed()) % (getCycleLength() * 5));
                ms.pushPose();
                double scaleFactor = holoSize / holoScale;
                Vector3D scaledPos = centerFrame.getStaticTransformTo(largestFrame, renderDate).transformPosition(posInFrame).scalarMultiply(scaleFactor);
                ms.translate(-scaledPos.getX(), -scaledPos.getY(), -scaledPos.getZ());
                // render our orbit prediction
                Iterator<Vector3D> iter = DeepSpaceHandler.getPositionPrediction(largestFrame, steps);
                Predicate<Vector3D> distancePred = v -> 4 * v.distanceSq(scaledPos) > holoSize * holoSize;
                int count = renderChainedPositions(iter, scaleFactor, bufferSource, ms, distancePred, cycle, cycle2, s, 0.3f);
                // render planetary orbit predictions
                for (CubePlanet planet : pair.right()) {
                    if (planet.orekitFrame() == largestFrame) continue;
                    ms.pushPose();
                    PVCoordinates c = planet.getPVCoordinates(renderDate, largestFrame);
                    ms.translate(c.getPosition().getX() * scaleFactor, c.getPosition().getY() * scaleFactor, c.getPosition().getZ() * scaleFactor);
                    renderVelocityVector(c.getVelocity(), bufferSource, ms, camera);
                    ms.popPose();
                    iter = DeepSpaceHandler.getPredictionDates(count)
                            .map(d -> planet.getPosition(d, largestFrame))
                            .iterator();
                    renderChainedPositions(iter, scaleFactor, bufferSource, ms, distancePred, cycle, cycle2, s, 1.0f);
                }
                renderIntersects(bufferSource, ms, largestFrame, pair.right(), scaleFactor, s);
                ms.popPose();
            }
        }
        ms.popPose();
        ms.popPose();
    }

    private void renderVelocityVector(Vector3D velocity, MultiBufferSource bufferSource, PoseStack ms, Camera camera) {
        VertexConsumer bufVel = bufferSource.getBuffer(RenderType.lineStrip());
        Vector3D normed = velocity.normalize();
        Vector3D pointer = new Vector3D(0.8, normed, 0.2, normed.crossProduct(DeepSpaceHelper.adaptf(camera.getLookVector())).normalize());
        Vector3D normal = pointer.subtract(normed).normalize();
        bufVel.addVertex(ms.last(), 0f, 0f, 0f)
                .setColor(1.0f, 0f, 1.0f, 0.8f)
                .setNormal(ms.last(), (float) normed.getX(), (float) normed.getY(), (float) normed.getZ());
        bufVel.addVertex(ms.last(), (float) normed.getX(), (float) normed.getY(), (float) normed.getZ())
                .setColor(1.0f, 0f, 1.0f, 0.8f)
                .setNormal(ms.last(), (float) normal.getX(), (float) normal.getY(), (float) normal.getZ());
        bufVel.addVertex(ms.last(), (float) pointer.getX(), (float) pointer.getY(), (float) pointer.getZ())
                .setColor(1.0f, 0f, 1.0f, 0.8f)
                .setNormal(ms.last(), (float) normal.getX(), (float) normal.getY(), (float) normal.getZ());
    }

    private int renderChainedPositions(Iterator<Vector3D> iter, double scaleFactor, MultiBufferSource bufferSource, PoseStack ms, Predicate<Vector3D> stopCondition, float cycle, float cycle2, double s, float b) {
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lineStrip());
        List<Vector3D> cycling = new ArrayList<>();
        List<Vector3D> cycling2 = new ArrayList<>();
        int count = 0;
        if (iter.hasNext()) {
            Vector3D v = iter.next().scalarMultiply(scaleFactor);
            while (iter.hasNext()) {
                Vector3D vNext = iter.next().scalarMultiply(scaleFactor);
                if (stopCondition.test(v)) {
                    break;
                }
                if (doCycle() && count == Math.ceil(cycle)) {
                    float partialCycle = cycle % 1;
                    cycling.add(new Vector3D(partialCycle, vNext, 1 - partialCycle, v));
                }
                if (doCycle() && cycle != cycle2 && count == Math.ceil(cycle2)) {
                    float partialCycle = cycle2 % 1;
                    cycling2.add(new Vector3D(partialCycle, vNext, 1 - partialCycle, v));
                }
                count++;
                Vector3D norm = vNext.subtract(v).normalize();
                buffer.addVertex(ms.last(), (float) v.getX(), (float) v.getY(), (float) v.getZ())
                        .setColor(0.8f, 0.8f, b, 0.8f)
                        .setNormal(ms.last(), (float) norm.getX(), (float) norm.getY(), (float) norm.getZ());
                v = vNext;
            }
        }
        if (doCycle()) {
            for (Vector3D c : cycling) {
                ms.pushPose();
                ms.translate(c.getX(), c.getY(), c.getZ());
                DebugRenderer.renderFilledBox(ms, bufferSource, -s, -s, -s, s, s, s, 1.0f, 0f, 0.2f, 1.0f);
                ms.popPose();
            }
            for (Vector3D c : cycling2) {
                ms.pushPose();
                ms.translate(c.getX(), c.getY(), c.getZ());
                DebugRenderer.renderFilledBox(ms, bufferSource, -s, -s, -s, s, s, s, 0.7f, 0f, 0.1f, 1.0f);
                ms.popPose();
            }
        }
        return count;
    }

    private void renderIntersects(MultiBufferSource bufferSource, PoseStack ms, Frame referenceFrame, List<CubePlanet> planets, double scaleFactor, float s) {
        Iterator<Orbit> iter = DeepSpaceHandler.getPredictionOrbits();
        Orbit prevOrbit = iter.next();
        while (iter.hasNext()) {
            Orbit orbit = iter.next();
            ms.pushPose();
            Vector3D v = orbit.getPosition(referenceFrame).scalarMultiply(scaleFactor);
            ms.translate(v.getX(), v.getY(), v.getZ());
            DebugRenderer.renderFilledBox(ms, bufferSource, -s, -s, -s, s, s, s, 0f, 0.0f, 1.0f, 1.0f);
            ms.popPose();
            List<Vector3D> vPs = new ArrayList<>();
            for (CubePlanet planet : planets) {
                if (planet.orekitFrame() != prevOrbit.getFrame() && planet.orekitFrame() != orbit.getFrame()) continue;
                ms.pushPose();
                Vector3D vP = planet.getPosition(orbit.getDate(), referenceFrame).scalarMultiply(scaleFactor);
                ms.translate(vP.getX(), vP.getY(), vP.getZ());
                DebugRenderer.renderFilledBox(ms, bufferSource, -s, -s, -s, s, s, s, 0f, 0.0f, 1.0f, 1.0f);
                ms.popPose();
                vPs.add(vP);
            }
            if (!vPs.isEmpty()) {
                VertexConsumer buffer = bufferSource.getBuffer(RenderType.lines());
                for (Vector3D vP : vPs) {
                    Vector3D norm = v.subtract(vP).normalize();
                    buffer.addVertex(ms.last(), (float) vP.getX(), (float) vP.getY(), (float) vP.getZ())
                            .setColor(0f, 0.0f, 1.0f, 1.0f)
                            .setNormal(ms.last(), (float) norm.getX(), (float) norm.getY(), (float) norm.getZ());
                    buffer.addVertex(ms.last(), (float) v.getX(), (float) v.getY(), (float) v.getZ())
                            .setColor(0f, 0.0f, 1.0f, 1.0f)
                            .setNormal(ms.last(), (float) norm.getX(), (float) norm.getY(), (float) norm.getZ());
                }
            }
            prevOrbit = orbit;
        }
    }

    private boolean doCycle() {
        return true;
    }

    private float getCycleSpeed() {
        return 0.25f;
    }

    private int getCycleLength() {
        return 20;
    }

    @Override
    public boolean shouldRenderOffScreen(HologramTableBlockEntity p_112306_) {
        return true;
    }

    @Override
    public @NotNull AABB getRenderBoundingBox(@NonNull HologramTableBlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        int size = blockEntity.getHoloSize();
        int halfSize = size / 2;
        return new AABB(pos.getX() - halfSize, pos.getY(), pos.getZ() - halfSize, pos.getX() + halfSize + 1, pos.getY() + size + 1, pos.getZ() + halfSize + 1);
    }
}
