package com.thelads.core.v1_21_11.embedded.etf.features.state;

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
                net.minecraft.client.renderer.SubmitNodeStorage.ModelSubmit
                >> DATA_IN = new ArrayList<>();

    public static final List<BiConsumer<ETFSubmitData,
            net.minecraft.client.renderer.SubmitNodeStorage.ModelSubmit
            >> DATA_OUT = new ArrayList<>();

    @Nullable
    public static ETFSubmitData from(
            net.minecraft.client.renderer.SubmitNodeStorage.ModelSubmit<?> modelSubmit
    ) {
        //noinspection ConstantValue
        return ((Object) modelSubmit) instanceof ETFSubmitExtension emf
                ? ((ETFSubmitExtension) (Object) modelSubmit).emf$getData()
                : null;
    }

}
