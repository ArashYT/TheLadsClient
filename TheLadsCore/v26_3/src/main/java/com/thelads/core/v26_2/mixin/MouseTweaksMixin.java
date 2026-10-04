package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeMouseTweaks;
import com.thelads.core.v26_2.gui.CommonInput;
import java.util.List;
import net.minecraft.client.gui.ItemSlotMouseAction;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lads Mouse Tweaks: the container screen's own mouse events go to NativeMouseTweaks first. */
@Mixin(AbstractContainerScreen.class)
public abstract class MouseTweaksMixin extends Screen implements NativeMouseTweaks.Screen {
    @Shadow @Final private List<ItemSlotMouseAction> itemSlotMouseActions;
    @Shadow private Slot getHoveredSlot(double x, double y) { return null; }
    @Shadow protected abstract void slotClicked(Slot slot, int slotId, int button, ContainerInput input);

    private MouseTweaksMixin(Component title) { super(title); }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$mouseTweaksPress(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (NativeMouseTweaks.press((AbstractContainerScreen<?>) (Object) this, event, CommonInput.button(event.button()))) cir.setReturnValue(true);
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), require = 1)
    private void lads$mouseTweaksDrag(MouseButtonEvent event, double dx, double dy, CallbackInfoReturnable<Boolean> cir) {
        NativeMouseTweaks.drag((AbstractContainerScreen<?>) (Object) this, event, CommonInput.button(event.button()));
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$mouseTweaksRelease(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (NativeMouseTweaks.release((AbstractContainerScreen<?>) (Object) this, CommonInput.button(event.button()))) cir.setReturnValue(true);
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$mouseTweaksScroll(double x, double y, double horizontal, double vertical, CallbackInfoReturnable<Boolean> cir) {
        if (NativeMouseTweaks.scroll((AbstractContainerScreen<?>) (Object) this, x, y, vertical)) cir.setReturnValue(true);
    }

    @Override public Slot lads$slotAt(double x, double y) { return getHoveredSlot(x, y); }
    @Override public void lads$click(Slot slot, int button, ContainerInput input) { slotClicked(slot, slot.index, button, input); }
    @Override public boolean lads$itemScrolls(Slot slot) {
        for (ItemSlotMouseAction action : itemSlotMouseActions) if (action.matches(slot)) return true;
        return false;
    }
}
