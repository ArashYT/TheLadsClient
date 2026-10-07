package com.thelads.core.v26_2.gui;

import com.thelads.core.v26_2.adapter.GuiGraphicsExtractorLadsAdapter;
import com.thelads.core.client.gui.LadsPalette;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Retains the original actions, narration and disabled states of secondary title widgets. */
public final class TitleExtrasScreen26 extends Screen {
    private final Screen parent;
    private final List<AbstractWidget> actions;
    private int page;
    private long previousFrame;

    public TitleExtrasScreen26(Screen parent, List<AbstractWidget> actions) {
        super(Component.literal("More"));
        this.parent = parent;
        var availableActions = new java.util.ArrayList<>(actions);
        // Exclude Essential, Replays, Friends, and Language (which are now directly on the main title screen / header)
        this.actions = availableActions.stream().filter(widget -> {
            String className = widget.getClass().getName();
            if (className.startsWith("gg.essential.")) return false;
            String text = widget.getMessage() != null ? widget.getMessage().getString() : "";
            Component msg = widget.getMessage();
            String key = (msg != null && msg.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) ? tc.getKey() : "";
            if (key.equals("options.language") || text.equalsIgnoreCase("language")) return false;
            if (key.equals("flashback.open_replays") || text.equalsIgnoreCase("replays")) return false;
            if (text.equalsIgnoreCase("friends") || text.equalsIgnoreCase("social")) return false;
            return true;
        }).toList();
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
                String label=TitleWidgetRegistry.renderLabel(original);
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

    @Override public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {}

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, LadsPalette.BACKGROUND);
        g.fill(0, 0, width, 2, LadsPalette.ACCENT);
        g.text(font, "MORE FROM YOUR CLIENT", 20, 18, LadsPalette.TEXT, false);
        g.text(font, "Services and extra tools", 20, 35, LadsPalette.MUTED, false);
        long now = System.nanoTime();
        float elapsed = (float)Math.clamp((now - previousFrame) / 1e9, 0, .1);
        previousFrame = now;
        TitleWidgetRegistry.beginFrame(this, new GuiGraphicsExtractorLadsAdapter(g, font), elapsed,
            minecraft.options.screenEffectScale().get() <= 0);
        try { super.extractRenderState(g, mouseX, mouseY, delta); }
        finally { TitleWidgetRegistry.endFrame(); }
    }

    @Override public void onClose() { minecraft.setScreenAndShow(parent); }
}
