package com.thelads.core.v1_20_1.gui;

import com.thelads.core.v1_20_1.mixin.menus.WidgetHeightAccessor;
import net.minecraft.client.gui.components.AbstractWidget;

public final class Widgets1201 {
    private Widgets1201() {}

    public static void setBounds(AbstractWidget widget, int x, int y, int width, int height) {
        widget.setX(x);
        widget.setY(y);
        widget.setWidth(width);
        ((WidgetHeightAccessor) widget).ladsSetHeight(height);
    }
}
