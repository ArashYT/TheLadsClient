package com.thelads.core.v1_20_1.game.mixin;

import com.thelads.core.client.HotbarScrollHelper;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.ScrollTweaksModule;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
public abstract class InventoryMixin {
    @Shadow public int selected;

    @Inject(method = "swapPaint(D)V", at = @At("HEAD"), cancellable = true)
    private void lads$scrollHotbar(double direction, CallbackInfo ci) {
        Module m = ModuleManager.getInstance().getModule(ScrollTweaksModule.NAME);
        if (m instanceof ScrollTweaksModule stm && stm.isEnabled()) {
            int dir = (int) Math.signum(direction);
            this.selected = HotbarScrollHelper.computeTargetSlot(this.selected, dir, stm.isScrollInverted(), stm.isNoWrap());
            ci.cancel();
        }
    }
}
