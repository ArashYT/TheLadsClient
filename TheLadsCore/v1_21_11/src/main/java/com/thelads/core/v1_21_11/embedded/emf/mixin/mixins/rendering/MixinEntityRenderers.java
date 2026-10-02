package com.thelads.core.v1_21_11.embedded.emf.mixin.mixins.rendering;

import com.google.common.collect.ImmutableMap;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_11.embedded.emf.EMFManager;
import com.thelads.core.v1_21_11.embedded.emf.utils.UEntityTypes;

@Mixin(EntityRenderers.class)
public class MixinEntityRenderers {


    private static final String method = "method_32174";

    @Inject(method = method, at = @At(value = "HEAD"))
    private static void emf$locateTransient(final ImmutableMap.Builder<?,?> builder, final EntityRendererProvider.Context context, final EntityType<?> entityType, final EntityRendererProvider<?> entityRendererProvider, final CallbackInfo ci) {
        if(entityType.equals(UEntityTypes.SPECTRAL_ARROW)) {
            EMFManager.getInstance().currentSpecifiedModelLoading.set("spectral_arrow");
        }else if (entityType.equals(UEntityTypes.BREEZE_WIND_CHARGE)) {
            EMFManager.getInstance().currentSpecifiedModelLoading.set("breeze_wind_charge");
        }else if (entityType
                .is(EntityTypeTags.BOAT)) {
            //entity.minecraft.dark_oak_boat
            EMFManager.getInstance().currentSpecifiedModelLoading.set("emf$boat$" //key to not override
                    + entityType.getDescriptionId()
                        .replaceAll("entity.minecraft.","")
                        .replaceAll("(_boat|_raft|_chest_boat)$",""));
        }
         else if (entityType.equals(UEntityTypes.CAMEL_HUSK)) {
                  EMFManager.getInstance().currentSpecifiedModelLoading.set("camel_husk");
              }
    }
    @Inject(method = method, at = @At(value = "TAIL"))
    private static void emf$reset(final ImmutableMap.Builder<?,?> builder, final EntityRendererProvider.Context context, final EntityType<?> entityType, final EntityRendererProvider<?> entityRendererProvider, final CallbackInfo ci) {
        EMFManager.getInstance().currentSpecifiedModelLoading.set("");
    }
}
