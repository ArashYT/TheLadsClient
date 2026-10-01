package com.thelads.core.v1_8_9.gui;

import com.thelads.core.client.gui.LadsPalette;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.v1_8_9.adapter.GuiLadsAdapter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

/** "More": the title (or pause) screen's secondary actions as Lads buttons, paged (TitleExtrasScreen121 on the other versions). */
public final class TitleExtrasScreen189 extends GuiScreen {
    /** One secondary action: a native button pressed on its own screen, an Essential action or a Lads one. */
    public static final class Action {
        final String label;
        final Runnable press;
        final boolean enabled;

        public Action(String label, Runnable press, boolean enabled) {
            this.label = label;
            this.press = press;
            this.enabled = enabled;
        }
    }

    private static final int DONE = -1, PREVIOUS = -2, NEXT = -3;
    private static final Map<GuiButton, float[]> HOVER = new WeakHashMap<>();
    private final GuiScreen parent;
    private final List<Action> actions;
    private int page;
    private boolean pageChanged;
    private long previousFrame;

    public TitleExtrasScreen189(GuiScreen parent, List<Action> actions) {
        this.parent = parent;
        this.actions = actions;
    }

    /** As below, labelled by their own text. */
    public static List<Action> of(GuiScreen owner, List<GuiButton> buttons) {
        return of(owner, buttons, button -> button.displayString);
    }

    /** Each native button pressed on its owner screen (labelled by label), Essential's proxies as their real actions. */
    public static List<Action> of(GuiScreen owner, List<GuiButton> buttons, Function<GuiButton, String> label) {
        List<Action> actions = new ArrayList<>();
        Set<String> essentialLabels = new HashSet<>();
        for (GuiButton button : buttons) {
            if (EssentialActions189.isEssential(button)) {
                Action action = EssentialActions189.capture(button);
                if (action != null && essentialLabels.add(action.label)) actions.add(action);
                continue;
            }
            String text = label.apply(button);
            actions.add(new Action(text == null || text.trim().isEmpty() ? "Extra settings" : text,
                () -> ((LadsButtonPress) owner).ladsPress(button), button.enabled));
        }
        return actions;
    }

    /** QA: the action labels, in order. */
    public List<String> labels() {
        List<String> labels = new ArrayList<>();
        for (Action action : actions) labels.add(action.label);
        return labels;
    }

    /** QA: this page's button for an action label, or null. */
    public GuiButton button(String label) {
        for (GuiButton button : buttonList) if (label.equals(button.displayString)) return button;
        return null;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        int columns = width < 360 ? 1 : 2;
        int count = Math.max(1, (height - 110) / 34) * columns;
        int pages = Math.max(1, (actions.size() + count - 1) / count);
        page = Math.min(page, pages - 1);
        int totalWidth = Math.min(width - 40, 470);
        int cellWidth = (totalWidth - (columns - 1) * 10) / columns;
        int startX = (width - totalWidth) / 2;
        for (int i = page * count; i < Math.min(actions.size(), (page + 1) * count); i++) {
            int slot = i - page * count;
            GuiButton button = new GuiButton(i, startX + slot % columns * (cellWidth + 10), 60 + slot / columns * 34, cellWidth, 28, actions.get(i).label);
            button.enabled = actions.get(i).enabled;
            buttonList.add(button);
        }
        if (pages > 1) {
            GuiButton previous = new GuiButton(PREVIOUS, startX, height - 38, 32, 23, "<");
            previous.enabled = page > 0;
            buttonList.add(previous);
            GuiButton next = new GuiButton(NEXT, startX + 38, height - 38, 32, 23, ">");
            next.enabled = page + 1 < pages;
            buttonList.add(next);
        }
        buttonList.add(new GuiButton(DONE, startX + totalWidth - 82, height - 38, 82, 23, "Done"));
        previousFrame = System.nanoTime();
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == DONE) mc.displayGuiScreen(parent);
        else if (button.id == PREVIOUS || button.id == NEXT) {
            // Rebuilt before the next frame: GuiScreen.mouseClicked keeps iterating buttonList after this.
            page += button.id == NEXT ? 1 : -1;
            pageChanged = true;
        } else actions.get(button.id).press.run();
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {
        if (keyCode == 1) mc.displayGuiScreen(parent);
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        if (pageChanged) {
            pageChanged = false;
            initGui();
        }
        Gui.drawRect(0, 0, width, height, LadsPalette.BACKGROUND);
        Gui.drawRect(0, 0, width, 2, LadsPalette.ACCENT);
        fontRendererObj.drawString("MORE FROM YOUR CLIENT", 20, 18, LadsPalette.TEXT);
        fontRendererObj.drawString("Accounts, services and extra tools", 20, 35, LadsPalette.MUTED);
        long now = System.nanoTime();
        float elapsed = (float) Math.min(0.1, (now - previousFrame) / 1e9);
        previousFrame = now;
        GuiLadsAdapter g = new GuiLadsAdapter(fontRendererObj, width, height);
        for (GuiButton button : buttonList)
            drawButton(g, button, button.displayString, button.id == DONE ? "play" : "more", button.id == DONE, mouseX, mouseY, elapsed);
    }

    /** A GuiButton as a Lads title button (TitleScreenTheme.renderButton) with the other versions' hover fade. */
    static void drawButton(GuiLadsAdapter g, GuiButton button, String label, String icon, boolean primary, int mouseX, int mouseY, float elapsed) {
        boolean hovered = mouseX >= button.xPosition && mouseY >= button.yPosition
            && mouseX < button.xPosition + button.width && mouseY < button.yPosition + button.height;
        float[] hover = HOVER.get(button);
        if (hover == null) HOVER.put(button, hover = new float[1]);
        float target = button.enabled && hovered ? 1 : 0;
        hover[0] = hover[0] < target ? Math.min(target, hover[0] + elapsed * 8) : Math.max(target, hover[0] - elapsed * 8);
        TitleScreenTheme.renderButton(g, button.xPosition, button.yPosition, button.width, button.height, label, icon, primary, hovered, false,
            button.enabled, hover[0]);
    }
}
