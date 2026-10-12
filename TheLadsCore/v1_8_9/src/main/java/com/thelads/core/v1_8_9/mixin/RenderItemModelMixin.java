package com.thelads.core.v1_8_9.mixin;

import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.model.ModelResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(RenderItem.class)
public abstract class RenderItemModelMixin {
    @Unique
    private static final ModelResourceLocation LADS_ROD_CAST = new ModelResourceLocation("fishing_rod_cast", "inventory");
    @Unique
    private static final ModelResourceLocation LADS_BOW_PULL_0 = new ModelResourceLocation("bow_pulling_0", "inventory");
    @Unique
    private static final ModelResourceLocation LADS_BOW_PULL_1 = new ModelResourceLocation("bow_pulling_1", "inventory");
    @Unique
    private static final ModelResourceLocation LADS_BOW_PULL_2 = new ModelResourceLocation("bow_pulling_2", "inventory");

    @Redirect(
        method = "renderItemModelForEntity",
        at = @At(value = "NEW", target = "net/minecraft/client/resources/model/ModelResourceLocation")
    )
    private ModelResourceLocation ladsReuseModelResourceLocation(String location, String variant) {
        if ("inventory".equals(variant)) {
            if ("fishing_rod_cast".equals(location)) return LADS_ROD_CAST;
            if ("bow_pulling_0".equals(location)) return LADS_BOW_PULL_0;
            if ("bow_pulling_1".equals(location)) return LADS_BOW_PULL_1;
            if ("bow_pulling_2".equals(location)) return LADS_BOW_PULL_2;
        }
        return new ModelResourceLocation(location, variant);
    }
}
