// Derived from quick-pack 1.4.0 by Drex (commit b80dac1, MIT); see META-INF/lads-sources/quickpack/LICENSE.
// Modified by The Lads: ported to Minecraft 1.20.1 from the Lads 26.3 embedded copy. 1.20.1's FilePackResources opens its zip
// itself (no shared zip access, no overlays), so the index is built on the first lookup instead of when the pack is opened.
package com.thelads.core.v1_20_1.embedded.quickpack.mixin;

import com.thelads.core.v1_20_1.embedded.quickpack.QuickPack;
import java.util.Set;
import java.util.zip.ZipFile;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FilePackResources.class)
public abstract class FilePackResourcesMixin {
    @Shadow
    @Nullable
    protected abstract ZipFile getOrCreateZipFile();

    @Unique
    private volatile QuickPack.Index quick_pack$index;

    /** The index of this pack's zip, read on first use; null when the zip cannot be opened (vanilla then answers, as before). */
    @Unique
    @Nullable
    private QuickPack.Index quick_pack$index(ZipFile zip) {
        QuickPack.Index index = this.quick_pack$index;
        if (index == null) {
            synchronized (this) {
                index = this.quick_pack$index;
                if (index == null) {
                    index = QuickPack.build(zip);
                    this.quick_pack$index = index;
                }
            }
        }
        return index;
    }

    @Inject(method = "getNamespaces", at = @At("HEAD"), cancellable = true)
    public void fastGetNamespaces(PackType packType, CallbackInfoReturnable<Set<String>> cir) {
        ZipFile zip = this.getOrCreateZipFile();
        if (zip == null) return;
        cir.setReturnValue(QuickPack.namespaces(this.quick_pack$index(zip), packType));
    }

    @Inject(method = "listResources", at = @At("HEAD"), cancellable = true)
    public void fastListResources(PackType packType, String namespace, String path, PackResources.ResourceOutput output, CallbackInfo ci) {
        ZipFile zip = this.getOrCreateZipFile();
        if (zip == null) return;
        QuickPack.list(this.quick_pack$index(zip), zip, packType, namespace, path, output);
        ci.cancel();
    }

    @Inject(method = "close", at = @At("HEAD"))
    public void onClose(CallbackInfo ci) {
        this.quick_pack$index = null;
    }
}
