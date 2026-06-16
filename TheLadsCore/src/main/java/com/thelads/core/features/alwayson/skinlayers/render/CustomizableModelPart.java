package com.thelads.core.features.alwayson.skinlayers.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.features.alwayson.skinlayers.api.Mesh;
import com.thelads.core.features.alwayson.skinlayers.api.MeshTransformer;
import com.thelads.core.features.alwayson.skinlayers.api.SkinLayersAPI;
import com.thelads.core.features.alwayson.skinlayers.versionless.render.CustomModelPart;
import com.thelads.core.features.alwayson.skinlayers.versionless.render.CustomizableCube;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class CustomizableModelPart extends CustomModelPart implements Mesh {
    private final List<ModelPart.Cube> cubes;
    private final Map<String, ModelPart> children;
    private final Vector4f[] vector4f = new Vector4f[]{new Vector4f(), new Vector4f(), new Vector4f(), new Vector4f()};

    public CustomizableModelPart(List<ModelPart.Cube> list, List<CustomizableCube> customCubes, Map<String, ModelPart> map) {
        super(customCubes);
        this.cubes = list;
        this.children = map;
    }

    @Override
    public void loadPose(PartPose partPose) {
        this.x = partPose.x();
        this.y = partPose.y();
        this.z = partPose.z();
        this.xRot = partPose.xRot();
        this.yRot = partPose.yRot();
        this.zRot = partPose.zRot();
    }

    @Override
    public void copyFrom(ModelPart modelPart) {
        this.xRot = modelPart.xRot;
        this.yRot = modelPart.yRot;
        this.zRot = modelPart.zRot;
        this.x = modelPart.x;
        this.y = modelPart.y;
        this.z = modelPart.z;
    }

    @Override
    public void render(PoseStack poseStack, VertexConsumer vertexConsumer, int i, int j) {
        this.render(null, poseStack, vertexConsumer, i, j, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    private int convertFloatColorToInteger(float color) {
        return color > 1.0f ? 255 : Math.round(color * 255.0f);
    }

    @Override
    @Deprecated
    public void render(ModelPart vanillaModel, PoseStack poseStack, VertexConsumer vertexConsumer, int light, int overlay, float red, float green, float blue, float alpha) {
        int color = (this.convertFloatColorToInteger(alpha) & 0xFF) << 24 | (this.convertFloatColorToInteger(red) & 0xFF) << 16 | (this.convertFloatColorToInteger(green) & 0xFF) << 8 | this.convertFloatColorToInteger(blue) & 0xFF;
        this.render(vanillaModel, poseStack, vertexConsumer, light, overlay, color);
    }

    @Override
    public void render(ModelPart vanillaModel, PoseStack poseStack, VertexConsumer vertexConsumer, int light, int overlay, int color) {
        if (!this.visible) {
            return;
        }
        poseStack.pushPose();
        this.translateAndRotate(poseStack);
        this.compile(vanillaModel, poseStack.last(), vertexConsumer, light, overlay, color);
        for (ModelPart modelPart : this.children.values()) {
            modelPart.render(poseStack, vertexConsumer, light, overlay, color);
        }
        poseStack.popPose();
    }

    public void translateAndRotate(PoseStack poseStack) {
        if (this.x != 0.0f || this.y != 0.0f || this.z != 0.0f) {
            poseStack.translate(this.x / 16.0f, this.y / 16.0f, this.z / 16.0f);
        }
        if (this.xRot != 0.0f || this.yRot != 0.0f || this.zRot != 0.0f) {
            poseStack.mulPose(new Quaternionf().rotationZYX(this.zRot, this.yRot, this.xRot));
        }
    }

    private void compile(ModelPart vanillaModel, PoseStack.Pose pose, VertexConsumer vertexConsumer, int light, int overlay, int color) {
        MeshTransformer transformer = SkinLayersAPI.getMeshTransformerProvider().prepareTransformer(vanillaModel);
        Matrix4f matrix4f = pose.pose();
        Matrix3f matrix3f = pose.normal();
        for (int id = 0; id < this.polygonData.length; id += 23) {
            int o;
            Vector3f vector3f = new Vector3f(this.polygonData[id + 0], this.polygonData[id + 1], this.polygonData[id + 2]);
            for (o = 0; o < 4; ++o) {
                this.vector4f[o].set(this.polygonData[id + 3 + o * 5 + 0], this.polygonData[id + 3 + o * 5 + 1], this.polygonData[id + 3 + o * 5 + 2], 1.0f);
            }
            transformer.transform(vector3f, this.vector4f);
            vector3f = matrix3f.transform(vector3f);
            for (o = 0; o < 4; ++o) {
                matrix4f.transform(this.vector4f[o]);
                vertexConsumer.addVertex(this.vector4f[o].x(), this.vector4f[o].y(), this.vector4f[o].z());
                vertexConsumer.setColor(color);
                vertexConsumer.setUv(this.polygonData[id + 3 + o * 5 + 3], this.polygonData[id + 3 + o * 5 + 4]);
                vertexConsumer.setOverlay(overlay);
                vertexConsumer.setLight(light);
                vertexConsumer.setNormal(vector3f.x(), vector3f.y(), vector3f.z());
            }
        }
        for (ModelPart.Cube cube : this.cubes) {
            transformer.transform(cube);
            cube.compile(pose, vertexConsumer, light, overlay, color);
        }
    }

    @Override
    public void reset() {
        this.x = 0.0f;
        this.y = 0.0f;
        this.z = 0.0f;
        this.xRot = 0.0f;
        this.yRot = 0.0f;
        this.zRot = 0.0f;
    }
}
