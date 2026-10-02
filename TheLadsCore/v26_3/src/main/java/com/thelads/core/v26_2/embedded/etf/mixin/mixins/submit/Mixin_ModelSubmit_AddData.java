package com.thelads.core.v26_2.embedded.etf.mixin.mixins.submit;

import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFSubmitData;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFSubmitExtension;

@Mixin(net.minecraft.client.renderer.feature.ModelFeatureRenderer.Submit.class)
public abstract class Mixin_ModelSubmit_AddData implements ETFSubmitExtension {

    @Unique private final @Nullable ETFSubmitData data = new ETFSubmitData();

    @Override
    public @Nullable ETFSubmitData emf$getData() {
        return data;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void emf$initBackupState(CallbackInfo ci) {
        if (data != null) {
            ETFSubmitData.DATA_IN.forEach(entry -> entry.accept(data, (
                    net.minecraft.client.renderer.feature.ModelFeatureRenderer.Submit
                    ) (Object) this));
        }
    }

}
