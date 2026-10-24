package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeFeatures;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin extends ClientInput {
    @Inject(method = "tick()V", at = @At("TAIL"), require = 1)
    private void ladsMovement(CallbackInfo ci) {
        if (com.thelads.core.client.InventoryMove.isMovementAllowed()) {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) {
                long window = mc.getWindow().handle();
                var opt = mc.options;
                boolean up = isKeyDown(opt.keyUp);
                boolean down = isKeyDown(opt.keyDown);
                boolean left = isKeyDown(opt.keyLeft);
                boolean right = isKeyDown(opt.keyRight);
                boolean jump = isKeyDown(opt.keyJump);
                boolean shift = isKeyDown(opt.keyShift);
                boolean sprint = isKeyDown(opt.keySprint);
                keyPresses = new net.minecraft.world.entity.player.Input(up, down, left, right, jump, shift, sprint);
                float f = (up == down ? 0.0F : (up ? 1.0F : -1.0F));
                float s = (left == right ? 0.0F : (left ? 1.0F : -1.0F));
                moveVector = new net.minecraft.world.phys.Vec2(s, f).normalized();
            }
        }
        keyPresses = NativeFeatures.movement(keyPresses);
    }

    @org.spongepowered.asm.mixin.Unique
    private static boolean isKeyDown(net.minecraft.client.KeyMapping mapping) {
        com.mojang.blaze3d.platform.InputConstants.Key key = ((com.thelads.core.v26_2.embedded.controlling.mixin.AccessKeyMapping) mapping).controlling$getKey();
        return key != null && key.getValue() != com.mojang.blaze3d.platform.InputConstants.UNKNOWN.getValue()
            && com.mojang.blaze3d.platform.InputConstants.isKeyDown(key.getValue());
    }
}
