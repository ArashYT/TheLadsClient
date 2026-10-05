package com.thelads.core.v26_2.mixin;

import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** What a HUD text draws, for the HUD build's fingerprint (GuiRenderStateCaptureMixin). */
@Mixin(GuiTextRenderState.class)
public interface GuiTextRenderStateAccessor {
    @Accessor("text") FormattedCharSequence lads$text();
    @Accessor("x") int lads$x();
    @Accessor("y") int lads$y();
    @Accessor("color") int lads$color();
    @Accessor("backgroundColor") int lads$background();
    @Accessor("dropShadow") boolean lads$shadow();
}
