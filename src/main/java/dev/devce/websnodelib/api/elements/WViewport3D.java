package dev.devce.websnodelib.api.elements;

import com.mojang.blaze3d.platform.Lighting;
import dev.devce.websnodelib.api.WElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class WViewport3D extends WElement {
    private final List<ModelEntry> models = new ArrayList<>();
    private float zoom = 1.0f;
    private Vector3f globalRot = new Vector3f(0, 0, 0);

    public WViewport3D(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void addModel(ItemStack stack, Vector3f pos, Vector3f rot, float scale) {
        models.add(new ModelEntry(stack, null, pos, rot, scale));
    }

    public void addObjModel(WObjModel model, Vector3f pos, Vector3f rot, float scale) {
        models.add(new ModelEntry(null, model, pos, rot, scale));
    }

    public void clear() {
        models.clear();
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, int mouseX, int mouseY, float partialTick) {
        // Background/Frame
        graphics.fill(x, y, x + width, y + height, 0xAA000000);
        graphics.renderOutline(x, y, width, height, 0xFF666666);

        // Scissor to prevent bleeding out of the viewport
        double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
        int sx = (int) (graphics.pose().last().pose().get(3, 0) * guiScale);
        int sy = (int) (graphics.pose().last().pose().get(3, 1) * guiScale);
        int sw = (int) (width * graphics.pose().last().pose().get(0, 0) * guiScale);
        int sh = (int) (height * graphics.pose().last().pose().get(1, 1) * guiScale);
        graphics.enableScissor(sx, sy, sx + sw, sy + sh);

        // Enable 3D Depth testing and Culling
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.enableCull();
        graphics.pose().pushPose();
        // Move to element center and apply depth that fits in GUI
        graphics.pose().translate(x + width / 2f, y + height / 2f, 200);
        
        // Use uniform scaling to avoid squashing
        float scale = Math.min(width, height) / 2f * zoom;
        graphics.pose().scale(scale, -scale, scale); 

        // Apply global rotation
        graphics.pose().mulPose(new Quaternionf().rotationXYZ(
            (float)Math.toRadians(globalRot.x),
            (float)Math.toRadians(globalRot.y),
            (float)Math.toRadians(globalRot.z)
        ));

        // Setup Lighting for 3D objects
        Lighting.setupFor3DItems();

        for (ModelEntry entry : models) {
            graphics.pose().pushPose();
            graphics.pose().translate(entry.pos.x, entry.pos.y, entry.pos.z);
            
            // Rotation
            graphics.pose().mulPose(new Quaternionf().rotationXYZ(
                (float)Math.toRadians(entry.rot.x),
                (float)Math.toRadians(entry.rot.y),
                (float)Math.toRadians(entry.rot.z)
            ));
            
            graphics.pose().scale(entry.scale, entry.scale, entry.scale);

            // Render Item/Block or Custom OBJ
            if (entry.stack != null) {
                Minecraft.getInstance().getItemRenderer().renderStatic(
                    entry.stack, 
                    ItemDisplayContext.FIXED, 
                    0xF000F0, 
                    OverlayTexture.NO_OVERLAY, 
                    graphics.pose(), 
                    graphics.bufferSource(), 
                    Minecraft.getInstance().level, 
                    0
                );
            } else if (entry.objModel != null) {
                entry.objModel.render(graphics.pose(), graphics.bufferSource(), 0xF000F0, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            }
            
            graphics.pose().popPose();
        }

        graphics.pose().popPose();
        com.mojang.blaze3d.systems.RenderSystem.disableCull();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        graphics.disableScissor();
        
        // Reset Lighting for flat GUI
        Lighting.setupForFlatItems();
    }

    public void setZoom(float zoom) { this.zoom = zoom; }
    public void setGlobalRotation(float rx, float ry, float rz) { this.globalRot.set(rx, ry, rz); }
    public List<ModelEntry> getModels() { return models; }

    public static class ModelEntry {
        public ItemStack stack;
        public WObjModel objModel;
        public Vector3f pos;
        public Vector3f rot;
        public float scale;

        public ModelEntry(ItemStack stack, WObjModel objModel, Vector3f pos, Vector3f rot, float scale) {
            this.stack = stack;
            this.objModel = objModel;
            this.pos = pos;
            this.rot = rot;
            this.scale = scale;
        }
    }
}
