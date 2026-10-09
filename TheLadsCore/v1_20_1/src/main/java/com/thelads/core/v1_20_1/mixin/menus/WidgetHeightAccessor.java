package com.thelads.core.v1_20_1.mixin.menus;

import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** 1.20.1 widgets have setX, setY and setWidth but no setHeight (the title and pause layouts resize vanilla buttons), and a protected alpha. */
@Mixin(AbstractWidget.class)
public interface WidgetHeightAccessor {
    @Accessor("height") void ladsSetHeight(int height);

    @Accessor("alpha") float ladsAlpha();
}
