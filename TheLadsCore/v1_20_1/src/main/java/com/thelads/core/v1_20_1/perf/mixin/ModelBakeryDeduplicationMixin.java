package com.thelads.core.v1_20_1.perf.mixin;

import com.thelads.core.v1_20_1.perf.ModelDeduplicator1201;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.FaceBakery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FaceBakery.class)
public abstract class ModelBakeryDeduplicationMixin {

    @Inject(method = "bakeQuad", at = @At("RETURN"), cancellable = true, require = 0)
    private void lads$canonicalizeBakedQuad(CallbackInfoReturnable<BakedQuad> cir) {
        BakedQuad quad = cir.getReturnValue();
        if (quad != null) {
            cir.setReturnValue(ModelDeduplicator1201.canonicalizeQuad(quad));
        }
    }
}
