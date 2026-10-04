package com.thelads.core.v26_2.gui;

import com.thelads.core.v26_2.feature.NativeReconnect;
import com.thelads.core.client.ReconnectSettings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Native list editor: one delay per attempt, one reason key/pattern per line. */
public final class ReconnectOptionsScreen26 extends Screen {
    private final Screen parent;
    private final ReconnectSettings draft;
    private String delayText, keyText, patternText, error = "";
    private int left, span;
    public ReconnectOptionsScreen26(Screen parent) {
        super(Component.literal("Reconnect delays and filters")); this.parent = parent;
        draft = NativeReconnect.settings().copy();
        delayText = String.join(", ", draft.retryDelays.stream().map(String::valueOf).toList());
        keyText = String.join("\n", draft.reasonKeys); patternText = String.join("\n", draft.reasonPatterns);
    }
    @Override protected void init() {
        span = Math.min(620, width - 32); left = (width - span) / 2;
        EditBox delays = new EditBox(font, left, 53, span, 20, Component.literal("Seconds between attempts"));
        delays.setMaxLength(1200); delays.setValue(delayText); delays.setResponder(value -> delayText = value); addRenderableWidget(delays);
        int column = (span - 10) / 2, boxHeight = Math.max(35, height - 165);
        MultiLineEditBox keys = MultiLineEditBox.builder().setX(left).setY(101).build(font, column, boxHeight, Component.literal("Disconnect reason keys, one per line"));
        keys.setCharacterLimit(16384); keys.setLineLimit(128); keys.setValue(keyText); keys.setValueListener(value -> keyText = value); addRenderableWidget(keys);
        MultiLineEditBox patterns = MultiLineEditBox.builder().setX(left + column + 10).setY(101).build(font, column, boxHeight, Component.literal("Disconnect reason patterns, one per line"));
        patterns.setCharacterLimit(16384); patterns.setLineLimit(128); patterns.setValue(patternText); patterns.setValueListener(value -> patternText = value); addRenderableWidget(patterns);
        addRenderableWidget(Button.builder(Component.literal("Save"), button -> save()).bounds(left, height - 28, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose()).bounds(left + 103, height - 28, 95, 20).build());
    }
    private void save() {
        try {
            var parsed = new ArrayList<Integer>();
            if (!delayText.isBlank()) for (String token : delayText.split("[,\\s]+")) {
                int seconds = Integer.parseInt(token);
                if (seconds < 1 || seconds > 86400) throw new IllegalArgumentException("Each delay must be 1–86400 seconds.");
                parsed.add(seconds);
            }
            if (parsed.size() > 100) throw new IllegalArgumentException("Use at most 100 retry delays.");
            List<String> keys = lines(keyText), patterns = lines(patternText);
            if (keys.size() > 128 || patterns.size() > 128) throw new IllegalArgumentException("Use at most 128 entries in each filter list.");
            for (String expression : patterns) Pattern.compile(expression);
            draft.retryDelays = parsed; draft.reasonKeys = keys; draft.reasonPatterns = patterns;
            NativeReconnect.replaceSettings(draft); minecraft.gui.setScreen(parent);
        } catch (RuntimeException invalid) { error = invalid instanceof NumberFormatException ? "Enter whole seconds separated by commas." : invalid.getMessage(); }
    }
    public static List<String> lines(String text) { return new ArrayList<>(Arrays.stream(text.split("\\R", -1)).filter(value -> !value.isBlank()).toList()); }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int x, int y, float delta) { graphics.fill(0, 0, width, height, 0xff111820); }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        graphics.text(font, title, left, 18, 0xffe8f1f5, false);
        graphics.text(font, "One delay per attempt; an empty list disables automatic attempts.", left, 38, 0xffaabcc8, false);
        graphics.text(font, "Reason keys (one per line)", left, 87, 0xffaabcc8, false);
        graphics.text(font, "Reason patterns (regular expressions)", left + (span + 10) / 2, 87, 0xffaabcc8, false);
        if (!error.isBlank()) graphics.text(font, font.plainSubstrByWidth(error, span), left, height - 44, 0xffff9292, false);
        super.extractRenderState(graphics, x, y, delta);
    }
}
