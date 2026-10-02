// Derived from quick-pack 1.4.0 by Drex (commit b80dac1, MIT); see META-INF/lads-sources/quickpack/LICENSE.
package com.thelads.core.v1_21_11.embedded.quickpack.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.thelads.core.v1_21_11.embedded.quickpack.QuickPack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FilePackResources.FileResourcesSupplier.class)
public abstract class FileResourcesSupplierMixin {
    @Inject(method = "openFull", at = @At(value = "RETURN", ordinal = 0))
    public void initializeFileTree(
        PackLocationInfo location, Pack.Metadata metadata, CallbackInfoReturnable<PackResources> cir,
        @Local(index = 4) PackResources primary,
        @Local(index = 3) FilePackResources.SharedZipFileAccess zipFileAccess
    ) {
        QuickPack.initializeFileTrees(zipFileAccess, List.of(primary));
    }

    @Inject(method = "openFull", at = @At(value = "RETURN", ordinal = 1))
    public void initializeFileTrees(
        PackLocationInfo location, Pack.Metadata metadata, CallbackInfoReturnable<PackResources> cir,
        @Local(index = 4) PackResources primary,
        @Local(index = 6) List<PackResources> overlayResources,
        @Local(index = 3) FilePackResources.SharedZipFileAccess zipFileAccess
    ) {
        List<PackResources> packList = new ArrayList<>(overlayResources.size() + 1);
        packList.add(primary);
        packList.addAll(overlayResources);
        QuickPack.initializeFileTrees(zipFileAccess, packList);
    }
}
