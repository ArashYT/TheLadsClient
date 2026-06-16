package com.thelads.core.features.alwayson.skinlayers.api;

import net.minecraft.client.model.geom.ModelPart;
import org.jetbrains.annotations.Nullable;

public interface MeshTransformerProvider {
    MeshTransformerProvider EMPTY_PROVIDER = cube -> MeshTransformer.EMPTY_TRANSFORMER;

    MeshTransformer prepareTransformer(@Nullable ModelPart vanillaModel);
}
