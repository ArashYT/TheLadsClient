/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.vertex.PoseStack
 *  com.mojang.blaze3d.vertex.VertexConsumer
 *  dev.tr7zw.transition.mc.GeneralUtil
 *  dev.tr7zw.transition.mc.MathUtil
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.renderer.SubmitNodeCollector
 *  net.minecraft.client.renderer.rendertype.RenderType
 *  net.minecraft.client.renderer.rendertype.RenderTypes
 *  net.minecraft.client.renderer.state.MapRenderState
 *  net.minecraft.core.component.DataComponents
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.MapItem
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.saveddata.maps.MapId
 *  net.minecraft.world.level.saveddata.maps.MapItemSavedData
 *  org.joml.Matrix4f
 *  org.joml.Matrix4fc
 *  org.joml.Quaternionfc
 */
package dev.tr7zw.notenoughanimations.util;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tr7zw.transition.mc.GeneralUtil;
import dev.tr7zw.transition.mc.MathUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;

public class MapRenderer {
    private static final RenderType MAP_BACKGROUND = RenderTypes.text((Identifier)GeneralUtil.getResourceLocation((String)"textures/map/map_background.png"));
    private static final RenderType MAP_BACKGROUND_CHECKERBOARD = RenderTypes.text((Identifier)GeneralUtil.getResourceLocation((String)"textures/map/map_background_checkerboard.png"));

    public static void renderFirstPersonMap(PoseStack matrices, SubmitNodeCollector vertexConsumers, int light, ItemStack stack, boolean small, boolean leftHanded) {
        Minecraft client = Minecraft.getInstance();
        if (small) {
            matrices.mulPose((Quaternionfc)MathUtil.YP.rotationDegrees(160.0f));
            matrices.mulPose((Quaternionfc)MathUtil.ZP.rotationDegrees(180.0f));
            matrices.scale(0.38f, 0.38f, 0.38f);
            matrices.translate(-0.1, -1.2, 0.0);
            matrices.scale(0.0098125f, 0.0098125f, 0.0098125f);
        } else {
            if (leftHanded) {
                matrices.mulPose((Quaternionfc)MathUtil.YP.rotationDegrees(154.5f));
                matrices.mulPose((Quaternionfc)MathUtil.ZP.rotationDegrees(166.5f));
                matrices.scale(0.38f, 0.38f, 0.38f);
                matrices.translate(0.585, -1.225, 0.15);
            } else {
                matrices.mulPose((Quaternionfc)MathUtil.YP.rotationDegrees(155.0f));
                matrices.mulPose((Quaternionfc)MathUtil.ZP.rotationDegrees(213.5f));
                matrices.scale(0.38f, 0.38f, 0.38f);
                matrices.translate(-0.955, -1.8, 0.0);
            }
            matrices.scale(0.0138125f, 0.0138125f, 0.0138125f);
        }
        MapId mapid = (MapId)stack.get(DataComponents.MAP_ID);
        MapItemSavedData mapState = MapItem.getSavedData((ItemStack)stack, (Level)client.level);
        RenderType renderType = mapState == null ? MAP_BACKGROUND : MAP_BACKGROUND_CHECKERBOARD;
        vertexConsumers.submitCustomGeometry(matrices, renderType, (pose, vertexConsumer) -> {
            vertexConsumer.addVertex(pose, -7.0f, 135.0f, 0.0f).setColor(-1).setUv(0.0f, 1.0f).setLight(light);
            vertexConsumer.addVertex(pose, 135.0f, 135.0f, 0.0f).setColor(-1).setUv(1.0f, 1.0f).setLight(light);
            vertexConsumer.addVertex(pose, 135.0f, -7.0f, 0.0f).setColor(-1).setUv(1.0f, 0.0f).setLight(light);
            vertexConsumer.addVertex(pose, -7.0f, -7.0f, 0.0f).setColor(-1).setUv(0.0f, 0.0f).setLight(light);
        });
        vertexConsumers.submitCustomGeometry(matrices, MAP_BACKGROUND, (pose, vertexConsumer) -> {
            vertexConsumer.addVertex(pose, -7.0f, -7.0f, 0.0f).setColor(-1).setUv(0.0f, 0.0f).setLight(light);
            vertexConsumer.addVertex(pose, 135.0f, -7.0f, 0.0f).setColor(-1).setUv(1.0f, 0.0f).setLight(light);
            vertexConsumer.addVertex(pose, 135.0f, 135.0f, 0.0f).setColor(-1).setUv(1.0f, 1.0f).setLight(light);
            vertexConsumer.addVertex(pose, -7.0f, 135.0f, 0.0f).setColor(-1).setUv(0.0f, 1.0f).setLight(light);
        });
        if (mapState != null) {
            MapRenderState mapRenderState = new MapRenderState();
            client.getMapRenderer().extractRenderState(mapid, mapState, mapRenderState);
            client.getMapRenderer().render(mapRenderState, matrices, vertexConsumers, false, light);
        }
    }

    public static void addVertex(VertexConsumer cons, Matrix4f matrix4f, float x, float y, float z, float u, float v, int lightmapUV) {
        cons.addVertex((Matrix4fc)matrix4f, x, y, z).setColor(-1).setUv(u, v).setLight(lightmapUV);
    }
}

