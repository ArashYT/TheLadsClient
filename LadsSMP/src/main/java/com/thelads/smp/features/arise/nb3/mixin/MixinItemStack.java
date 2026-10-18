package com.thelads.smp.features.arise.nb3.mixin;

import com.thelads.smp.features.arise.nb3.obscuretooltips.ObscureTooltipComponent;
import com.thelads.smp.features.arise.nb3.obscuretooltips.ObscureTooltipsFeature;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/**
 * Mixin into ItemStack to append ObscureTooltipComponent for weapons, armor, and tools.
 */
@Mixin(ItemStack.class)
public abstract class MixinItemStack {

    @Inject(method = "getTooltipImage", at = @At("RETURN"), cancellable = true)
    private void ladssmp$appendObscureTooltip(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
        if (cir.getReturnValue().isEmpty()) {
            ItemStack self = (ItemStack) (Object) this;
            if (ObscureTooltipsFeature.shouldShowPreview(self)) {
                cir.setReturnValue(Optional.of(new ObscureTooltipComponent(self)));
            }
        }
    }
}
