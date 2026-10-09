package com.thelads.core.v26_2.render.mixin;

import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** QA (1.8.5 render capture): drop the GUI item atlas so every slot is drawn again with the current settings. */
@Mixin(GuiRenderer.class)
public interface GuiRendererProbeAccess {
    @Invoker("invalidateItemAtlas") void lads$invalidateItemAtlas();
}
