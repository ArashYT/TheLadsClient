/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.vertex.PoseStack
 *  dev.tr7zw.transition.mc.GeneralUtil
 *  dev.tr7zw.transition.mc.ItemUtil
 *  dev.tr7zw.transition.mc.MathUtil
 *  net.minecraft.client.model.HumanoidModel
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.client.renderer.SubmitNodeCollector
 *  net.minecraft.client.renderer.entity.RenderLayerParent
 *  net.minecraft.client.renderer.entity.layers.RenderLayer
 *  net.minecraft.client.renderer.entity.state.HumanoidRenderState
 *  net.minecraft.world.entity.EquipmentSlot
 *  net.minecraft.world.entity.HumanoidArm
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.Items
 *  org.joml.Quaternionfc
 */
package dev.tr7zw.notenoughanimations.renderlayer;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.tr7zw.notenoughanimations.access.ExtendedLivingRenderState;
import dev.tr7zw.notenoughanimations.access.PlayerData;
import dev.tr7zw.notenoughanimations.versionless.NEABaseMod;
import dev.tr7zw.transition.mc.GeneralUtil;
import dev.tr7zw.transition.mc.ItemUtil;
import dev.tr7zw.transition.mc.MathUtil;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionfc;

public class SwordRenderLayer
extends RenderLayer<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    private boolean lazyInit = true;
    private static Set<Item> items = new HashSet<Item>();
    private boolean disabled = false;

    public SwordRenderLayer(RenderLayerParent<HumanoidRenderState, HumanoidModel<HumanoidRenderState>> renderer) {
        super(renderer);
    }

    public static void update(Player player) {
        PlayerData data = (PlayerData)player;
        if (items.contains(player.getMainHandItem().getItem())) {
            data.setSideSword(player.getMainHandItem());
        }
        if (items.contains(player.getOffhandItem().getItem())) {
            data.setSideSword(player.getOffhandItem());
        }
    }

    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int i, HumanoidRenderState entityRenderState, float f, float g) {
        boolean wearingArmor;
        if (!(((ExtendedLivingRenderState)entityRenderState).getEntity() instanceof AbstractClientPlayer)) {
            return;
        }
        AbstractClientPlayer player = (AbstractClientPlayer)((ExtendedLivingRenderState)entityRenderState).getEntity();
        if (this.disabled || player == null) {
            return;
        }
        if (this.lazyInit) {
            this.lazyInit = false;
            this.init();
        }
        if (!NEABaseMod.config.showLastUsedSword) {
            return;
        }
        if (player.isInvisible() || player.isSleeping()) {
            return;
        }
        if (!(player instanceof PlayerData)) {
            return;
        }
        if (player.isPassenger()) {
            return;
        }
        PlayerData data = (PlayerData)player;
        ItemStack itemStack = data.getSideSword();
        if (itemStack.isEmpty()) {
            return;
        }
        if (player.getMainHandItem() == itemStack || player.getOffhandItem() == itemStack) {
            return;
        }
        poseStack.pushPose();
        ((HumanoidModel)this.getParentModel()).body.translateAndRotate(poseStack);
        boolean lefthanded = player.getMainArm() == HumanoidArm.LEFT;
        boolean bl = wearingArmor = !player.getItemBySlot(EquipmentSlot.LEGS).isEmpty();
        if (!player.getItemBySlot(EquipmentSlot.CHEST).isEmpty() && player.getItemBySlot(EquipmentSlot.CHEST).getItem() != Items.ELYTRA) {
            wearingArmor = true;
        }
        double offsetX = wearingArmor ? 0.3 : 0.28;
        float swordRotation = -80.0f;
        if (lefthanded) {
            offsetX *= -1.0;
        }
        poseStack.translate(offsetX, 0.85, 0.25);
        poseStack.mulPose((Quaternionfc)MathUtil.XP.rotationDegrees(swordRotation));
        poseStack.mulPose((Quaternionfc)MathUtil.YP.rotationDegrees(180.0f));
        poseStack.popPose();
    }

    private void init() {
        for (String itemKey : NEABaseMod.config.sheathSwords) {
            Item item;
            if (!itemKey.contains(":") || (item = ItemUtil.getItem((Identifier)GeneralUtil.getResourceLocation((String)itemKey.split(":")[0], (String)itemKey.split(":")[1]))) == Items.AIR) continue;
            items.add(item);
        }
        try {
            Class.forName("net.backslot.BackSlotMain");
            this.disabled = true;
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }
}

