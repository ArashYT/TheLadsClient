// Derived from Optimized Cushions Backport 1.0.0 (commit a6de9cc) by NikitaCartes (MIT); see META-INF/lads-sources/cushions/LICENSE.
package com.thelads.core.v1_21_11.embedded.cushions.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_11.embedded.cushions.CushionSectionTasks;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(method = "removeTransientBlocksInSection", at = @At("RETURN"), require = 0)
    private void optimizedcushions$commitCushions(final long sectionNode, final long compileTaskStartTimeNs, final CallbackInfo ci) {
        CushionSectionTasks.executeTasks(sectionNode);
    }
}
