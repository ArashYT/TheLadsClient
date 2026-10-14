package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.render.Flerovium189;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    @Inject(method = "renderItemIntoGUI", at = @At("HEAD"))
    private void ladsEnterGui(ItemStack stack, int x, int y, CallbackInfo ci) {
        Flerovium189.enterGui();
    }

    @Inject(method = "renderItemIntoGUI", at = @At("RETURN"))
    private void ladsExitGui(ItemStack stack, int x, int y, CallbackInfo ci) {
        Flerovium189.exitGui();
    }

    @ModifyVariable(
        method = "renderModel(Lnet/minecraft/client/resources/model/IBakedModel;ILnet/minecraft/item/ItemStack;)V",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private IBakedModel ladsWrapGuiModel(IBakedModel model) {
        return Flerovium189.wrapGuiModel(model);
    }
}
