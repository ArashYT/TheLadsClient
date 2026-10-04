package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.feature.NativeItemPhysics;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Item Physics' right-click pickup: the use key on a dropped item picks it up instead of using the held item. */
@Mixin(Minecraft.class)
public class ItemPhysicsInputMixin {
    @Shadow private int rightClickDelay;

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true, require = 1)
    private void lads$rightClickPickup(CallbackInfo callback) {
        if (!NativeItemPhysics.pickUp((Minecraft) (Object) this)) return;
        rightClickDelay = 4;
        callback.cancel();
    }
}
