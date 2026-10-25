package com.thelads.core.v26_2.mixin;

import com.thelads.core.v26_2.social.FriendsScreen26;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftFriendsMixin {
    @Inject(method = "toggleFriendsScreen()Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void ladsToggleFriendsScreen(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = (Minecraft) (Object) this;
        if (mc.gui != null && mc.gui.screen() instanceof FriendsScreen26) {
            mc.gui.screen().onClose();
            cir.setReturnValue(true);
            return;
        }
        mc.gui.setScreen(new FriendsScreen26(mc.gui != null ? mc.gui.screen() : null));
        cir.setReturnValue(true);
    }
}
