// Derived from Optimized Cushions Backport 1.0.0 (commit a6de9cc) by NikitaCartes (MIT); see META-INF/lads-sources/cushions/LICENSE.
package com.thelads.core.v1_21_1.embedded.cushions.mixin;

import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_1.embedded.cushions.CushionSectionTasks;

@Mixin(SectionRenderDispatcher.RenderSection.class)
public class RenderSectionMixin {
    @Inject(method = "setCompiled", at = @At("RETURN"))
    private void optimizedcushions$commitCushions(final CallbackInfo ci) {
        SectionRenderDispatcher.RenderSection self = (SectionRenderDispatcher.RenderSection) (Object) this;
        BlockPos origin = self.getOrigin();
        CushionSectionTasks.executeTasks(SectionPos.asLong(origin));
    }
}
