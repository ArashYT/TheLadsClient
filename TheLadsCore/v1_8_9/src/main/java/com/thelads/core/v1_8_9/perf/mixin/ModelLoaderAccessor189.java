package com.thelads.core.v1_8_9.perf.mixin;

import java.util.Map;
import java.util.Set;
import net.minecraftforge.client.model.ModelLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ModelLoader.class, remap = false)
public interface ModelLoaderAccessor189 {
    @Accessor("stateModels") Map<?, ?> lads$stateModels();
    @Accessor("textures") Set<?> lads$textures();
    @Accessor("missingVariants") Set<?> lads$missingVariants();
    @Accessor("loadingExceptions") Map<?, ?> lads$loadingExceptions();
}
