package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.pack.PackMenuSettings;
import net.minecraft.client.resources.ResourcePackListEntryFound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * "No pack compat check" (on by default): a pack made for another version reports 1.8.9's pack format, so the pack menu draws no red
 * "incompatible" label and asks no confirmation before selecting it.
 */
@Mixin(ResourcePackListEntryFound.class)
public abstract class ResourcePackListEntryFoundMixin {
    @Inject(method = "func_183019_a", at = @At("HEAD"), cancellable = true)
    private void lads$compatible(CallbackInfoReturnable<Integer> cir) {
        if (PackMenuSettings.noCompatCheck()) cir.setReturnValue(1);
    }
}
