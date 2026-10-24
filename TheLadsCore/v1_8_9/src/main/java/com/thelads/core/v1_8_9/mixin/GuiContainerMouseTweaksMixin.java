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

    /** As later versions: any button but left, right and creative pick-block swaps the hovered slot with the hotbar slot bound to it. */
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void ladsHotbarButton(int x, int y, int button, CallbackInfo ci) {
        Slot slot = getSlotAtPosition(x, y);
        if (slot != null && com.thelads.core.client.SlotLocking.enabled() && (org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_LMENU) || org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_RMENU) || org.lwjgl.input.Keyboard.isKeyDown(org.lwjgl.input.Keyboard.KEY_L))) {
            com.thelads.core.client.SlotLocking.toggleLock(slot.slotNumber);
            ci.cancel();
            return;
        }
        if (button == 2 && slot != null && slot.getHasStack() && com.thelads.core.client.InventorySort.isArmorSwapEnabled()) {
            net.minecraft.item.Item item = slot.getStack().getItem();
            if (item instanceof net.minecraft.item.ItemArmor) {
                net.minecraft.item.ItemArmor armor = (net.minecraft.item.ItemArmor) item;
                int armorSlotId = 5 + (3 - armor.armorType);
                handleMouseClick(slot, slot.slotNumber, 0, 0);
                Slot target = ((GuiContainer) (Object) this).inventorySlots.getSlot(armorSlotId);
                if (target != null) handleMouseClick(target, target.slotNumber, 0, 0);
                handleMouseClick(slot, slot.slotNumber, 0, 0);
                ci.cancel();
                return;
            }
        }
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

    @Inject(method = "drawSlot", at = @At("TAIL"))
    private void ladsDrawSlotFeatures(Slot slot, CallbackInfo ci) {
        if (slot == null) return;
        if (com.thelads.core.client.SlotLocking.isSlotLocked(slot.slotNumber)) {
            drawRect(slot.xDisplayPosition, slot.yDisplayPosition, slot.xDisplayPosition + 16, slot.yDisplayPosition + 16, 0x40FF0000);
        }
        if (slot.getHasStack() && com.thelads.core.client.InventorySearch.enabled()) {
            if (com.thelads.core.client.InventorySearch.shouldDim(slot.getStack().getDisplayName(), null)) {
                drawRect(slot.xDisplayPosition, slot.yDisplayPosition, slot.xDisplayPosition + 16, slot.yDisplayPosition + 16, 0xAA000000);
            }
        }
    }

    @Inject(method = "keyTyped", at = @At("HEAD"), cancellable = true)
    private void ladsContainerKeyTyped(char typedChar, int keyCode, CallbackInfo ci) {
        if (keyCode == mc.gameSettings.keyBindDrop.getKeyCode()) {
            Slot slot = getSlotAtPosition(org.lwjgl.input.Mouse.getX() * width / mc.displayWidth, height - org.lwjgl.input.Mouse.getY() * height / mc.displayHeight - 1);
            if (slot != null && !com.thelads.core.client.SlotLocking.canDropSlot(slot.slotNumber)) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "updateScreen", at = @At("TAIL"))
    private void ladsInventoryMoveTick(CallbackInfo ci) {
        if (com.thelads.core.client.InventoryMove.isMovementAllowed() && mc.thePlayer != null) {
            net.minecraft.client.settings.GameSettings gs = mc.gameSettings;
            mc.thePlayer.movementInput.moveForward = 0.0F;
            mc.thePlayer.movementInput.moveStrafe = 0.0F;
            if (org.lwjgl.input.Keyboard.isKeyDown(gs.keyBindForward.getKeyCode())) mc.thePlayer.movementInput.moveForward += 1.0F;
            if (org.lwjgl.input.Keyboard.isKeyDown(gs.keyBindBack.getKeyCode())) mc.thePlayer.movementInput.moveForward -= 1.0F;
            if (org.lwjgl.input.Keyboard.isKeyDown(gs.keyBindLeft.getKeyCode())) mc.thePlayer.movementInput.moveStrafe += 1.0F;
            if (org.lwjgl.input.Keyboard.isKeyDown(gs.keyBindRight.getKeyCode())) mc.thePlayer.movementInput.moveStrafe -= 1.0F;
            mc.thePlayer.movementInput.jump = org.lwjgl.input.Keyboard.isKeyDown(gs.keyBindJump.getKeyCode());
            mc.thePlayer.movementInput.sneak = org.lwjgl.input.Keyboard.isKeyDown(gs.keyBindSneak.getKeyCode());
        }
    }
}
