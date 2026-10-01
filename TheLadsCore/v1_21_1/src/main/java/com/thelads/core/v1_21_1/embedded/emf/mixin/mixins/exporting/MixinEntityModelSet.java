package com.thelads.core.v1_21_1.embedded.emf.mixin.mixins.exporting;

import com.google.common.collect.ImmutableMap;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.LayerDefinitions;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.thelads.core.v1_21_1.embedded.emf.EMF;
import com.thelads.core.v1_21_1.embedded.emf.utils.IEMFUnmodifiedLayerRootGetter;

import java.util.Map;

@Mixin(value = EntityModelSet.class, priority = 1001)
public class MixinEntityModelSet implements IEMFUnmodifiedLayerRootGetter {
    @Shadow
    public Map<ModelLayerLocation, LayerDefinition> roots;
    
    @Override
    public Map<ModelLayerLocation, LayerDefinition> emf$getUnmodifiedRoots() {
        return roots;
    }
}