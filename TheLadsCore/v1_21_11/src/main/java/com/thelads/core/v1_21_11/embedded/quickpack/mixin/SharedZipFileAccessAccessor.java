// Derived from quick-pack 1.4.0 by Drex (commit b80dac1, MIT); see META-INF/lads-sources/quickpack/LICENSE.
package com.thelads.core.v1_21_11.embedded.quickpack.mixin;

import java.util.zip.ZipFile;
import net.minecraft.server.packs.FilePackResources;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FilePackResources.SharedZipFileAccess.class)
public interface SharedZipFileAccessAccessor {
    @Invoker
    ZipFile invokeGetOrCreateZipFile();
}
