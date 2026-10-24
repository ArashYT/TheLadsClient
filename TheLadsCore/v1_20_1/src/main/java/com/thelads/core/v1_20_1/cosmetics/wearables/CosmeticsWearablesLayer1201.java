package com.thelads.core.v1_20_1.cosmetics.wearables;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.client.cosmetics.wearables.CosmeticItem;
import com.thelads.core.client.cosmetics.wearables.CosmeticLibrary;
import com.thelads.core.client.cosmetics.wearables.CosmeticModels;
import com.thelads.core.client.cosmetics.wearables.CosmeticSlot;
import com.thelads.core.client.cosmetics.wearables.CosmeticsManager;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CosmeticsWearablesLayer1201 extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final Vector3f POS = new Vector3f();
    private static final Vector3f NORMAL = new Vector3f();
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();

    public CosmeticsWearablesLayer1201(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    public static ResourceLocation getResource(String path) {
        if (path == null) return null;
        return TEXTURES.computeIfAbsent(path, p -> {
            String clean = p.startsWith("theladscore:") ? p.substring("theladscore:".length()) : p;
            return new ResourceLocation("theladscore", clean);
        });
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        CosmeticsManager manager = CosmeticsManager.get();
        UUID uuid = player.getUUID();

        String hatId = manager.getEquippedHat(uuid);
        String glassesId = manager.getEquippedGlasses(uuid);

        if (hatId == null && glassesId == null) return;

        poseStack.pushPose();
        this.getParentModel().getHead().translateAndRotate(poseStack);

        if (hatId != null && manager.shouldRender(uuid, CosmeticSlot.HAT)) {
            renderCosmetic(hatId, poseStack, buffer, packedLight);
        }
        if (glassesId != null && manager.shouldRender(uuid, CosmeticSlot.GLASSES)) {
            renderCosmetic(glassesId, poseStack, buffer, packedLight);
        }

        poseStack.popPose();
    }

    private void renderCosmetic(String itemId, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float[] mesh = CosmeticModels.getMesh(itemId);
        if (mesh == null || mesh.length == 0) return;

        CosmeticItem item = CosmeticLibrary.get(itemId);
        if (item == null || item.texturePath() == null) return;

        ResourceLocation texture = getResource(item.texturePath());
        VertexConsumer consumer = buffer.getBuffer(RenderType.armorCutoutNoCull(texture));

        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normals = poseStack.last().normal();

        for (int i = 0; i < mesh.length; i += CosmeticModels.STRIDE) {
            matrix.transformPosition(mesh[i], mesh[i + 1], mesh[i + 2], POS);
            normals.transform(mesh[i + 5], mesh[i + 6], mesh[i + 7], NORMAL);
            consumer.vertex(POS.x, POS.y, POS.z, 1.0f, 1.0f, 1.0f, 1.0f,
                mesh[i + 3], mesh[i + 4], OverlayTexture.NO_OVERLAY, packedLight,
                NORMAL.x, NORMAL.y, NORMAL.z);
        }
    }
}
