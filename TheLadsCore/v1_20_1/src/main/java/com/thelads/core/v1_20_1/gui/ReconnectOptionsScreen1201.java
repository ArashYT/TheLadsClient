package com.thelads.core.v1_20_1.gui;

import com.thelads.core.client.ReconnectSettings;
import com.thelads.core.v1_20_1.feature.NativeReconnect1201;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Native list editor: one delay per attempt, one reason key/pattern per line. */
public final class ReconnectOptionsScreen1201 extends Screen {
    private final Screen parent;
    private final ReconnectSettings draft;
    private String delayText, keyText, patternText, error = "";
    private int left, span;
    private EditBox delays;
    private MultiLineEditBox keys, patterns;

    public ReconnectOptionsScreen1201(Screen parent) {
        super(Component.literal("Reconnect delays and filters"));
        this.parent = parent;
        draft = NativeReconnect1201.settings().copy();
        delayText = String.join(", ", draft.retryDelays.stream().map(String::valueOf).toList());
        keyText = String.join("\n", draft.reasonKeys);
        patternText = String.join("\n", draft.reasonPatterns);
    }

    @Override
    protected void init() {
        span = Math.min(620, width - 32);
        left = (width - span) / 2;
        delays = new EditBox(font, left, 53, span, 20, Component.literal("Seconds between attempts"));
        delays.setMaxLength(1200);
        delays.setValue(delayText);
        delays.setResponder(value -> delayText = value);
        addRenderableWidget(delays);
        int column = (span - 10) / 2, boxHeight = Math.max(35, height - 165);
        keys = new MultiLineEditBox(font, left, 101, column, boxHeight, Component.literal("Reason keys, one per line"),
            Component.literal("Disconnect reason keys, one per line"));
        keys.setCharacterLimit(16384);
        keys.setValue(keyText);
        keys.setValueListener(value -> keyText = value);
        addRenderableWidget(keys);
        patterns = new MultiLineEditBox(font, left + column + 10, 101, column, boxHeight, Component.literal("Reason patterns, one per line"),
            Component.literal("Disconnect reason patterns, one per line"));
        patterns.setCharacterLimit(16384);
        patterns.setValue(patternText);
        patterns.setValueListener(value -> patternText = value);
        addRenderableWidget(patterns);
        addRenderableWidget(Button.builder(Component.literal("Save"), button -> save()).bounds(left, height - 28, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose()).bounds(left + 103, height - 28, 95, 20).build());
    }

    @Override
    public void tick() {
        if (delays != null) delays.tick();
        if (keys != null) keys.tick();
        if (patterns != null) patterns.tick();
    }

    private void save() {
        try {
            var parsed = new ArrayList<Integer>();
            if (!delayText.isBlank()) {
                for (String token : delayText.split("[,\\s]+")) {
                    int seconds = Integer.parseInt(token);
                    if (seconds < 1 || seconds > 86400) throw new IllegalArgumentException("Each delay must be 1–86400 seconds.");
                    parsed.add(seconds);
                }
            }
            if (parsed.size() > 100) throw new IllegalArgumentException("Use at most 100 retry delays.");
            List<String> keyLines = lines(keyText), patternLines = lines(patternText);
            if (keyLines.size() > 128 || patternLines.size() > 128) throw new IllegalArgumentException("Use at most 128 entries in each filter list.");
            for (String expression : patternLines) Pattern.compile(expression);
            draft.retryDelays = parsed;
            draft.reasonKeys = keyLines;
            draft.reasonPatterns = patternLines;
            NativeReconnect1201.replaceSettings(draft);
            minecraft.setScreen(parent);
        } catch (RuntimeException invalid) {
            error = invalid instanceof NumberFormatException ? "Enter whole seconds separated by commas." : invalid.getMessage();
        }
    }

    public static List<String> lines(String text) {
        return new ArrayList<>(Arrays.stream(text.split("\\R", -1)).filter(value -> !value.isBlank()).toList());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    @Override
    public void renderBackground(GuiGraphics graphics) {
        graphics.fill(0, 0, width, height, 0xff111820);
    }

    @Override
    public void render(GuiGraphics graphics, int x, int y, float delta) {
        renderBackground(graphics);
        graphics.drawString(font, title, left, 18, 0xffe8f1f5, false);
        graphics.drawString(font, "One delay per attempt; an empty list disables automatic attempts.", left, 38, 0xffaabcc8, false);
        graphics.drawString(font, "Reason keys (one per line)", left, 87, 0xffaabcc8, false);
        graphics.drawString(font, "Reason patterns (regular expressions)", left + (span + 10) / 2, 87, 0xffaabcc8, false);
        if (!error.isBlank()) graphics.drawString(font, font.plainSubstrByWidth(error, span), left, height - 44, 0xffff9292, false);
        super.render(graphics, x, y, delta);
    }
}
