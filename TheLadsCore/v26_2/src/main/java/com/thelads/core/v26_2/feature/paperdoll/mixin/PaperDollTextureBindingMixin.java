package com.thelads.core.v26_2.feature.paperdoll.mixin;

import com.thelads.core.v26_2.feature.paperdoll.PaperDollTexture;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(targets = "net.minecraft.client.renderer.rendertype.RenderSetup$TextureBinding")
public abstract class PaperDollTextureBindingMixin implements PaperDollTexture {
    @Shadow @Final private Identifier location;
    @Override public Identifier ladsPaperDollTexture() { return location; }
}
