package com.thelads.core.v1_21_11.gui;

import com.thelads.core.v1_21_11.adapter.GuiGraphicsLadsAdapter;
import com.thelads.core.client.gui.LadsPalette;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Retains the original actions, narration and disabled states of secondary title widgets (26.x TitleExtrasScreen26). */
public final class TitleExtrasScreen12111 extends Screen {
    private final Screen parent;
    private final List<AbstractWidget> actions;
    private int page;
    private long previousFrame;

    public TitleExtrasScreen12111(Screen parent, List<AbstractWidget> actions) {
        super(Component.literal("More"));
        this.parent = parent;
        var availableActions=new java.util.ArrayList<>(actions);
        if(parent instanceof net.minecraft.client.gui.screens.TitleScreen && FlashbackScreens.available()
            &&availableActions.stream().noneMatch(widget->widget.getMessage().getString().equals(Component.translatable("flashback.open_replays").getString())))
            availableActions.add(Button.builder(Component.literal("Replays"),button->FlashbackScreens.open(this)).bounds(0,0,1,1).build());
        // Essential's actions have their own row on the title and pause screens.
        this.actions = availableActions.stream().filter(widget->!widget.getClass().getName().startsWith("gg.essential.")).toList();
    }

    @Override protected void init() {
        int columns = width < 360 ? 1 : 2;
        int rows = Math.max(1, (height - 110) / 34);
        int count = rows * columns;
        int pages = Math.max(1, (actions.size() + count - 1) / count);
        page = Math.min(page, pages - 1);
        int totalWidth = Math.min(width - 40, 470);
        int cellWidth = (totalWidth - (columns - 1) * 10) / columns;
        int startX = (width - totalWidth) / 2;
        for (int i = page * count; i < Math.min(actions.size(), (page + 1) * count); i++) {
            AbstractWidget original = actions.get(i);
            AbstractWidget widget = original;
            if(original instanceof Button button){
                String label=original.getMessage().getString();
                if(label.isBlank())label="Extra settings";
                widget=Button.builder(Component.literal(label),b->button.onPress(null)).bounds(0,0,1,1).build();widget.active=original.active;
            }
            int slot = i - page * count;
            widget.setX(startX + slot % columns * (cellWidth + 10));
            widget.setY(60 + slot / columns * 34);
            widget.setWidth(cellWidth);
            widget.setHeight(28);
            widget.setTabOrderGroup(slot);
            addRenderableWidget(widget);
            TitleWidgetRegistry.register(this, widget, "more", false);
        }
        if (pages > 1) {
            var previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> changePage(-1))
                .bounds(startX, height - 38, 32, 23).build());
            previous.active = page > 0;
            TitleWidgetRegistry.register(this, previous, "more", false);
            var next = addRenderableWidget(Button.builder(Component.literal(">"), button -> changePage(1))
                .bounds(startX + 38, height - 38, 32, 23).build());
            next.active = page + 1 < pages;
            TitleWidgetRegistry.register(this, next, "more", false);
        }
        var done = addRenderableWidget(Button.builder(Component.literal("Done"), button -> onClose())
            .bounds(startX + totalWidth - 82, height - 38, 82, 23).build());
        TitleWidgetRegistry.register(this, done, "play", true);
        previousFrame = System.nanoTime();
    }

    private void changePage(int direction) { page += direction; rebuildWidgets(); }

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float delta) {}

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, LadsPalette.BACKGROUND);
        g.fill(0, 0, width, 2, LadsPalette.ACCENT);
        g.drawString(font, "MORE FROM YOUR CLIENT", 20, 18, LadsPalette.TEXT, false);
        g.drawString(font, "Accounts, services and extra tools", 20, 35, LadsPalette.MUTED, false);
        long now = System.nanoTime();
        float elapsed = (float)Math.clamp((now - previousFrame) / 1e9, 0, .1);
        previousFrame = now;
        TitleWidgetRegistry.beginFrame(this, new GuiGraphicsLadsAdapter(g, font), elapsed,
            minecraft.options.screenEffectScale().get() <= 0);
        try { super.render(g, mouseX, mouseY, delta); }
        finally { TitleWidgetRegistry.endFrame(); }
    }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
