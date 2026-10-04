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

/** Lads Mouse Tweaks: the container screen's own mouse handlers go to MouseTweaks189 first. */
@Mixin(GuiContainer.class)
public abstract class GuiContainerMouseTweaksMixin extends GuiScreen implements MouseTweaks189.Screen {
    @Shadow private Slot getSlotAtPosition(int x, int y) { return null; }
    @Shadow protected abstract void handleMouseClick(Slot slot, int slotId, int button, int mode);

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
