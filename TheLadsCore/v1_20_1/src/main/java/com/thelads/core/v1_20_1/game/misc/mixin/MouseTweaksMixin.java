package com.thelads.core.v1_20_1.game.misc.mixin;

import com.thelads.core.v1_20_1.game.misc.NativeMouseTweaks1201;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lads Mouse Tweaks: the container screen's own mouse events go to NativeMouseTweaks1201 first. */
@Mixin(AbstractContainerScreen.class)
public abstract class MouseTweaksMixin extends Screen implements NativeMouseTweaks1201.Screen1201 {
    @Shadow private Slot findSlot(double x, double y) { return null; }
    @Shadow protected abstract void slotClicked(Slot slot, int slotId, int button, ClickType type);

    private MouseTweaksMixin(Component title) { super(title); }

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$mouseTweaksPress(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        if (NativeMouseTweaks1201.press((AbstractContainerScreen<?>) (Object) this, x, y, button)) cir.setReturnValue(true);
    }

    @Inject(method = "mouseDragged(DDIDD)Z", at = @At("HEAD"), require = 1)
    private void lads$mouseTweaksDrag(double x, double y, int button, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        NativeMouseTweaks1201.drag((AbstractContainerScreen<?>) (Object) this, x, y, button);
    }

    @Inject(method = "mouseReleased(DDI)Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$mouseTweaksRelease(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        if (NativeMouseTweaks1201.release((AbstractContainerScreen<?>) (Object) this, button)) cir.setReturnValue(true);
    }

    @Override public Slot lads$slotAt(double x, double y) { return findSlot(x, y); }
    @Override public void lads$click(Slot slot, int button, ClickType type) { slotClicked(slot, slot.index, button, type); }
}
