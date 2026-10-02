package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.LunarPacks189;
import java.io.File;
import java.util.List;
import net.minecraft.client.resources.ResourcePackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lunar Client's 1.8 resource packs in the pack list (LunarPacks189). */
@Mixin(ResourcePackRepository.class)
public class ResourcePackRepositoryMixin {
    @Inject(method = "getResourcePackFiles", at = @At("RETURN"), cancellable = true, require = 1)
    private void ladsLunarPacks(CallbackInfoReturnable<List<File>> callback) {
        callback.setReturnValue(LunarPacks189.withLunar(callback.getReturnValue()));
    }
}
