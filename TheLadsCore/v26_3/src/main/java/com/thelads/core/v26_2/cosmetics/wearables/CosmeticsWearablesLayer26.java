package com.thelads.core.v26_2.cosmetics.wearables;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.client.cosmetics.wearables.CosmeticItem;
import com.thelads.core.client.cosmetics.wearables.CosmeticLibrary;
import com.thelads.core.client.cosmetics.wearables.CosmeticModels;
import com.thelads.core.client.cosmetics.wearables.CosmeticSlot;
import com.thelads.core.client.cosmetics.wearables.CosmeticsManager;
import com.thelads.core.v26_2.cosmetics.anim.EmoteState26;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CosmeticsWearablesLayer26 extends RenderLayer<AvatarRenderState, PlayerModel> {
    private static final Vector3f POS = new Vector3f();
    private static final Vector3f NORMAL = new Vector3f();
    private static final Map<String, Identifier> IDENTIFIERS = new HashMap<>();

    public CosmeticsWearablesLayer26(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
        super(renderer);
    }

    public static Identifier getIdentifier(String path) {
        if (path == null) return null;
        return IDENTIFIERS.computeIfAbsent(path, p -> {
            String clean = p.startsWith("theladscore:") ? p.substring("theladscore:".length()) : p;
            return Identifier.parse("theladscore:" + clean);
        });
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int packedLight,
                       AvatarRenderState state, float yRot, float xRot) {
        UUID uuid = ((EmoteState26) state).lads$uuid();
        if (uuid == null) return;

        CosmeticsManager manager = CosmeticsManager.get();
        String hatId = manager.getEquippedHat(uuid);
        String glassesId = manager.getEquippedGlasses(uuid);
        if (hatId == null && glassesId == null) return;

        poseStack.pushPose();
        this.getParentModel().getHead().translateAndRotate(poseStack);

        if (hatId != null && manager.shouldRender(uuid, CosmeticSlot.HAT)) {
            submitCosmetic(hatId, poseStack, collector, packedLight);
        }
        if (glassesId != null && manager.shouldRender(uuid, CosmeticSlot.GLASSES)) {
            submitCosmetic(glassesId, poseStack, collector, packedLight);
        }

        poseStack.popPose();
    }

    private void submitCosmetic(String itemId, PoseStack poseStack, SubmitNodeCollector collector, int packedLight) {
        float[] mesh = CosmeticModels.getMesh(itemId);
        if (mesh == null || mesh.length == 0) return;

        CosmeticItem item = CosmeticLibrary.get(itemId);
        if (item == null || item.texturePath() == null) return;

        Identifier id = getIdentifier(item.texturePath());
        RenderType type = RenderTypes.armorCutoutNoCull(id);

        collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
            Matrix4f matrix = pose.pose();
            for (int i = 0; i < mesh.length; i += CosmeticModels.STRIDE) {
                matrix.transformPosition(mesh[i], mesh[i + 1], mesh[i + 2], POS);
                pose.transformNormal(mesh[i + 5], mesh[i + 6], mesh[i + 7], NORMAL);
                buffer.addVertex(POS.x, POS.y, POS.z, -1, mesh[i + 3], mesh[i + 4],
                    OverlayTexture.NO_OVERLAY, packedLight, NORMAL.x, NORMAL.y, NORMAL.z);
            }
        });
    }
}
