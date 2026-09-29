package com.thelads.core.v26_2.feature.paperdoll.mixin;

import com.thelads.core.v26_2.feature.paperdoll.PaperDollTexture;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(RenderType.class)
public abstract class PaperDollRenderTypeMixin implements PaperDollTexture {
    @Shadow @Final private RenderSetup state;
    @Override public Identifier ladsPaperDollTexture() { return ((PaperDollTexture) (Object) state).ladsPaperDollTexture(); }
}
