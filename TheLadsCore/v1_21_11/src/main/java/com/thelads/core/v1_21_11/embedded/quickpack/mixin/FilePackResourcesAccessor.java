// Derived from quick-pack 1.4.0 by Drex (commit b80dac1, MIT); see META-INF/lads-sources/quickpack/LICENSE.
package com.thelads.core.v1_21_11.embedded.quickpack.mixin;

import net.minecraft.server.packs.FilePackResources;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FilePackResources.class)
public interface FilePackResourcesAccessor {
    @Accessor
    static Logger getLOGGER() {
        throw new AssertionError();
    }

    @Accessor
    String getPrefix();
}
