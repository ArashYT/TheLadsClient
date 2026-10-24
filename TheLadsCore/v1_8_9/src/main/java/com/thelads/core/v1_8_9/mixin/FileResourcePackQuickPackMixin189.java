package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.quickpack.QuickPack189;
import java.io.IOException;
import java.util.Set;
import java.util.zip.ZipFile;
import net.minecraft.client.resources.FileResourcePack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * QuickPack on 1.8.9: eliminates repeated ZipFile.entries() scans across all resource domains
 * and replaces repeated native getEntry() queries with O(1) in-memory index queries.
 */
@Mixin(FileResourcePack.class)
public abstract class FileResourcePackQuickPackMixin189 {
    @Shadow private ZipFile resourcePackZipFile;
    @Shadow protected abstract ZipFile getResourcePackZipFile() throws IOException;

    @Inject(method = "getResourceDomains", at = @At("HEAD"), cancellable = true)
    private void ladsQuickPackDomains(CallbackInfoReturnable<Set<String>> cir) {
        try {
            ZipFile zip = getResourcePackZipFile();
            if (zip != null) {
                com.thelads.core.perf.ZipPackIndex index =
                    QuickPack189.getOrBuild((FileResourcePack) (Object) this, zip);
                if (index != null) {
                    cir.setReturnValue(index.domains());
                }
            }
        } catch (IOException ignored) {}
    }

    @Inject(method = "hasResourceName", at = @At("HEAD"), cancellable = true)
    private void ladsQuickPackHasResource(String name, CallbackInfoReturnable<Boolean> cir) {
        Boolean has = QuickPack189.hasEntry((FileResourcePack) (Object) this, name);
        if (has != null) {
            cir.setReturnValue(has);
        }
    }

    @Inject(method = "close", at = @At("RETURN"))
    private void ladsQuickPackOnClose(CallbackInfo ci) {
        QuickPack189.onClose((FileResourcePack) (Object) this);
    }
}
