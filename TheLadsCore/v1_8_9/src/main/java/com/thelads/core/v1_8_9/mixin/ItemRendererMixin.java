package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.OldAnimations;
import com.thelads.core.client.OldAnimations.Held;
import com.thelads.core.client.OldAnimations.Use;
import com.thelads.core.modules.OldAnimationsModule.Feature;
import com.thelads.core.v1_8_9.feature.LegacySwing189;
import com.thelads.core.v1_8_9.feature.OldAnimations189;
import com.thelads.core.v1_8_9.feature.OldAnimations189.Hook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms.TransformType;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * LegacySwing (LegacySwing189), as 26.x LegacySwingMixin/LegacyEquipMixin: the legacy swing replaces vanilla's whole swing,
 * its translation too (26.x cancels swingArm, which holds both), and using an item no longer pops the hand down. Switching
 * items keeps vanilla's lower-and-raise.
 *
 * <p>1.7 Animations (OldAnimations189), first person. OptiFine M5 keeps renderItemInFirstPerson as it is (it only adds a shaders
 * early return), so its calls are redirected: a HEAD inject reads the frame's item and use, the use branch's transforms become
 * the shared 1.7 hand recipe (which keeps the swing: blockhitting) and the item draw becomes 1.7's icon placement with no 1.8
 * display transform. The idle hand stays vanilla's, which is 1.7's, so LegacySwing still swings it; a block item (a torch) also keeps
 * 1.8.9's placement while LegacySwing is on, so placing blocks is LegacySwing's alone (OldAnimations.legacySwingPlaces). While an
 * item is in use 1.7 Animations draws the hand instead, so the two never both transform it. Low Fire lowers the fire overlay's quads.
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Shadow @Final private Minecraft mc;
    @Shadow private ItemStack itemToRender;
    @Shadow private void transformFirstPersonItem(float equipProgress, float swingProgress) {}
    @Shadow private void performDrinking(AbstractClientPlayer clientPlayer, float partialTicks) {}
    @Shadow private void doBlockTransformations() {}
    @Shadow private void doBowTransformations(float partialTicks, AbstractClientPlayer clientPlayer) {}

    /** This frame's use, or null when 1.8.9 draws the frame (module off, block or special model, a use 1.7 drew alike). */
    @Unique private Use ladsUse;
    @Unique private Held ladsHeld;
    /** The 1.7 hand recipe replaces vanilla's use transforms; false with a bow while "1.7 bow position" is off. */
    @Unique private boolean ladsReplay;
    /** The item goes where 1.7 drew it, with TransformType.NONE instead of the 1.8 first-person transform. */
    @Unique private boolean ladsIcon;

    @Inject(method = "transformFirstPersonItem", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsSwing(float equipProgress, float swingProgress, CallbackInfo ci) {
        if (LegacySwing189.transform(equipProgress, swingProgress)) ci.cancel();
    }

    @Inject(method = "doItemUsedTransformations", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsNoVanillaSwingMove(float swingProgress, CallbackInfo ci) {
        if (LegacySwing189.enabled()) ci.cancel();
    }

    @Inject(method = {"resetEquippedProgress", "resetEquippedProgress2"}, at = @At("HEAD"), cancellable = true, require = 2)
    private void ladsNoUsePop(CallbackInfo ci) {
        if (LegacySwing189.enabled()) ci.cancel();
    }

    @Inject(method = "renderItemInFirstPerson", at = @At("HEAD"), require = 1)
    private void ladsFrame(float partialTicks, CallbackInfo ci) {
        ladsUse = null;
        ladsReplay = ladsIcon = false;
        if (!OldAnimations189.MODULE.isEnabled() || itemToRender == null || mc.thePlayer == null) return;
        ladsHeld = OldAnimations189.held(itemToRender);
        if (ladsHeld == null) return;
        ladsUse = OldAnimations189.use(mc.thePlayer, itemToRender);
        if (ladsUse == null) return;
        ladsReplay = ladsUse != Use.NONE && (ladsUse != Use.BOW || OldAnimations189.active(Feature.BOW));
        ladsIcon = OldAnimations189.MODULE.iconPlacement(OldAnimations189.PLATFORM, ladsUse, ladsHeld)
            && !OldAnimations.legacySwingPlaces(LegacySwing189.enabled(), ladsUse, OldAnimations189.placesBlock(itemToRender.getItem()));
    }

    /** All five calls: the idle one and the use branch's four (each passes swing 0, 1.8's drop of the swing while using). */
    @Redirect(method = "renderItemInFirstPerson", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/ItemRenderer;transformFirstPersonItem(FF)V"), require = 5, allow = 5)
    private void ladsHand(ItemRenderer self, float equip, float swing, float partialTicks) {
        if (ladsUse == null || ladsUse == Use.NONE) {
            transformFirstPersonItem(equip, swing);
            return;
        }
        EntityPlayerSP player = mc.thePlayer;
        float shown = OldAnimations189.swingShown(ladsUse, player.getSwingProgress(partialTicks));
        OldAnimations189.usedSwing = Math.max(OldAnimations189.usedSwing, shown);
        if (!ladsReplay) {
            transformFirstPersonItem(equip, shown); // 1.8.9's bow draw follows, with 1.7's swing while using
            return;
        }
        OldAnimations.hand(OldAnimations189.GL, 1, equip, shown, ladsUse, player.getItemInUseCount(), partialTicks,
            itemToRender.getMaxItemUseDuration());
        OldAnimations189.hit(Hook.FP_HAND);
    }

    @Redirect(method = "renderItemInFirstPerson", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/ItemRenderer;performDrinking(Lnet/minecraft/client/entity/AbstractClientPlayer;F)V"),
        require = 1, allow = 1)
    private void ladsDrinking(ItemRenderer self, AbstractClientPlayer player, float partialTicks) {
        if (!ladsReplay) performDrinking(player, partialTicks);
    }

    @Redirect(method = "renderItemInFirstPerson", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/ItemRenderer;doBlockTransformations()V"), require = 1, allow = 1)
    private void ladsBlock(ItemRenderer self) {
        if (!ladsReplay) doBlockTransformations();
    }

    @Redirect(method = "renderItemInFirstPerson", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/ItemRenderer;doBowTransformations(FLnet/minecraft/client/entity/AbstractClientPlayer;)V"),
        require = 1, allow = 1)
    private void ladsBow(ItemRenderer self, float partialTicks, AbstractClientPlayer player) {
        if (!ladsReplay) doBowTransformations(partialTicks, player);
    }

    @Redirect(method = "renderItemInFirstPerson", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItem("
        + "Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;)V"),
        require = 1, allow = 1)
    private void ladsDrawItem(ItemRenderer self, EntityLivingBase player, ItemStack stack, TransformType type) {
        if (!ladsIcon) {
            self.renderItem(player, stack, type);
            return;
        }
        OldAnimations.item(OldAnimations189.GL, 1, ladsHeld == Held.ROD); // full size: RenderItem.preTransform undoes its 0.5
        self.renderItem(player, stack, TransformType.NONE);
        OldAnimations189.hit(Hook.FP_ICON);
    }

    /** Low Fire: the y of the one translate placing each fire quad (the same call in OptiFine M5). */
    @ModifyArg(method = "renderFireInFirstPerson", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/renderer/GlStateManager;translate(FFF)V"), index = 1, require = 1, allow = 1)
    private float ladsLowFire(float y) {
        return OldAnimations189.fireY(y);
    }
}
