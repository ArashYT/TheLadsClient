package com.thelads.core.client.gui;

import com.google.gson.JsonObject;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.hud.HudGroupLayout.Rect;
import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.ProfileManager;
import com.thelads.core.config.ShareCode;
import com.thelads.core.config.ShareCode.ImportMode;
import com.thelads.core.config.ShareCodeClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.thelads.core.client.gui.MenuGraphics.*;

/**
 * Common HUD Profiles modal panel:
 *  - Profile list: Load, Save as..., Rename, Delete
 *  - Copy share code to clipboard (shows toast "Copied")
 *  - Import code from clipboard (shows preview "N modules, layout" + Mods/Settings/Both choices, or red error text on invalid code)
 */
public final class ProfilesDialog {
    public enum Mode {
        LIST,
        PROMPT_SAVE,
        PROMPT_RENAME,
        CONFIRM_DELETE,
        CHOICE_LOAD,
        CHOICE_IMPORT
    }

    private boolean open = false;
    private Mode mode = Mode.LIST;
    private String selectedProfile = null;
    private int listScroll = 0, maxListScroll = 0;
    private String nameBuffer = "";
    private String toast = "";
    private long toastExpire = 0;
    private String errorText = "";
    private String previewText = "";
    private JsonObject importedPayload = null;

    private Supplier<String> clipboardReader = () -> "";
    private Consumer<String> clipboardWriter = text -> {};
    private Runnable onSave = () -> {};

    // Bounds recorded during render for clicks and QA verification
    private Rect box = new Rect(0, 0, 0, 0);
    private Rect closeBtn = new Rect(0, 0, 0, 0);
    private Rect loadBtn = new Rect(0, 0, 0, 0);
    private Rect saveBtn = new Rect(0, 0, 0, 0);
    private Rect renameBtn = new Rect(0, 0, 0, 0);
    private Rect deleteBtn = new Rect(0, 0, 0, 0);
    private Rect copyBtn = new Rect(0, 0, 0, 0);
    private Rect importBtn = new Rect(0, 0, 0, 0);
    private Rect choiceMods = new Rect(0, 0, 0, 0);
    private Rect choiceSettings = new Rect(0, 0, 0, 0);
    private Rect choiceBoth = new Rect(0, 0, 0, 0);
    private Rect choiceCancel = new Rect(0, 0, 0, 0);
    private Rect promptOk = new Rect(0, 0, 0, 0);
    private Rect promptCancel = new Rect(0, 0, 0, 0);
    private Rect listArea = new Rect(0, 0, 0, 0);
    private final Map<String, Rect> profileRows = new LinkedHashMap<>();

    private static java.util.concurrent.Executor asyncExecutor = java.util.concurrent.Executors.newCachedThreadPool();
    private final java.util.Queue<Runnable> mainThreadTasks = new java.util.concurrent.ConcurrentLinkedQueue<>();

    public static void setAsyncExecutor(java.util.concurrent.Executor executor) {
        asyncExecutor = executor != null ? executor : java.util.concurrent.Executors.newCachedThreadPool();
    }

    public void postToMainThread(Runnable task) {
        if (task != null) {
            mainThreadTasks.add(task);
        }
    }

