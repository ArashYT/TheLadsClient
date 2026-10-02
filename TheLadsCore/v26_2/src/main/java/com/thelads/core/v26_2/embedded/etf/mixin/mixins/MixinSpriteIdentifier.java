package com.thelads.core.v26_2.embedded.etf.mixin.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.thelads.core.v26_2.embedded.etf.features.ETFManager;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFState;
import com.thelads.core.v26_2.embedded.etf.features.texture_handlers.ETFTexture;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.function.Function;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.SpriteCoordinateExpander;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.resources.Identifier;
import com.thelads.core.v26_2.embedded.etf.utils.ETFUtils2;
@Mixin(net.minecraft.client.resources.model.sprite.SpriteId.class)
public class MixinSpriteIdentifier {
    //TODO really needs a look at
}
