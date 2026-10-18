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

    /** 1.9.12 perf: made only when something can read it (an ETF state is mounted, or packs give ETF/EMF data): before, every model
     *  submit of every entity allocated one with its HashMap every frame. */
    @Unique private @Nullable ETFSubmitData data;

    @Override
    public @Nullable ETFSubmitData emf$getData() {
        return data;
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void emf$initBackupState(CallbackInfo ci) {
        if (!com.thelads.core.v26_2.embedded.etf.features.ETFManager.packData
                && com.thelads.core.v26_2.embedded.etf.features.state.ETFState.state() == null) return;
        data = new ETFSubmitData();
        ETFSubmitData.DATA_IN.forEach(entry -> entry.accept(data, (
                net.minecraft.client.renderer.feature.ModelFeatureRenderer.Submit
                ) (Object) this));
    }

}
