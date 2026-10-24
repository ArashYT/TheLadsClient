package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.HotbarScrollHelper;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.ScrollTweaksModule;
import net.minecraft.entity.player.InventoryPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryPlayer.class)
public abstract class InventoryPlayerMixin {
    @Shadow public int currentItem;

    @Inject(method = "changeCurrentItem(I)V", at = @At("HEAD"), cancellable = true)
    private void lads$scrollHotbar(int direction, CallbackInfo ci) {
        Module m = ModuleManager.getInstance().getModule(ScrollTweaksModule.NAME);
        if (m instanceof ScrollTweaksModule && m.isEnabled()) {
            ScrollTweaksModule stm = (ScrollTweaksModule) m;
            this.currentItem = HotbarScrollHelper.computeTargetSlot(this.currentItem, direction, stm.isScrollInverted(), stm.isNoWrap());
            ci.cancel();
        }
    }
}
