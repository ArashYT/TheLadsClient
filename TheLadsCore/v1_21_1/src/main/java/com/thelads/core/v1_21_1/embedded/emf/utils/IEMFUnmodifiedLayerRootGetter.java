package com.thelads.core.v1_21_1.embedded.emf.utils;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;

import java.util.Map;

public interface IEMFUnmodifiedLayerRootGetter {
    Map<ModelLayerLocation, LayerDefinition> emf$getUnmodifiedRoots();

}
