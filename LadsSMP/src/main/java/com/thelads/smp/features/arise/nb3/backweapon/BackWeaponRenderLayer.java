package com.thelads.smp.features.arise.nb3.backweapon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.thelads.smp.features.arise.nb3.AriseNb3Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Render layer that displays the player's sheathed primary weapon/tool across their back.
 */
public class BackWeaponRenderLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
    private final ItemStackRenderState itemRenderState = new ItemStackRenderState();

    public BackWeaponRenderLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, AvatarRenderState state, float limbSwing, float limbSwingAmount) {
        if (!AriseNb3Config.get().backWeapon.enabled || !AriseNb3Config.get().backWeapon.showBackWeapon) {
            return;
        }
        if (state.isSpectator) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        var entity = mc.level.getEntity(state.id);
        if (!(entity instanceof Player player)) return;

        ItemStack backStack = BackWeaponFeature.getBackWeaponStack(player);
        if (backStack.isEmpty()) return;

        poseStack.pushPose();
        // Attach to torso part so it rotates with body/sneaking/swimming
        this.getParentModel().body.translateAndRotate(poseStack);

        int xOff = AriseNb3Config.get().backWeapon.xOffset;
        int yOff = AriseNb3Config.get().backWeapon.yOffset;

        // Position on back
        poseStack.translate(xOff * 0.01f, 0.25f + yOff * 0.01f, 0.18f);
        poseStack.rotateDegrees(Axis.XP, 180.0f);
        poseStack.rotateDegrees(Axis.ZP, 45.0f);
        poseStack.scale(0.65f, 0.65f, 0.65f);

        mc.getItemModelResolver().updateForLiving(itemRenderState, backStack, ItemDisplayContext.FIXED, player);
        itemRenderState.submit(poseStack, submitNodeCollector, packedLight, OverlayTexture.NO_OVERLAY, 0);

        poseStack.popPose();
    }
}
