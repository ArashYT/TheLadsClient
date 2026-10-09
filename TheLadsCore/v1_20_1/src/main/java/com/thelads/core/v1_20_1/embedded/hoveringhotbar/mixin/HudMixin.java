// Adapted from Hovering Hotbar 26.3.0 by Fuzs (MPL-2.0); modified by The Lads: repackaged into Lads Core, applied before
// Core's SmoothHotbar redirect of the same call so the taller selection reaches it; ported to Minecraft 1.20.1.
package com.thelads.core.v1_20_1.embedded.hoveringhotbar.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.thelads.core.v1_20_1.embedded.hoveringhotbar.HoveringHotbar;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.PlayerRideableJumping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.20.1 has no gui layer registry to wrap (26.3 uses Fabric's hud element and status bar height registries), so the bottom of the
 * HUD is moved where it is drawn: the hotbar, the experience bar and level, the jump bar, the health and mount health rows are
 * each lifted by the offset with a matrix translation around the method that draws them, and the held item name (a fixed height)
 * is raised by the same amount. The hooks sit on Gui's own draw methods rather than on {@code Gui.render}, because Forge's
 * gui replaces render and still calls several of these helpers.
 */
@Mixin(value = Gui.class, priority = 900)
abstract class HudMixin {

    @Inject(method = "renderHotbar", at = @At("HEAD"))
    private void hoveringhotbar$hotbarStart(float partialTick, GuiGraphics guiGraphics, CallbackInfo ci) {
        hoveringhotbar$lift(guiGraphics);
        HoveringHotbar.hotbarDrawn(guiGraphics.pose().last().pose().m31());
    }

    @Inject(method = "renderHotbar", at = @At("RETURN"))
    private void hoveringhotbar$hotbarEnd(float partialTick, GuiGraphics guiGraphics, CallbackInfo ci) {
        guiGraphics.pose().popPose();
    }

    /** The selection frame is 24 pixels tall in the texture but drawn 22 tall in vanilla, which hides its bottom edge off screen; floating, it shows. */
    @ModifyArg(method = "renderHotbar",
               at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIII)V", ordinal = 1),
               index = 6)
    private int hoveringhotbar$selectionHeight(int height) {
        return height == 22 ? 24 : height;
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"))
    private void hoveringhotbar$experienceStart(GuiGraphics guiGraphics, int x, CallbackInfo ci) {
        HoveringHotbar.EXPERIENCE_DRAWS.incrementAndGet();
        hoveringhotbar$lift(guiGraphics);
    }

    @Inject(method = "renderExperienceBar", at = @At("RETURN"))
    private void hoveringhotbar$experienceEnd(GuiGraphics guiGraphics, int x, CallbackInfo ci) {
        guiGraphics.pose().popPose();
    }

    /** The level number is drawn at screenHeight - 31 - 4, over the top of the bar; the console edition (and 26.3) put it three pixels higher. */
    @ModifyConstant(method = "renderExperienceBar", constant = @Constant(intValue = 31))
    private int hoveringhotbar$levelAboveBar(int distanceFromBottom) {
        return HoveringHotbar.CONFIG.moveExperienceAboveBar ? distanceFromBottom + 3 : distanceFromBottom;
    }

    @Inject(method = "renderJumpMeter", at = @At("HEAD"))
    private void hoveringhotbar$jumpStart(PlayerRideableJumping rideable, GuiGraphics guiGraphics, int x, CallbackInfo ci) {
        hoveringhotbar$lift(guiGraphics);
    }

    @Inject(method = "renderJumpMeter", at = @At("RETURN"))
    private void hoveringhotbar$jumpEnd(PlayerRideableJumping rideable, GuiGraphics guiGraphics, int x, CallbackInfo ci) {
        guiGraphics.pose().popPose();
    }

    @Inject(method = "renderPlayerHealth", at = @At("HEAD"))
    private void hoveringhotbar$healthStart(GuiGraphics guiGraphics, CallbackInfo ci) {
        HoveringHotbar.STATUS_DRAWS.incrementAndGet();
        hoveringhotbar$lift(guiGraphics);
    }

    @Inject(method = "renderPlayerHealth", at = @At("RETURN"))
    private void hoveringhotbar$healthEnd(GuiGraphics guiGraphics, CallbackInfo ci) {
        guiGraphics.pose().popPose();
    }

    @Inject(method = "renderVehicleHealth", at = @At("HEAD"))
    private void hoveringhotbar$mountStart(GuiGraphics guiGraphics, CallbackInfo ci) {
        hoveringhotbar$lift(guiGraphics);
    }

    @Inject(method = "renderVehicleHealth", at = @At("RETURN"))
    private void hoveringhotbar$mountEnd(GuiGraphics guiGraphics, CallbackInfo ci) {
        guiGraphics.pose().popPose();
    }

    /** The held item's name floats 59 pixels above the bottom (above the status bars); keep it above the lifted bars. */
    @ModifyConstant(method = "renderSelectedItemName", constant = @Constant(intValue = 59))
    private int hoveringhotbar$itemNameHeight(int distanceFromBottom) {
        HoveringHotbar.ITEM_NAME_LINES.incrementAndGet();
        return distanceFromBottom + HoveringHotbar.CONFIG.getHotbarOffset();
    }

    @Unique
    private static void hoveringhotbar$lift(GuiGraphics guiGraphics) {
        PoseStack pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(0.0F, -HoveringHotbar.CONFIG.getHotbarOffset(), 0.0F);
    }
}
