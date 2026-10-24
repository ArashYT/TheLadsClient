package com.thelads.core.v1_20_1.game.misc.mixin;

import com.thelads.core.v1_20_1.game.misc.NativeMouseTweaks1201;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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
        Slot slot = findSlot(x, y);
        if (slot != null && com.thelads.core.client.SlotLocking.enabled() && (hasAltDown() || com.mojang.blaze3d.platform.InputConstants.isKeyDown(minecraft.getWindow().getWindow(), 76))) {
            com.thelads.core.client.SlotLocking.toggleLock(slot.index);
            cir.setReturnValue(true);
            return;
        }
        if (button == 2 && slot != null && slot.hasItem() && com.thelads.core.client.InventorySort.isArmorSwapEnabled()) {
            net.minecraft.world.item.ItemStack stack = slot.getItem();
            if (stack.getItem() instanceof net.minecraft.world.item.ArmorItem armor) {
                int armorSlotId = 5 + (3 - armor.getType().ordinal());
                slotClicked(slot, slot.index, 0, ClickType.PICKUP);
                Slot target = ((AbstractContainerScreen<?>) (Object) this).getMenu().getSlot(armorSlotId);
                if (target != null) slotClicked(target, target.index, 0, ClickType.PICKUP);
                slotClicked(slot, slot.index, 0, ClickType.PICKUP);
                cir.setReturnValue(true);
                return;
            }
        }
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

    @Inject(method = "keyPressed(III)Z", at = @At("HEAD"), cancellable = true)
    private void lads$slotDropLock(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (minecraft.options.keyDrop.matches(keyCode, scanCode)) {
            Slot slot = lads$slotAt(minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth(),
                minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight());
            if (slot != null && !com.thelads.core.client.SlotLocking.canDropSlot(slot.index)) {
                cir.setReturnValue(true);
            }
        }
    }

    @Unique
    private static boolean lads$isKeyDown(long window, net.minecraft.client.KeyMapping mapping) {
        com.mojang.blaze3d.platform.InputConstants.Key key = ((com.thelads.core.v1_20_1.embedded.controlling.mixin.AccessKeyMapping) mapping).controlling$getKey();
        return key != null && key.getValue() != com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue()
            && com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, key.getValue());
    }

    @Inject(method = "containerTick()V", at = @At("TAIL"))
    private void lads$invMoveTick(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (com.thelads.core.client.InventoryMove.isMovementAllowed() && minecraft.player != null) {
            long window = minecraft.getWindow().getWindow();
            net.minecraft.client.Options opt = minecraft.options;
            minecraft.player.input.forwardImpulse = 0.0F;
            minecraft.player.input.leftImpulse = 0.0F;
            if (lads$isKeyDown(window, opt.keyUp)) minecraft.player.input.forwardImpulse += 1.0F;
            if (lads$isKeyDown(window, opt.keyDown)) minecraft.player.input.forwardImpulse -= 1.0F;
            if (lads$isKeyDown(window, opt.keyLeft)) minecraft.player.input.leftImpulse += 1.0F;
            if (lads$isKeyDown(window, opt.keyRight)) minecraft.player.input.leftImpulse -= 1.0F;
            minecraft.player.input.jumping = lads$isKeyDown(window, opt.keyJump);
            minecraft.player.input.shiftKeyDown = lads$isKeyDown(window, opt.keyShift);
        }
    }

    @Inject(method = "renderSlot", at = @At("TAIL"))
    private void lads$renderSlotOverlay(net.minecraft.client.gui.GuiGraphics graphics, Slot slot, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (slot == null) return;
        if (com.thelads.core.client.SlotLocking.isSlotLocked(slot.index)) {
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x40FF0000);
        }
        if (slot.hasItem() && com.thelads.core.client.InventorySearch.enabled()) {
            if (com.thelads.core.client.InventorySearch.shouldDim(slot.getItem().getHoverName().getString(), null)) {
                graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xAA000000);
            }
        }
    }
}
