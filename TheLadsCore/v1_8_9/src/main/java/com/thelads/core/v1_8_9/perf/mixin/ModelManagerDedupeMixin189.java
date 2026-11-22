package com.thelads.core.v1_8_9.perf.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thelads.core.v1_8_9.perf.LoadPerf189;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.util.IRegistry;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FoamFix-style cleanup: Forge keeps the whole unbaked model set (every ModelBlock, blockstate definition and IModel wrapper)
 * alive after baking, through ModelLoader's VanillaLoader and ModelLoaderRegistry's cache, until the next resource reload.
 * Once the bake event is over nothing needs them (Forge reloads any model asked for later from the resources), so drop them.
 * While the bake runs, quads with equal vertex numbers share their arrays (LoadPerf189.intern).
 * Kill switch: -Dlads.dedupe189=false.
 */
@Mixin(ModelManager.class)
public abstract class ModelManagerDedupeMixin189 {
    @Unique private ModelBakery lads$bakery;

    @WrapOperation(method = "onResourceManagerReload", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/resources/model/ModelBakery;setupModelRegistry()Lnet/minecraft/util/IRegistry;"))
    private IRegistry lads$capture(ModelBakery bakery, Operation<IRegistry> original) {
        lads$bakery = bakery;
        LoadPerf189.internStart();
        try {
            return original.call(bakery);
        } finally {
            LoadPerf189.LOG.info("[Lads] model bake: {}", LoadPerf189.internEnd());
        }
    }

    @Inject(method = "onResourceManagerReload", at = @At("RETURN"))
    private void lads$dropUnbaked(IResourceManager manager, CallbackInfo ci) {
        ModelBakery bakery = lads$bakery;
        lads$bakery = null;
        if (bakery == null) return;
        try {
            int models = ((ModelBakeryAccessor189) bakery).lads$models().size();
            ((ModelBakeryAccessor189) bakery).lads$models().clear();
            ((ModelBakeryAccessor189) bakery).lads$variants().clear();
            ((ModelBakeryAccessor189) bakery).lads$blockDefinitions().clear();
            int states = 0;
            if (bakery instanceof ModelLoader) {
                ModelLoaderAccessor189 loader = (ModelLoaderAccessor189) bakery;
                states = loader.lads$stateModels().size();
                loader.lads$stateModels().clear();
                loader.lads$textures().clear();
                loader.lads$missingVariants().clear();
                loader.lads$loadingExceptions().clear();
                ModelLoaderRegistry.clearModelCache();
            }
            LoadPerf189.LOG.info("[Lads] dropped {} unbaked models and {} blockstate models after baking", models, states);
        } catch (Throwable failed) {
            LoadPerf189.LOG.warn("[Lads] could not drop the unbaked models", failed);
        }
    }
}
