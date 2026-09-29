// Adapted from Raised 6.0.0 source by yurisuika, LGPL-3.0-or-later.
// Pinned source 4b6a3b8718316d681c3ae6832cdd19c3174a29a4; see META-INF/lads-sources/raised.
package com.thelads.core.v26_2.feature.raised.mixin.minecraft.server.packs.repository;

import com.thelads.core.v26_2.feature.raised.util.Icon;
import com.thelads.core.v26_2.feature.raised.util.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PackRepository.class)
public abstract class PackRepositoryMixin {

    /**
     * Checks if resource pack support is present.
     */
    @Inject(method = "reload", at = @At("TAIL"))
    private void checkPacks(CallbackInfo ci) {
        Pack.checkResources();
        Icon.checkResources();
    }

}