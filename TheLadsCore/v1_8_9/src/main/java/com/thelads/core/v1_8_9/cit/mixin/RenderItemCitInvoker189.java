package com.thelads.core.v1_8_9.cit.mixin;

import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.resources.model.IBakedModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Custom Item Textures: RenderItem's own model draw in one colour, which vanilla's glint uses; CIT enchantment effects use it too. */
@Mixin(RenderItem.class)
public interface RenderItemCitInvoker189 {
    @Invoker("renderModel")
    void lads$renderModel(IBakedModel model, int color);
}
