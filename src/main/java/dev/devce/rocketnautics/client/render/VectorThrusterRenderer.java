package dev.devce.rocketnautics.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.devce.rocketnautics.content.blocks.RocketThrusterBlock;
import dev.devce.rocketnautics.content.blocks.VectorThrusterBlockEntity;
import dev.devce.rocketnautics.registry.RocketPartials;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class VectorThrusterRenderer extends SafeBlockEntityRenderer<VectorThrusterBlockEntity> {

    public VectorThrusterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(VectorThrusterBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        BlockState blockState = be.getBlockState();
        Direction facing = blockState.getValue(RocketThrusterBlock.FACING);

        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);


        float gX = Mth.lerp(partialTicks, be.getPrevGimbalX(), be.getGimbalX());
        float gY = Mth.lerp(partialTicks, be.getPrevGimbalY(), be.getGimbalY());
        float gZ = Mth.lerp(partialTicks, be.getPrevGimbalZ(), be.getGimbalZ());

        // Calculate the world-space target direction vector
        Vector3f baseDir = new Vector3f(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        Vector3f targetDir = new Vector3f(
            facing.getStepX() + gX,
            facing.getStepY() + gY,
            facing.getStepZ() + gZ
        ).normalize();

        // Create a rotation that tilts the neutral 'facing' direction towards our 'targetDir'
        Quaternionf tilt = new Quaternionf().rotationTo(baseDir, targetDir);

        // Apply tilt on top of the base facing rotation
        Quaternionf q = new Quaternionf(tilt).mul(facing.getRotation());

        ms.mulPose(q);

        ms.translate(-0.5, -0.5, -0.5);

        var bakedModel = RocketPartials.vectorThrusterNozzle;
        if (bakedModel != null) {
            Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(
                    ms.last(), buffer.getBuffer(RenderType.cutout()), blockState, bakedModel,
                    1.0f, 1.0f, 1.0f, light, overlay);
        }

        ms.popPose();

    }
}
