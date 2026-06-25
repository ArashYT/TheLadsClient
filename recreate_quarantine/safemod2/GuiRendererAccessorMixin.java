package com.thelads.core.mixin.auto.safemod2;

import net.minecraft.client.renderer.gui.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiRenderer.class)
public interface GuiRendererAccessor {
    @Accessor("guiGraphics")
    void setGuiGraphics(net.minecraft.client.gui.GuiGraphics guiGraphics);
}
