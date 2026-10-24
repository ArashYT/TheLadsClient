package com.thelads.core.v1_8_9.hudextras.mixin;

import net.minecraft.client.gui.GuiIngame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Title Tweaker's Fit to Screen measures the title and subtitle on screen. */
@Mixin(GuiIngame.class)
public interface GuiTitleAccessor189 {
    @Accessor("displayedTitle")
    String lads$title();

    @Accessor("displayedSubTitle")
    String lads$subtitle();
}
