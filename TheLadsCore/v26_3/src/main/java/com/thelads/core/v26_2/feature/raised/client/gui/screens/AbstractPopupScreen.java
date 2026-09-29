// Adapted from Raised 6.0.0 source by yurisuika, LGPL-3.0-or-later.
// Pinned source 4b6a3b8718316d681c3ae6832cdd19c3174a29a4; see META-INF/lads-sources/raised.
package com.thelads.core.v26_2.feature.raised.client.gui.screens;

import com.thelads.core.v26_2.feature.raised.Raised;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;

public abstract class AbstractPopupScreen extends AbstractRaisedScreen {

    public SelectScreen parent;
    public ArrayList<AbstractWidget> controls;
    public ArrayList<AbstractWidget> options;
    public AbstractWidget controlClose;
    public int panelX;
    public int panelY;

    public AbstractPopupScreen(SelectScreen parent) {
        super(parent, 164, 56);
        this.parent = parent;
    }

    @Override
    public void setSizes() {
        super.setSizes();

        panelX = containerX + CONTAINER_PADDING;
        panelY = containerY + CONTAINER_PADDING;
    }

    @Override
    public void addContent() {
        createControls();
        createOptions();
    }

    public void createControls() {
        controls = new ArrayList<>();

        controlClose = Button.builder(Component.translatable("options.raised.control.close"), button -> onClose())
                .size(WIDGET_WIDTH_SQUARE, WIDGET_HEIGHT)
                .pos(panelX, panelY)
                .build();

        controls.add(controlClose);

        controls.forEach(this::addRenderableWidget);
    }

    public abstract void createOptions();

    @Override
    public void resize(int width, int height) {
        int previousX = panelX;
        int previousY = panelY;
        super.resize(width, height);
        if (parent != null) parent.resize(width, height);

        // Move the existing widgets so text drafts, selection and focus survive resizing.
        int deltaX = panelX - previousX;
        int deltaY = panelY - previousY;
        if (controls != null) controls.forEach(widget ->
                widget.setPosition(widget.getX() + deltaX, widget.getY() + deltaY));
        if (options != null) options.forEach(widget ->
                widget.setPosition(widget.getX() + deltaX, widget.getY() + deltaY));
    }

    @Override
    public void extractBackground(final GuiGraphicsExtractor guiGraphics, final int mouseX, final int mouseY, final float a) {
        if (parent != null) {
            parent.extractBackground(guiGraphics, mouseX, mouseY, a);
            guiGraphics.nextStratum();
            parent.extractRenderState(guiGraphics, -1, -1, a);
            guiGraphics.nextStratum();
            extractTransparentBackground(guiGraphics);
        } else {
            super.extractBackground(guiGraphics, mouseX, mouseY, a);
        }

        guiGraphics.blitSprite(
                RenderPipelines.GUI_TEXTURED,
                Identifier.fromNamespaceAndPath(Raised.MOD_ID, "popup/background"),
                containerX,
                containerY,
                containerWidth,
                containerHeight);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.textRenderer().acceptScrollingWithDefaultCenter(
                getPopupTitle(),
                panelX + WIDGET_WIDTH_SQUARE + PANEL_GAP,
                panelX + panelWidth,
                panelY,
                panelY + WIDGET_HEIGHT);
    }

    public abstract Component getPopupTitle();

}
