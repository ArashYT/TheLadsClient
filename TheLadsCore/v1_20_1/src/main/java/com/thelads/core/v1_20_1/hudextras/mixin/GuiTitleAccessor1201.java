package com.thelads.core.v1_20_1.hudextras.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Title Tweaker's Fit to Screen measures the title and subtitle on screen. */
@Mixin(Gui.class)
public interface GuiTitleAccessor1201 {
    @Accessor("title")
    Component lads$title();

    @Accessor("subtitle")
    Component lads$subtitle();
}
