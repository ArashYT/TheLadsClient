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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
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
        Slot slot = lads$slotAt(event.x(), event.y());
        if (slot != null && com.thelads.core.client.SlotLocking.enabled() && (minecraft.hasAltDown() || com.mojang.blaze3d.platform.InputConstants.isKeyDown(76))) {
            com.thelads.core.client.SlotLocking.toggleLock(slot.index);
            cir.setReturnValue(true);
            return;
        }
        if (event.button() == 2 && slot != null && slot.hasItem() && com.thelads.core.client.InventorySort.isArmorSwapEnabled()) {
            net.minecraft.world.item.ItemStack stack = slot.getItem();
            net.minecraft.world.item.equipment.Equippable eq = stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE);
            if (eq != null) {
                int armorSlotId = -1;
                if (eq.slot() == net.minecraft.world.entity.EquipmentSlot.HEAD) armorSlotId = 5;
                else if (eq.slot() == net.minecraft.world.entity.EquipmentSlot.CHEST) armorSlotId = 6;
                else if (eq.slot() == net.minecraft.world.entity.EquipmentSlot.LEGS) armorSlotId = 7;
                else if (eq.slot() == net.minecraft.world.entity.EquipmentSlot.FEET) armorSlotId = 8;
                if (armorSlotId != -1) {
                    slotClicked(slot, slot.index, 0, ContainerInput.PICKUP);
                    Slot target = ((AbstractContainerScreen<?>) (Object) this).getMenu().getSlot(armorSlotId);
                    if (target != null) slotClicked(target, target.index, 0, ContainerInput.PICKUP);
                    slotClicked(slot, slot.index, 0, ContainerInput.PICKUP);
                    cir.setReturnValue(true);
                    return;
                }
            }
        }
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

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void lads$slotDropLock(net.minecraft.client.input.KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (minecraft != null && minecraft.options.keyDrop.matches(event)) {
            Slot slot = lads$slotAt(minecraft.mouseHandler.getScaledXPos(minecraft.getWindow()),
                minecraft.mouseHandler.getScaledYPos(minecraft.getWindow()));
            if (slot != null && !com.thelads.core.client.SlotLocking.canDropSlot(slot.index)) {
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "extractSlot", at = @At("TAIL"))
    private void lads$renderSlotOverlay(net.minecraft.client.gui.GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
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
