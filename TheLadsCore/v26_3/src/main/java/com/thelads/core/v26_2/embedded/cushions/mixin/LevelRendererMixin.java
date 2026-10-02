// Derived from Optimized Cushions 1.0.0 (tag 1.0.0) by NikitaCartes (MIT); see META-INF/lads-sources/cushions/LICENSE.
package com.thelads.core.v26_2.embedded.cushions.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v26_2.embedded.cushions.CushionSectionTasks;

/**
 * Commits deferred baked-flag flips once a rebuilt section mesh is installed.
 *
 * <p>Under Sodium the vanilla {@code RenderSection.setSectionMesh} path is replaced:
 * after uploading each rebuilt section Sodium calls
 * {@code LevelRenderer.removeTransientBlocksInSection} on the main thread, which is
 * exactly when queued {@code commitBakedSection}/{@code commitUnbaked} tasks must run.
 * On the vanilla path this hook is harmless ({@code executeTasks} simply finds an
 * empty queue, since {@code RenderSectionMixin} already consumed it) and keeps a
 * single commit consumer for both renderers.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(method = "removeTransientBlocksInSection", at = @At("RETURN"))
    private void optimizedcushions$commitCushions(final long sectionNode, final long compileTaskStartTimeNs, final CallbackInfo ci) {
        CushionSectionTasks.executeTasks(sectionNode);
    }
}
