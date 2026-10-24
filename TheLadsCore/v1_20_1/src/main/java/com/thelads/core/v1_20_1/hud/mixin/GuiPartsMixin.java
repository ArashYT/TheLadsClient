package com.thelads.core.v1_20_1.hud.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.modules.RaisedModule;
import com.thelads.core.v1_20_1.hud.HudParts1201;
import com.thelads.core.v1_20_1.hud.Raised1201;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.PlayerRideableJumping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Plain Fabric: Raised and Auto Hide around vanilla's hotbar group inside Gui.render, and Raised under the action bar message.
 * Forge's GUI replaces Gui.render, so these call sites are not reached there; ForgeOverlays1201 does the same through its events.
 */
@Mixin(Gui.class)
public abstract class GuiPartsMixin {
    private static final String RENDER = "render(Lnet/minecraft/client/gui/GuiGraphics;F)V";
    private static final String GUI = "Lnet/minecraft/client/gui/Gui;";
    private static final String G = "Lnet/minecraft/client/gui/GuiGraphics;";

    @WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = GUI + "renderHotbar(F" + G + ")V"), require = 1)
    private void lads$hotbar(Gui gui, float partialTick, GuiGraphics graphics, Operation<Void> original) {
        HudParts1201.group(graphics, RaisedModule.HOTBAR, () -> original.call(gui, partialTick, graphics));
    }

    @WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = GUI + "renderPlayerHealth(" + G + ")V"), require = 1)
    private void lads$health(Gui gui, GuiGraphics graphics, Operation<Void> original) {
        HudParts1201.group(graphics, () -> original.call(gui, graphics));
    }

    @WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = GUI + "renderVehicleHealth(" + G + ")V"), require = 1)
    private void lads$vehicle(Gui gui, GuiGraphics graphics, Operation<Void> original) {
        HudParts1201.group(graphics, RaisedModule.MOUNT, () -> original.call(gui, graphics));
    }

    @WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = GUI + "renderJumpMeter(Lnet/minecraft/world/entity/PlayerRideableJumping;" + G + "I)V"), require = 1)
    private void lads$jump(Gui gui, PlayerRideableJumping jumping, GuiGraphics graphics, int x, Operation<Void> original) {
        HudParts1201.group(graphics, () -> original.call(gui, jumping, graphics, x));
    }

    @WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = GUI + "renderExperienceBar(" + G + "I)V"), require = 1)
    private void lads$experience(Gui gui, GuiGraphics graphics, int x, Operation<Void> original) {
        HudParts1201.group(graphics, () -> original.call(gui, graphics, x));
    }

    @WrapOperation(method = RENDER, at = @At(value = "INVOKE", target = GUI + "renderSelectedItemName(" + G + ")V"), require = 1)
    private void lads$itemName(Gui gui, GuiGraphics graphics, Operation<Void> original) {
        HudParts1201.group(graphics, () -> original.call(gui, graphics));
    }

    /** The action bar message sits above the hotbar, so Raised moves it with it (its pose is translated to screen centre x, bottom - 68). */
    @ModifyArg(method = RENDER, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0), index = 1, require = 1)
    private float lads$raiseOverlayMessage(float y) {
        return y - Raised1201.hotbar();
    }
}
