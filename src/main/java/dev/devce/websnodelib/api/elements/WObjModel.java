package dev.devce.websnodelib.api.elements;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class WObjModel {
    private final List<Vector3f> vertices = new ArrayList<>();
    private final List<Vector2f> uvs = new ArrayList<>();
    private final List<Face> faces = new ArrayList<>();

    private Vector3f[] vertexNormals;

    public static WObjModel load(InputStream is) {
        WObjModel model = new WObjModel();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split("\\s+");
                switch (parts[0]) {
                    case "v" -> model.vertices.add(new Vector3f(
                        Float.parseFloat(parts[1]),
                        Float.parseFloat(parts[2]),
                        Float.parseFloat(parts[3])
                    ));
                    case "vt" -> model.uvs.add(new Vector2f(
                        Float.parseFloat(parts[1]),
                        1.0f - Float.parseFloat(parts[2]) // Flip Y
                    ));
                    case "f" -> {
                        List<VertexIndices> vertexIndices = new ArrayList<>();
                        for (int i = 1; i < parts.length; i++) {
                            String[] subParts = parts[i].split("/");
                            int vIdx = Integer.parseInt(subParts[0]) - 1;
                            int uvIdx = subParts.length > 1 && !subParts[1].isEmpty() ? Integer.parseInt(subParts[1]) - 1 : -1;
                            vertexIndices.add(new VertexIndices(vIdx, uvIdx));
                        }
                        for (int i = 1; i < vertexIndices.size() - 1; i++) {
                            model.faces.add(new Face(vertexIndices.get(0), vertexIndices.get(i), vertexIndices.get(i + 1)));
                        }
                    }
                }
            }
            model.calculateSmoothNormals();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return model;
    }

    private net.minecraft.resources.ResourceLocation texture;

    public void setTexture(net.minecraft.resources.ResourceLocation texture) {
        this.texture = texture;
    }

    public static net.minecraft.resources.ResourceLocation loadExternalTexture(String path) {
        try (InputStream is = new java.io.FileInputStream(path)) {
            com.mojang.blaze3d.platform.NativeImage image = com.mojang.blaze3d.platform.NativeImage.read(is);
            net.minecraft.client.renderer.texture.DynamicTexture dynamicTexture = new net.minecraft.client.renderer.texture.DynamicTexture(image);
            return net.minecraft.client.Minecraft.getInstance().getTextureManager().register("webs_custom_" + java.util.UUID.randomUUID(), dynamicTexture);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void calculateSmoothNormals() {
        vertexNormals = new Vector3f[vertices.size()];
        for (int i = 0; i < vertexNormals.length; i++) vertexNormals[i] = new Vector3f(0, 0, 0);

        for (Face face : faces) {
            Vector3f v1 = vertices.get(face.v1.vIdx);
            Vector3f v2 = vertices.get(face.v2.vIdx);
            Vector3f v3 = vertices.get(face.v3.vIdx);
            
            Vector3f edge1 = new Vector3f(v2).sub(v1);
            Vector3f edge2 = new Vector3f(v3).sub(v1);
            Vector3f normal = new Vector3f(edge1).cross(edge2).normalize();

            vertexNormals[face.v1.vIdx].add(normal);
            vertexNormals[face.v2.vIdx].add(normal);
            vertexNormals[face.v3.vIdx].add(normal);
        }

        for (Vector3f n : vertexNormals) n.normalize();
    }

    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay, int color) {
        RenderType type = texture != null ? RenderType.entityCutout(texture) : RenderType.solid();
        VertexConsumer builder = bufferSource.getBuffer(type);
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = ((color >> 24) & 0xFF) / 255.0f;
        if (a == 0) a = 1.0f;

        for (Face face : faces) {
            renderVertex(face.v1, poseStack, builder, r, g, b, a, packedLight, packedOverlay);
            renderVertex(face.v2, poseStack, builder, r, g, b, a, packedLight, packedOverlay);
            renderVertex(face.v3, poseStack, builder, r, g, b, a, packedLight, packedOverlay);
        }
    }

    private void renderVertex(VertexIndices idx, PoseStack poseStack, VertexConsumer builder, float r, float g, float b, float a, int light, int overlay) {
        Vector3f pos = vertices.get(idx.vIdx);
        Vector3f normal = vertexNormals[idx.vIdx];
        Vector2f uv = idx.uvIdx != -1 ? uvs.get(idx.uvIdx) : new Vector2f(0, 0);
        
        builder.addVertex(poseStack.last().pose(), pos.x(), pos.y(), pos.z())
               .setColor(r, g, b, a)
               .setUv(uv.x(), uv.y())
               .setOverlay(overlay)
               .setLight(light)
               .setNormal(poseStack.last(), normal.x(), normal.y(), normal.z());
    }

    private record VertexIndices(int vIdx, int uvIdx) {}
    private record Face(VertexIndices v1, VertexIndices v2, VertexIndices v3) {}
}
