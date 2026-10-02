package com.thelads.core.v26_2.embedded.etf.tconfig.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.jetbrains.annotations.Nullable;
import com.thelads.core.v26_2.embedded.etf.utils.UScreen;
import com.thelads.core.v26_2.embedded.etf.tconfig.gui.entries.TConfigEntry;

public class TConfigEntryListWidget extends AbstractSelectionList<TConfigEntryListWidget.TConfigEntryForList> {
    public TConfigEntryListWidget(final int width, final int height, final int y, final int x, final int itemHeight,
                                  TConfigEntry... entries) {
        super(Minecraft.getInstance(), width, height, y,
                itemHeight);
        for (TConfigEntry option : entries) {
            if (option == null || option.getWidget(0, 0, 0, 0) == null) continue;
            addEntry(option);
        }
        setX(x);
    }


    @Override
    public int getRowWidth() {
        return Math.min(width - 14, super.getRowWidth());
    }


    @Override
    protected int scrollBarX() {
        return getX() == 0 ? super.scrollBarX() : getX() + getRowWidth() + 4;
    }




    @Override
    protected void updateWidgetNarration(final NarrationElementOutput builder) {}



    @Override protected boolean isValidClickButton(final MouseButtonInfo mouseButtonInfo) { return true; }


    @Override
    public void setSelected(@Nullable final TConfigEntryListWidget.TConfigEntryForList entry) {

    }



    protected boolean fullWidthBackgroundEvenIfSmaller = false;

    public void setWidgetBackgroundToFullWidth() {
        this.fullWidthBackgroundEvenIfSmaller = true;
    }



    @Override
    protected void extractListBackground(final GuiGraphicsExtractor context) {
        if(fullWidthBackgroundEvenIfSmaller){
            int x = getX();
            int width = getWidth();
            setX(0);
            assert UScreen.currentScreen() != null;
            setWidth(UScreen.currentScreen().width);
            super.extractListBackground(context);
            setX(x);
            setWidth(width);
        } else {
            super.extractListBackground(context);
        }
    }

    @Override
    protected void extractListSeparators(final GuiGraphicsExtractor context) {
        if (fullWidthBackgroundEvenIfSmaller) {
            int x = getX();
            int width = getWidth();
            setX(0);
            assert UScreen.currentScreen() != null;
            setWidth(UScreen.currentScreen().width);
            super.extractListSeparators(context);
            setX(x);
            setWidth(width);
        } else {
            super.extractListSeparators(context);
        }
    }

    public abstract static class TConfigEntryForList extends Entry<TConfigEntryForList> {


        protected @Nullable AbstractWidget lastWidgetRendered = null;

        @Override
        public void extractContent(final GuiGraphicsExtractor guiGraphics, final int i, final int j, final boolean bl, final float f) {
            lastWidgetRendered = getWidget(getContentX(), getContentY(), getContentWidth(), getContentHeight());
            if (lastWidgetRendered != null) lastWidgetRendered.
                    extractRenderState
                            (guiGraphics, i, j, f);
        }

        public abstract AbstractWidget getWidget(int x, int y, int width, int height);

        private boolean ignoreMouseAt(double mouseX, double mouseY) {
            return lastWidgetRendered == null || !lastWidgetRendered.isMouseOver(mouseX, mouseY);
        }

        @Override
        public boolean mouseClicked(final MouseButtonEvent mouseButtonEvent, final boolean bl) {
            if (ignoreMouseAt(mouseButtonEvent.x(), mouseButtonEvent.y())) return false;
            return lastWidgetRendered.mouseClicked(mouseButtonEvent, bl);
        }

        @Override
        public boolean mouseDragged(final MouseButtonEvent mouseButtonEvent, final double d, final double e) {
            if (ignoreMouseAt(mouseButtonEvent.x(), mouseButtonEvent.y())) return false;
            return lastWidgetRendered.mouseDragged(mouseButtonEvent, d, e);
        }

        @Override
        public boolean mouseReleased(final MouseButtonEvent mouseButtonEvent) {
            if (ignoreMouseAt(mouseButtonEvent.x(), mouseButtonEvent.y())) return false;
            return lastWidgetRendered.mouseReleased(mouseButtonEvent);
        }

        @Override
        public void setFocused(final boolean focused) {
            if (lastWidgetRendered == null) return;
            lastWidgetRendered.setFocused(focused);
        }
    }
}
