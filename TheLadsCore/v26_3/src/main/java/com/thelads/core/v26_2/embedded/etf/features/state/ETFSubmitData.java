package com.thelads.core.v26_2.embedded.etf.features.state;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public class ETFSubmitData {

    public ETFEntityRenderState backupState = ETFState.state();

    public final Map<String, Object> data = new HashMap<>();




    public static final List<BiConsumer<ETFSubmitData,
                net.minecraft.client.renderer.feature.ModelFeatureRenderer.Submit
                >> DATA_IN = new ArrayList<>();

    public static final List<BiConsumer<ETFSubmitData,
            net.minecraft.client.renderer.feature.ModelFeatureRenderer.Submit
            >> DATA_OUT = new ArrayList<>();

    @Nullable
    public static ETFSubmitData from(
            net.minecraft.client.renderer.feature.ModelFeatureRenderer.Submit<?> modelSubmit
    ) {
        //noinspection ConstantValue
        return ((Object) modelSubmit) instanceof ETFSubmitExtension emf
                ? ((ETFSubmitExtension) (Object) modelSubmit).emf$getData()
                : null;
    }

}
