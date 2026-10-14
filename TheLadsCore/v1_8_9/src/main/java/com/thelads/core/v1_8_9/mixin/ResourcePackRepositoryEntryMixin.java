package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.client.pack.PackFolderHelper;
import java.io.File;
import net.minecraft.client.resources.ResourcePackRepository;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Returns relative path for packs inside subfolders of resourcepacks/ so they survive restart in options.txt,
 * and caches both the relative pack name and {@code toString()} key to eliminate per-frame filesystem stats
 * and {@code String.format} allocations.
 */
@Mixin(ResourcePackRepository.Entry.class)
public abstract class ResourcePackRepositoryEntryMixin {
    @Shadow @Final private File resourcePackFile;

    @Unique private String lads$cachedRelativeName;
    @Unique private boolean lads$relativeNameResolved;
    @Unique private String lads$cachedToStringKey;

    @Inject(method = "getResourcePackName", at = @At("HEAD"), cancellable = true)
    private void ladsRelativePackName(CallbackInfoReturnable<String> cir) {
        if (resourcePackFile == null) return;
        if (!lads$relativeNameResolved) {
            String rel = PackFolderHelper.getRelativePackPath(resourcePackFile);
            if (!rel.isEmpty() && !rel.equals(resourcePackFile.getName())) {
                lads$cachedRelativeName = rel;
            }
            lads$relativeNameResolved = true;
        }
        if (lads$cachedRelativeName != null) {
            cir.setReturnValue(lads$cachedRelativeName);
        }
    }

    @Inject(method = "toString", at = @At("HEAD"), cancellable = true)
    private void ladsCachedToString(CallbackInfoReturnable<String> cir) {
        if (resourcePackFile == null) return;
        String key = lads$cachedToStringKey;
        if (key == null) {
            String name = lads$relativeNameResolved && lads$cachedRelativeName != null
                ? lads$cachedRelativeName
                : resourcePackFile.getName();
            key = name + ":" + (resourcePackFile.isDirectory() ? "folder" : "zip") + ":" + resourcePackFile.lastModified();
            lads$cachedToStringKey = key;
        }
        cir.setReturnValue(key);
    }
}
