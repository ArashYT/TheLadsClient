package com.thelads.core.v26_2.gui;

import com.thelads.core.v26_2.feature.NativeReconnect;
import com.thelads.core.v26_2.feature.ReconnectSettings;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Explicitly configured context-specific actions. Editing this screen never sends messages. */
public final class ReconnectActionsScreen26 extends Screen {
    private final Screen parent;
    private final ReconnectSettings draft;
    private int selected, left, span;
    private EditBox id, delay;
    private MultiLineEditBox messages;
    private String error = "";
    public ReconnectActionsScreen26(Screen parent) {
        super(Component.literal("Actions after reconnecting")); this.parent = parent;
        draft = NativeReconnect.settings().copy();
    }
    @Override protected void init() {
        String currentId = id == null ? null : id.getValue();
        String currentDelay = delay == null ? null : delay.getValue();
        String currentMessages = messages == null ? null : messages.getValue();
        span = Math.min(620, width - 32); left = (width - span) / 2;
        addRenderableWidget(Button.builder(Component.literal("Add"), button -> { if (!stash()) return; draft.autoMessages.add(new ReconnectSettings.Action()); selected = draft.autoMessages.size() - 1; rebuild(); }).bounds(left, 40, 60, 20).build()).active = draft.autoMessages.size() < 64;
        addRenderableWidget(Button.builder(Component.literal("Remove"), button -> { if (draft.autoMessages.isEmpty()) return; draft.autoMessages.remove(selected); selected = Math.max(0, selected - 1); rebuild(); }).bounds(left + 66, 40, 68, 20).build()).active = !draft.autoMessages.isEmpty();
        addRenderableWidget(Button.builder(Component.literal("<"), button -> { if (stash()) { selected--; rebuild(); } }).bounds(left + span - 60, 40, 26, 20).build()).active = selected > 0;
        addRenderableWidget(Button.builder(Component.literal(">"), button -> { if (stash()) { selected++; rebuild(); } }).bounds(left + span - 26, 40, 26, 20).build()).active = selected + 1 < draft.autoMessages.size();
        if (!draft.autoMessages.isEmpty()) {
            ReconnectSettings.Action action = draft.autoMessages.get(selected);
            id = new EditBox(font, left, 82, span - 130, 20, Component.literal("Server address, Realm name or world folder"));
            id.setMaxLength(512); id.setValue(currentId == null ? action.id : currentId); addRenderableWidget(id);
            delay = new EditBox(font, left + span - 120, 82, 120, 20, Component.literal("Seconds before and between messages"));
            delay.setMaxLength(12); delay.setValue(currentDelay == null ? Double.toString(action.delay) : currentDelay); addRenderableWidget(delay);
            messages = MultiLineEditBox.builder().setX(left).setY(121).build(font, span, Math.max(35, height - 217), Component.literal("One message or slash command per line"));
            messages.setCharacterLimit(262144); messages.setLineLimit(100); messages.setValue(currentMessages == null ? String.join("\n", action.messages) : currentMessages); addRenderableWidget(messages);
            addRenderableWidget(Button.builder(Component.literal(action.enabled ? "Profile enabled" : "Profile disabled"), button -> {
                action.enabled = !action.enabled; button.setMessage(Component.literal(action.enabled ? "Profile enabled" : "Profile disabled"));
            }).bounds(left, height - 84, 140, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Save"), button -> save()).bounds(left, height - 28, 95, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose()).bounds(left + 103, height - 28, 95, 20).build());
    }
    private void rebuild() { id = null; delay = null; messages = null; clearWidgets(); init(); }
    private boolean stash() {
        if (id == null || draft.autoMessages.isEmpty()) return true;
        try {
            var action = draft.autoMessages.get(selected);
            double seconds = Double.parseDouble(delay.getValue());
            if (!Double.isFinite(seconds) || seconds < .1 || seconds > 3600) throw new IllegalArgumentException("Action delay must be 0.1–3600 seconds.");
            if (NativeReconnect.module().regexIds.get()) Pattern.compile(id.getValue());
            var lines = ReconnectOptionsScreen26.lines(messages.getValue());
            for (String line : lines) if (line.length() > (line.startsWith("/") ? 32767 : 256)) throw new IllegalArgumentException("A chat message exceeds 256 characters or a command exceeds 32767.");
            action.id = id.getValue(); action.delay = seconds; action.messages = lines;
            error = ""; return true;
        } catch (RuntimeException invalid) { error = invalid instanceof NumberFormatException ? "Enter a numeric action delay." : invalid.getMessage(); return false; }
    }
    private void save() {
        if (!stash()) return;
        try { NativeReconnect.replaceSettings(draft); minecraft.gui.setScreen(parent); }
        catch (RuntimeException failed) { error = failed.getMessage(); }
    }
    @Override public void onClose() { minecraft.gui.setScreen(parent); }
    @Override public void extractBackground(GuiGraphicsExtractor graphics, int x, int y, float delta) { graphics.fill(0, 0, width, height, 0xff111820); }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        graphics.text(font, title, left, 18, 0xffe8f1f5, false);
        graphics.text(font, draft.autoMessages.isEmpty() ? "No actions configured" : "Profile " + (selected + 1) + " / " + draft.autoMessages.size(), left + 146, 46, 0xffaabcc8, false);
        if (!draft.autoMessages.isEmpty()) {
            graphics.text(font, "Server address, Realm name or world folder", left, 69, 0xffaabcc8, false);
            graphics.text(font, "Delay (seconds)", left + span - 120, 69, 0xffaabcc8, false);
            graphics.text(font, "One message or /command per line", left, 108, 0xffaabcc8, false);
        }
        graphics.text(font, "Runs only after automatic reconnect, with Enable Reconnect Actions on.", left, height - 56, 0xffaabcc8, false);
        if (!error.isBlank()) graphics.text(font, font.plainSubstrByWidth(error, span), left, height - 43, 0xffff9292, false);
        super.extractRenderState(graphics, x, y, delta);
    }
}