    public void processMainThreadTasks() {
        Runnable task;
        while ((task = mainThreadTasks.poll()) != null) {
            try {
                task.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void tick() {
        processMainThreadTasks();
    }

    public void setClipboardReader(Supplier<String> reader) {
        this.clipboardReader = reader != null ? reader : () -> "";
    }

    public void setClipboardWriter(Consumer<String> writer) {
        this.clipboardWriter = writer != null ? writer : text -> {};
    }

    public void setOnSave(Runnable action) {
        this.onSave = action != null ? action : () -> {};
    }

    public void open() {
        this.open = true;
        this.mode = Mode.LIST;
        this.errorText = "";
        this.toast = "";
        this.listScroll = 0;
        List<String> names = ProfileManager.get().getProfileNames();
        if (selectedProfile != null && !names.contains(selectedProfile)) {
            selectedProfile = null;
        }
    }

    public void close() {
        this.open = false;
        this.mode = Mode.LIST;
        this.errorText = "";
        this.importedPayload = null;
    }

    public boolean isOpen() {
        return open;
    }

    public Mode mode() {
        return mode;
    }

    public String selectedProfile() {
        return selectedProfile;
    }

    public void selectProfile(String name) {
        this.selectedProfile = name;
    }

    public String toast() {
        return isToastActive() ? toast : "";
    }

    public String errorText() {
        return errorText;
    }

    public String previewText() {
        return previewText;
    }

    public JsonObject importedPayload() {
        return importedPayload;
    }

    public Rect bounds() { return box; }
    public Rect closeBounds() { return closeBtn; }
    public Rect loadBounds() { return loadBtn; }
    public Rect saveAsBounds() { return saveBtn; }
    public Rect renameBounds() { return renameBtn; }
    public Rect deleteBounds() { return deleteBtn; }
    public Rect copyBounds() { return copyBtn; }
    public Rect importBounds() { return importBtn; }
    public Rect choiceModsBounds() { return choiceMods; }
    public Rect choiceSettingsBounds() { return choiceSettings; }
    public Rect choiceBothBounds() { return choiceBoth; }
    public Rect choiceCancelBounds() { return choiceCancel; }
    public Rect promptOkBounds() { return promptOk; }
    public Rect promptCancelBounds() { return promptCancel; }
    public Rect profileRowBounds(String name) { return profileRows.get(name); }

    public void showToast(String message) {
        this.toast = message;
        this.toastExpire = System.currentTimeMillis() + 2000;
    }

    private boolean isToastActive() {
        return !toast.isEmpty() && System.currentTimeMillis() < toastExpire;
    }

    public void render(LadsGraphics g, int mx, int my) {
        processMainThreadTasks();
        if (!open) return;
        int width = g.getScaledWidth(), height = g.getScaledHeight();
        int dw = Math.min(380, width - 20);
        int dh = Math.min(230, height - 20);
        int dx = (width - dw) / 2, dy = (height - dh) / 2;
        box = new Rect(dx, dy, dw, dh);

        g.fill(0, 0, width, height, 0xB0000000);
        round(g, dx - 2, dy + 3, dw + 4, dh + 4, 0x14000000);
        round(g, dx, dy, dw, dh, LadsPalette.BORDER);
        round(g, dx + 1, dy + 1, dw - 2, dh - 2, LadsPalette.PANEL);
        g.fill(dx + 1, dy, dx + dw - 1, dy + 2, LadsPalette.ACCENT);

        // Close X button
        closeBtn = new Rect(dx + dw - 20, dy + 6, 14, 14);
        boolean hoverClose = closeBtn.contains(mx, my);
        round(g, closeBtn.x(), closeBtn.y(), closeBtn.width(), closeBtn.height(), hoverClose ? LadsPalette.HOVER : LadsPalette.CARD);
        g.drawCenteredText("✕", closeBtn.x() + closeBtn.width() / 2, closeBtn.y() + 3, hoverClose ? LadsPalette.TEXT : LadsPalette.MUTED);

        switch (mode) {
            case LIST -> renderList(g, dx, dy, dw, dh, mx, my);
            case CHOICE_LOAD -> renderChoice(g, dx, dy, dw, dh, mx, my, "Load Profile: " + selectedProfile, "Choose what to apply from this profile:");
            case CHOICE_IMPORT -> renderChoice(g, dx, dy, dw, dh, mx, my, "Import HUD Layout", (previewText.isEmpty() ? "" : previewText + " · ") + "Choose what to import:");
            case PROMPT_SAVE -> renderPrompt(g, dx, dy, dw, dh, mx, my, "Save HUD Profile", "Enter name for profile:", "Save");
            case PROMPT_RENAME -> renderPrompt(g, dx, dy, dw, dh, mx, my, "Rename Profile", "Enter new name for '" + selectedProfile + "':", "Rename");
            case CONFIRM_DELETE -> renderConfirmDelete(g, dx, dy, dw, dh, mx, my);
        }
    }

    private void renderList(LadsGraphics g, int dx, int dy, int dw, int dh, int mx, int my) {
        g.drawText("HUD PROFILES", dx + 12, dy + 10, LadsPalette.ACCENT);

        // List Area
        int lx = dx + 12, ly = dy + 28, lw = dw - 124, lh = dh - 62;
        listArea = new Rect(lx, ly, lw, lh);
        round(g, lx, ly, lw, lh, LadsPalette.CARD);

        List<String> names = ProfileManager.get().getProfileNames();
        profileRows.clear();
        maxListScroll = Math.max(0, names.size() * 18 - lh);
        listScroll = Math.max(0, Math.min(maxListScroll, listScroll));

        g.enableScissor(lx, ly, lx + lw, ly + lh);
        try {
            if (names.isEmpty()) {
                g.drawText("No saved profiles", lx + 8, ly + 8, LadsPalette.MUTED);
            } else {
                for (int i = 0; i < names.size(); i++) {
                    String name = names.get(i);
                    int ry = ly + i * 18 - listScroll;
                    if (ry + 18 <= ly || ry >= ly + lh) continue;
                    Rect r = new Rect(lx + 2, ry + 1, lw - (maxListScroll > 0 ? 8 : 4), 16);
                    profileRows.put(name, r);
                    boolean hover = r.contains(mx, my);
                    boolean chosen = name.equals(selectedProfile);
                    int fill = chosen ? (hover ? LadsPalette.PRIMARY_HOVER : LadsPalette.PRIMARY) : hover ? LadsPalette.HOVER : 0x00000000;
                    if (fill != 0) round(g, r.x(), r.y(), r.width(), r.height(), fill);
                    g.drawText(fit(g, name, r.width() - 8), r.x() + 4, r.y() + 4, chosen ? LadsPalette.TEXT : LadsPalette.MUTED);
                }
            }
        } finally {
            g.disableScissor();
        }

        // Scrollbar thumb if needed
        if (maxListScroll > 0) {
            int thumbH = Math.max(10, lh * lh / (lh + maxListScroll));
            int thumbY = ly + (lh - thumbH) * listScroll / maxListScroll;
            g.fill(lx + lw - 3, ly, lx + lw - 1, ly + lh, LadsPalette.CARD);
            g.fill(lx + lw - 3, thumbY, lx + lw - 1, thumbY + thumbH, LadsPalette.BORDER);
        }

        // Right side action buttons
        int bx = dx + dw - 104, bw = 92, bh = 17;
        int by = dy + 28;

        boolean hasSelection = selectedProfile != null && !selectedProfile.isEmpty();
        loadBtn = new Rect(bx, by, bw, bh);
        drawButton(g, loadBtn, "Load", hasSelection, mx, my);

        by += 22;
        saveBtn = new Rect(bx, by, bw, bh);
        drawButton(g, saveBtn, "Save as...", true, mx, my);

        by += 22;
        renameBtn = new Rect(bx, by, bw, bh);
        drawButton(g, renameBtn, "Rename", hasSelection, mx, my);

        by += 22;
        deleteBtn = new Rect(bx, by, bw, bh);
        drawButton(g, deleteBtn, "Delete", hasSelection, mx, my);

        // Bottom toolbar
        int bty = dy + dh - 26;
        copyBtn = new Rect(dx + 12, bty, 114, 18);
        drawButton(g, copyBtn, "Copy share code", true, mx, my);

        importBtn = new Rect(dx + 132, bty, 90, 18);
        drawButton(g, importBtn, "Import code", true, mx, my);

        // Toast or Error message at bottom right
        if (isToastActive()) {
            g.drawText(toast, dx + 230, bty + 5, 0xFF5DF08A);
        } else if (!errorText.isEmpty()) {
            g.drawText(fit(g, errorText, dw - 238), dx + 228, bty + 5, 0xFFFF5555);
        }
    }

    private void renderChoice(LadsGraphics g, int dx, int dy, int dw, int dh, int mx, int my, String title, String msg) {
        g.drawText(fit(g, title, dw - 30), dx + 12, dy + 10, LadsPalette.ACCENT);
        g.drawText(fit(g, msg, dw - 24), dx + 12, dy + 28, LadsPalette.TEXT);

        int cw = (dw - 40) / 3;
        int cy = dy + 56;

        choiceMods = new Rect(dx + 12, cy, cw, 42);
        drawChoiceCard(g, choiceMods, "Mods", "Toggles only", mx, my);

        choiceSettings = new Rect(dx + 16 + cw, cy, cw, 42);
        drawChoiceCard(g, choiceSettings, "Settings", "Layout & options", mx, my);

        choiceBoth = new Rect(dx + 20 + cw * 2, cy, cw, 42);
        drawChoiceCard(g, choiceBoth, "Both", "All settings", mx, my);

        int cby = dy + dh - 32;
        choiceCancel = new Rect(dx + (dw - 90) / 2, cby, 90, 20);
        drawButton(g, choiceCancel, "Cancel", true, mx, my);

        g.drawCenteredText(fit(g, "Auto-saves 'Before import' snapshot (undoable)", dw - 24), dx + dw / 2, cby - 12, LadsPalette.MUTED);
    }

    private void renderPrompt(LadsGraphics g, int dx, int dy, int dw, int dh, int mx, int my, String title, String msg, String okLabel) {
        g.drawText(title, dx + 12, dy + 10, LadsPalette.ACCENT);
        g.drawText(msg, dx + 12, dy + 32, LadsPalette.TEXT);

        Rect inputRect = new Rect(dx + 12, dy + 52, dw - 24, 22);
        round(g, inputRect.x(), inputRect.y(), inputRect.width(), inputRect.height(), LadsPalette.CARD);
        round(g, inputRect.x() - 1, inputRect.y() - 1, inputRect.width() + 2, inputRect.height() + 2, LadsPalette.ACCENT);
        g.drawText(fit(g, nameBuffer + "_", inputRect.width() - 10), inputRect.x() + 6, inputRect.y() + 6, LadsPalette.TEXT);

        promptOk = new Rect(dx + 12, dy + 86, 80, 20);
        drawButton(g, promptOk, okLabel, !nameBuffer.trim().isEmpty(), mx, my);

        promptCancel = new Rect(dx + 100, dy + 86, 80, 20);
        drawButton(g, promptCancel, "Cancel", true, mx, my);
    }

    private void renderConfirmDelete(LadsGraphics g, int dx, int dy, int dw, int dh, int mx, int my) {
        g.drawText("Delete Profile", dx + 12, dy + 10, LadsPalette.ACCENT);
        g.drawText("Delete profile '" + selectedProfile + "'?", dx + 12, dy + 35, LadsPalette.TEXT);

        promptOk = new Rect(dx + 12, dy + 70, 80, 20);
        drawButton(g, promptOk, "Delete", true, mx, my);

        promptCancel = new Rect(dx + 100, dy + 70, 80, 20);
        drawButton(g, promptCancel, "Cancel", true, mx, my);
    }

    private void drawButton(LadsGraphics g, Rect r, String label, boolean enabled, int mx, int my) {
        boolean hover = enabled && r.contains(mx, my);
        int fill = !enabled ? LadsPalette.PANEL : hover ? LadsPalette.HOVER : LadsPalette.CARD;
        round(g, r.x(), r.y(), r.width(), r.height(), fill);
        if (hover) round(g, r.x() - 1, r.y() - 1, r.width() + 2, r.height() + 2, LadsPalette.BORDER);
        int textCol = enabled ? LadsPalette.TEXT : LadsPalette.DISABLED;
        g.drawCenteredText(label, r.x() + r.width() / 2, r.y() + (r.height() - g.fontHeight()) / 2 + 1, textCol);
    }

    private void drawChoiceCard(LadsGraphics g, Rect r, String title, String subtitle, int mx, int my) {
        boolean hover = r.contains(mx, my);
        int fill = hover ? LadsPalette.PRIMARY_HOVER : LadsPalette.PRIMARY;
        round(g, r.x(), r.y(), r.width(), r.height(), fill);
        round(g, r.x() - 1, r.y() - 1, r.width() + 2, r.height() + 2, LadsPalette.ACCENT);
        g.drawCenteredText(title, r.x() + r.width() / 2, r.y() + 8, LadsPalette.TEXT);
        g.drawCenteredText(subtitle, r.x() + r.width() / 2, r.y() + 22, LadsPalette.MUTED);
    }

    public boolean click(double x, double y, int button) {
        if (!open) return false;
        if (button != 0) return true;

        if (closeBtn.contains(x, y)) {
            close();
            return true;
        }

        switch (mode) {
            case LIST -> {
                // Click on a profile row
                for (Map.Entry<String, Rect> e : profileRows.entrySet()) {
                    if (e.getValue().contains(x, y)) {
                        selectedProfile = e.getKey();
                        return true;
                    }
                }
                if (loadBtn.contains(x, y) && selectedProfile != null) {
                    mode = Mode.CHOICE_LOAD;
                    return true;
                }
                if (saveBtn.contains(x, y)) {
                    nameBuffer = ProfileManager.get().nextDefaultName();
                    mode = Mode.PROMPT_SAVE;
                    return true;
                }
                if (renameBtn.contains(x, y) && selectedProfile != null) {
                    nameBuffer = selectedProfile;
                    mode = Mode.PROMPT_RENAME;
                    return true;
                }
                if (deleteBtn.contains(x, y) && selectedProfile != null) {
                    mode = Mode.CONFIRM_DELETE;
                    return true;
                }
                if (copyBtn.contains(x, y)) {
                    performCopy();
                    return true;
                }
                if (importBtn.contains(x, y)) {
                    performImport();
                    return true;
                }
            }
            case CHOICE_LOAD -> {
                try {
                    if (choiceMods.contains(x, y)) {
                        ProfileManager.get().apply(selectedProfile, ImportMode.MODS);
                        onSave.run();
                        showToast("Loaded (Mods)");
                        mode = Mode.LIST;
                        return true;
                    }
                    if (choiceSettings.contains(x, y)) {
                        ProfileManager.get().apply(selectedProfile, ImportMode.SETTINGS);
                        onSave.run();
                        showToast("Loaded (Settings)");
                        mode = Mode.LIST;
                        return true;
                    }
                    if (choiceBoth.contains(x, y)) {
                        ProfileManager.get().apply(selectedProfile, ImportMode.BOTH);
                        onSave.run();
                        showToast("Loaded (Both)");
                        mode = Mode.LIST;
                        return true;
                    }
                } catch (Exception ex) {
                    errorText = "Load failed: " + ex.getMessage();
                    mode = Mode.LIST;
                    return true;
                }
                if (choiceCancel.contains(x, y)) {
                    mode = Mode.LIST;
                    return true;
                }
            }
            case CHOICE_IMPORT -> {
                try {
                    if (choiceMods.contains(x, y) && importedPayload != null) {
                        ShareCode.applySharePayload(importedPayload, ImportMode.MODS);
                        onSave.run();
                        showToast("Imported (Mods)");
                        mode = Mode.LIST;
                        return true;
                    }
                    if (choiceSettings.contains(x, y) && importedPayload != null) {
                        ShareCode.applySharePayload(importedPayload, ImportMode.SETTINGS);
                        onSave.run();
                        showToast("Imported (Settings)");
                        mode = Mode.LIST;
                        return true;
                    }
                    if (choiceBoth.contains(x, y) && importedPayload != null) {
                        ShareCode.applySharePayload(importedPayload, ImportMode.BOTH);
                        onSave.run();
                        showToast("Imported (Both)");
                        mode = Mode.LIST;
                        return true;
                    }
                } catch (Exception ex) {
                    errorText = "Import failed: " + ex.getMessage();
                    mode = Mode.LIST;
                    return true;
                }
                if (choiceCancel.contains(x, y)) {
                    mode = Mode.LIST;
                    return true;
                }
            }
            case PROMPT_SAVE -> {
                if (promptOk.contains(x, y)) {
                    commitSave();
                    return true;
                }
                if (promptCancel.contains(x, y)) {
                    mode = Mode.LIST;
                    return true;
                }
            }
            case PROMPT_RENAME -> {
                if (promptOk.contains(x, y)) {
                    commitRename();
                    return true;
                }
                if (promptCancel.contains(x, y)) {
                    mode = Mode.LIST;
                    return true;
                }
            }
            case CONFIRM_DELETE -> {
                if (promptOk.contains(x, y)) {
                    ProfileManager.get().delete(selectedProfile);
                    List<String> names = ProfileManager.get().getProfileNames();
                    selectedProfile = names.isEmpty() ? null : names.get(0);
                    showToast("Deleted");
                    mode = Mode.LIST;
                    return true;
                }
                if (promptCancel.contains(x, y)) {
                    mode = Mode.LIST;
                    return true;
                }
            }
        }

        if (!box.contains(x, y)) {
            close();
        }
        return true;
    }

    private void performCopy() {
        if (clipboardWriter == null) return;
        try {
            JsonObject full = selectedProfile != null && ProfileManager.get().hasProfile(selectedProfile)
                ? ProfileManager.get().getProfile(selectedProfile)
                : ConfigManager.toJson();
            JsonObject payload = ShareCode.createSharePayload(full);
            final String longCode = ShareCode.encode(payload);
            errorText = "";

            asyncExecutor.execute(() -> {
                String shortCode = null;
                try {
                    shortCode = ShareCodeClient.postShare(longCode);
                } catch (Exception ignored) {
                }

                final String resultShortCode = shortCode;
                postToMainThread(() -> {
                    if (resultShortCode != null) {
                        clipboardWriter.accept(resultShortCode);
                        showToast("Copied " + resultShortCode);
                    } else {
                        clipboardWriter.accept(longCode);
                        showToast("Server offline - copied long code");
                    }
                });
            });
        } catch (Exception ex) {
            errorText = "Copy failed: " + ex.getMessage();
        }
    }

    private void performImport() {
        String raw = clipboardReader != null ? clipboardReader.get() : "";
        if (raw == null || raw.trim().isEmpty()) {
            errorText = "Clipboard is empty";
            return;
        }
        final String text = raw.trim();
        if (ShareCodeClient.isShortCode(text)) {
            errorText = "";
            asyncExecutor.execute(() -> {
                try {
                    String downloadedPayload = ShareCodeClient.getShare(text);
                    JsonObject decoded = ShareCode.decode(downloadedPayload);
                    String summary = ShareCode.summarize(decoded);
                    postToMainThread(() -> {
                        importedPayload = decoded;
                        previewText = summary;
                        errorText = "";
                        mode = Mode.CHOICE_IMPORT;
                    });
                } catch (ShareCodeClient.ShareNotFoundException e) {
                    postToMainThread(() -> {
                        errorText = "Code not found";
                    });
                } catch (Exception e) {
                    postToMainThread(() -> {
                        errorText = "Can't reach the share server";
                    });
                }
            });
        } else {
            try {
                JsonObject decoded = ShareCode.decode(text);
                importedPayload = decoded;
                previewText = ShareCode.summarize(decoded);
                errorText = "";
                mode = Mode.CHOICE_IMPORT;
            } catch (Exception ex) {
                errorText = ex.getMessage();
            }
        }
    }

    private void commitSave() {
        String name = nameBuffer.trim();
        if (!name.isEmpty()) {
            ProfileManager.get().saveAs(name);
            selectedProfile = name;
            showToast("Saved '" + name + "'");
            mode = Mode.LIST;
        }
    }

    private void commitRename() {
        String name = nameBuffer.trim();
        if (!name.isEmpty() && selectedProfile != null) {
            if (ProfileManager.get().rename(selectedProfile, name)) {
                selectedProfile = name;
                showToast("Renamed to '" + name + "'");
                mode = Mode.LIST;
            } else {
                errorText = "Cannot rename profile";
            }
        }
    }

    public boolean release() {
        return open;
    }

    public boolean scroll(double x, double y, double amount) {
        if (!open) return false;
        if (mode == Mode.LIST && listArea.contains(x, y)) {
            listScroll = Math.max(0, Math.min(maxListScroll, listScroll - (int) Math.round(amount * 18)));
            return true;
        }
        return true;
    }

    public boolean key(int key, int modifiers) {
        if (!open) return false;
        if (key == 256) { // Esc
            if (mode == Mode.LIST) {
                close();
            } else {
                mode = Mode.LIST;
            }
            return true;
        }
        if (mode == Mode.PROMPT_SAVE || mode == Mode.PROMPT_RENAME) {
            if (key == 257 || key == 335) { // Enter
                if (mode == Mode.PROMPT_SAVE) commitSave(); else commitRename();
                return true;
            }
            if (key == 259 && !nameBuffer.isEmpty()) { // Backspace
                nameBuffer = nameBuffer.substring(0, nameBuffer.offsetByCodePoints(nameBuffer.length(), -1));
                return true;
            }
            return true;
        }
        if (mode == Mode.CONFIRM_DELETE && (key == 257 || key == 335)) {
            ProfileManager.get().delete(selectedProfile);
            List<String> names = ProfileManager.get().getProfileNames();
            selectedProfile = names.isEmpty() ? null : names.get(0);
            showToast("Deleted");
            mode = Mode.LIST;
            return true;
        }
        return true;
    }

    public boolean type(int codePoint) {
        if (!open) return false;
        if (mode == Mode.PROMPT_SAVE || mode == Mode.PROMPT_RENAME) {
            if (codePoint >= 32 && codePoint != 127 && nameBuffer.length() < 32) {
                nameBuffer += new String(Character.toChars(codePoint));
            }
            return true;
        }
        return true;
    }
}
