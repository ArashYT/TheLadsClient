package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.pack.PackFolderHelper;
import java.io.File;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.ResourcePackRepository;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Returns relative path for packs inside subfolders of resourcepacks/ so they survive restart in options.txt.
 */
@Mixin(ResourcePackRepository.Entry.class)
public abstract class ResourcePackRepositoryEntryMixin {
    @Shadow @Final private File resourcePackFile;

    @Inject(method = "getResourcePackName", at = @At("HEAD"), cancellable = true)
    private void ladsRelativePackName(CallbackInfoReturnable<String> cir) {
        if (resourcePackFile == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        ResourcePackRepository repo = mc.getResourcePackRepository();
        if (repo == null) return;
        File root = repo.getDirResourcepacks();
        if (root != null && root.isDirectory()) {
            String rel = PackFolderHelper.getRelativePath(root.toPath(), resourcePackFile.toPath());
            if (!rel.isEmpty() && !rel.equals(resourcePackFile.getName())) {
                cir.setReturnValue(rel);
            }
        }
    }
}
