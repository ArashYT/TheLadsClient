package com.thelads.core.v26_2.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.LoggerFactory;

/** Launches the retained advancement engine's public editors from Lads. */
public final class NewEngineScreens26 extends Screen {
    private final Screen parent;
    public NewEngineScreens26(Screen parent) {
        super(Component.literal("Advancement layout editors"));
        this.parent = parent;
    }
    @Override protected void init() {
        int x = width / 2 - 110, y = height / 2 - 42;
        addRenderableWidget(Button.builder(Component.literal("Toast layout"), button -> open("ToastEditScreen"))
            .bounds(x, y, 220, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Tracker layout"), button -> open("HudEditScreen"))
            .bounds(x, y + 30, 220, 22).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
            .bounds(x, y + 68, 220, 22).build());
    }
    private void open(String editor) {
        try {
            Screen target = (Screen)Class.forName("net.a5ho999.modernadvancements.client.screen." + editor)
                .getConstructor(Screen.class).newInstance(this);
            Minecraft.getInstance().setScreenAndShow(target);
        } catch (ReflectiveOperationException | LinkageError error) {
            LoggerFactory.getLogger("TheLadsCore").warn("Cannot open advancement editor {}", editor, error);
            Minecraft.getInstance().getNarrator().saySystemNow("The advancement editor could not open. See the game log.");
        }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xF00A0A10);
        String heading = title.getString();
        graphics.text(font, heading, (width - font.width(heading)) / 2, height / 2 - 80, 0xFFFFFFFF, false);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }
    @Override public void onClose() { Minecraft.getInstance().setScreenAndShow(parent); }
}
