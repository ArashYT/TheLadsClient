package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.MouseTweaks189;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lads Mouse Tweaks: the container screen's own mouse handlers go to MouseTweaks189 first. Also hotbar keys bound to a mouse
 * button (Mouse 4/5...): 1.8.9 checked them for keyboard keys only, so a side button never swapped the hovered slot.
 */
@Mixin(GuiContainer.class)
public abstract class GuiContainerMouseTweaksMixin extends GuiScreen implements MouseTweaks189.Screen {
    @Shadow private Slot getSlotAtPosition(int x, int y) { return null; }
    @Shadow protected abstract void handleMouseClick(Slot slot, int slotId, int button, int mode);
    @Shadow protected abstract boolean checkHotbarKeys(int keyCode);
    @Shadow protected int guiLeft;
    @Shadow protected int guiTop;
    @Shadow protected int xSize;
    @Shadow protected int ySize;

    /** As later versions: any button but left, right and creative pick-block swaps the hovered slot with the hotbar slot bound to it. */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void ladsClickOutsideToClose(int x, int y, int button, CallbackInfo ci) {
        if (((com.thelads.core.v1_8_9.feature.ChatTweaks189.Typing) this).lads$overSearch(x, y)) return; // Inventory Search's box sits above the container
        if (com.thelads.core.client.ContainerQoL.isClickOutsideToCloseEnabled() && button == 0) {
            if (com.thelads.core.client.ContainerQoL.isClickOutside(x, y, guiLeft, guiTop, xSize, ySize)) {
                if (mc.thePlayer != null && mc.thePlayer.inventory.getItemStack() == null) {
                    mc.thePlayer.closeScreen();
                    ci.cancel();
                    return;
                }
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void ladsHotbarButton(int x, int y, int button, CallbackInfo ci) {
        if (button == 0 || button == 1 || button - 100 == mc.gameSettings.keyBindPickBlock.getKeyCode() && mc.playerController != null && mc.playerController.isInCreativeMode()) return;
        if (checkHotbarKeys(button - 100)) ci.cancel(); // mouse binds are stored as button - 100
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void ladsMouseTweaksPress(int x, int y, int button, CallbackInfo ci) {
        if (MouseTweaks189.press((GuiContainer) (Object) this, x, y, button)) ci.cancel();
    }

    @Inject(method = "mouseClickMove", at = @At("HEAD"))
    private void ladsMouseTweaksDrag(int x, int y, int button, long held, CallbackInfo ci) {
        MouseTweaks189.drag((GuiContainer) (Object) this, x, y, button);
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void ladsMouseTweaksRelease(int x, int y, int button, CallbackInfo ci) {
        if (MouseTweaks189.release((GuiContainer) (Object) this, button)) ci.cancel();
    }

    @Override public Slot ladsSlotAt(int x, int y) { return getSlotAtPosition(x, y); }
    /** Mode 0 is a click (PICKUP), 1 a shift-click (QUICK_MOVE). */
    @Override public void ladsClick(Slot slot, int button, boolean quickMove) { handleMouseClick(slot, slot.slotNumber, button, quickMove ? 1 : 0); }
}
