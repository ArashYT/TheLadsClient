package com.thelads.core.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.LadsSettingsScreen.Rect;
import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.config.ConfigManager;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import static com.thelads.core.client.gui.MenuGraphics.*;

/**
 * Lads settings: Import from another client. The importer itself is the launcher's (it owns the shared options, server list, packs and
 * config): this dialog runs its {@code --client-import} command, shows the clients found and each category's preview, and takes the
 * changed config into the live modules afterwards. Options the running game holds apply at its next start; the launcher tells it.
 */
public final class ClientImportDialog {
    /** Set by the launcher for every game it starts: its own exe. */
    public static final String LAUNCHER_ENV = "LADS_LAUNCHER_EXE";
    private static final int ROW = 13;
    private static final long TIMEOUT_MINUTES = 15;

    /** The launcher's command; replaced by tests. */
    public interface Importer { JsonObject run(String... args) throws IOException, InterruptedException; }

    private record Hit(Rect rect, Runnable action) {}

    private final Importer importer;
    private final Runnable reloadConfig;
    private final Queue<Runnable> finished = new ConcurrentLinkedQueue<>();
    private final List<Hit> hits = new ArrayList<>();
    private final Set<String> chosen = new LinkedHashSet<>();
    private boolean open, busy, canUndo;
    private String status = "";
    private JsonArray clients = new JsonArray();
    private JsonObject selected;
    private int offset, x, y, w, h;

    ClientImportDialog() { this(launcherCommand(System.getenv(LAUNCHER_ENV)), ConfigManager::load); }

    ClientImportDialog(Importer importer, Runnable reloadConfig) {
        this.importer = importer;
        this.reloadConfig = reloadConfig;
    }

    boolean isOpen() { return open; }
    String status() { return status; }
    JsonObject selected() { return selected; }
    Set<String> chosen() { return chosen; }
    boolean canUndo() { return canUndo; }

    /** Finished background work, applied on the game thread (each frame; tests call it). */
    void pump() { for (Runnable done; (done = finished.poll()) != null; ) done.run(); }

    void open() {
        open = true;
        selected = null;
        offset = 0;
        chosen.clear();
        clients = new JsonArray();
        status = "Looking for other clients...";
        background(new String[] {"scan"}, answer -> {
            clients = answer.get("clients") instanceof JsonArray list ? list : new JsonArray();
            canUndo = answer.has("canUndo") && answer.get("canUndo").getAsBoolean();
            status = clients.isEmpty() ? "No other client was found." : "Pick the client to import from.";
        });
    }

    void select(JsonObject client) {
        selected = client;
        offset = 0;
        chosen.clear();
        for (JsonElement category : client.getAsJsonArray("categories")) {
            JsonObject c = category.getAsJsonObject();
            if (c.get("changes").getAsInt() > 0 && !c.has("problem")) chosen.add(c.get("id").getAsString());
        }
        status = "";
    }

    void apply() {
        if (selected == null || chosen.isEmpty() || busy) return;
        status = "Importing...";
        ConfigManager.flush(); // the importer reads the config: everything changed in this session first
        background(new String[] {"apply", selected.get("id").getAsString(), String.join(",", chosen), "--in-game"}, answer -> {
            finishChange(answer, summary(answer));
            selected = null;
            canUndo = canUndo || answer.getAsJsonObject("applied").size() > 0;
        });
    }

    void undo() {
        if (busy) return;
        status = "Undoing the last import...";
        ConfigManager.flush();
        background(new String[] {"undo", "--in-game"}, answer -> {
            finishChange(answer, "The last import was undone.");
            selected = null;
            canUndo = false;
        });
    }

    private void finishChange(JsonObject answer, String text) {
        if (answer.has("configChanged") && answer.get("configChanged").getAsBoolean()) reloadConfig.run();
        StringBuilder message = new StringBuilder(text);
        if (answer.has("restartNeeded") && answer.get("restartNeeded").getAsBoolean()) message.append(" Keybinds and video settings apply when you restart the game.");
        if (answer.has("launcherPending") && answer.get("launcherPending").getAsBoolean()) message.append(" Launcher settings apply when the launcher comes back.");
        JsonArray errors = answer.getAsJsonArray("errors");
        if (errors != null && !errors.isEmpty()) message.append(" Problems: ").append(errors);
        status = message.toString();
    }

    static String summary(JsonObject answer) {
        JsonObject applied = answer.getAsJsonObject("applied");
        if (applied == null || applied.size() == 0) return "Nothing changed.";
        List<String> parts = new ArrayList<>();
        applied.entrySet().forEach(e -> parts.add(e.getValue().getAsInt() + " " + e.getKey().toLowerCase(java.util.Locale.ROOT)));
        return "Imported " + String.join(", ", parts) + ".";
    }

    private interface Done { void accept(JsonObject answer); }

    private void background(String[] args, Done done) {
        busy = true;
        Thread worker = new Thread(() -> {
            JsonObject answer;
            String failure = null;
            try {
                answer = importer.run(args);
                if (answer.has("error")) failure = answer.get("error").getAsString();
            } catch (IOException | RuntimeException e) {
                answer = null;
                failure = e.getMessage();
            } catch (InterruptedException e) {
                answer = null;
                failure = "Interrupted.";
                Thread.currentThread().interrupt();
            }
            JsonObject result = answer;
            String problem = failure;
            finished.add(() -> {
                busy = false;
                if (problem != null) status = problem;
                else done.accept(result);
            });
        }, "Lads client import");
        worker.setDaemon(true);
        worker.start();
    }

    /** Runs the launcher that started this game. Its data folder is THELADS_GLOBAL_DIR; THELADS_DIR, as the game has it, is the profile's. */
    static Importer launcherCommand(String exe) {
        return args -> {
            if (exe == null || exe.isBlank() || !new File(exe).isFile())
                throw new IOException("Start the game from The Lads Launcher to import here, or use the launcher: Settings, General, Import from another client.");
            List<String> command = new ArrayList<>(List.of(exe, "--client-import"));
            command.addAll(List.of(args));
            ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(false);
            builder.environment().put("THELADS_DIR", ClientPaths.getGlobalBaseDir().toAbsolutePath().toString());
            builder.redirectError(ProcessBuilder.Redirect.appendTo(new File(System.getProperty("java.io.tmpdir"), "lads-client-import.log")));
            Process process = builder.start();
            try {
                process.getOutputStream().close();
                // ponytail: bounded read of the answer, then a wait; the launcher's answer is small and comes after its work.
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[65536];
                for (int read; (read = process.getInputStream().read(chunk)) > 0 && output.size() < 16 * 1024 * 1024; ) output.write(chunk, 0, read);
                if (!process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) throw new IOException("The launcher did not answer in time.");
                return JsonParser.parseString(new String(output.toByteArray(), StandardCharsets.UTF_8)).getAsJsonObject();
            } finally {
                process.destroy();
            }
        };
    }

    void render(LadsGraphics g, int mx, int my) {
        if (!open) return;
        pump();
        hits.clear();
        w = Math.min(520, g.getScaledWidth() - 24); h = Math.min(420, g.getScaledHeight() - 24);
        x = (g.getScaledWidth() - w) / 2; y = (g.getScaledHeight() - h) / 2;
        g.fill(0, 0, g.getScaledWidth(), g.getScaledHeight(), 0xB8000000);
        round(g, x, y, w, h, PANEL);
        g.drawText("Import from another client", x + 12, y + 11, TEXT);
        link(g, "Close", x + w - 43, y + 11, () -> open = false, mx, my);
        int top = y + 30;
        top += wrap(g, status, x + 12, top, w - 24, 3, MUTED) + 4;
        if (selected == null) renderClients(g, top, mx, my);
        else renderPreview(g, top, mx, my);
        if (canUndo && !busy) link(g, "Undo last import", x + 12, y + h - 16, this::undo, mx, my);
    }

    private void renderClients(LadsGraphics g, int top, int mx, int my) {
        int row = 0;
        for (JsonElement element : clients) {
            JsonObject client = element.getAsJsonObject();
            int ry = top + row++ * 26;
            Rect rect = new Rect(x + 10, ry, w - 20, 23);
            g.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), rect.contains(mx, my) ? LadsPalette.BORDER : CARD);
            String name = client.get("name").getAsString() + (client.get("basic").getAsBoolean() ? " (basic import)" : "");
            g.drawText(fit(g, name, w - 40), x + 16, ry + 7, TEXT);
            if (!busy) hits.add(new Hit(rect, () -> select(client)));
        }
    }

    private void renderPreview(LadsGraphics g, int top, int mx, int my) {
        g.drawText(fit(g, selected.get("note").getAsString(), w - 24), x + 12, top, MUTED);
        top += 14;
        List<String> lines = new ArrayList<>();
        for (JsonElement element : selected.getAsJsonArray("categories")) {
            JsonObject category = element.getAsJsonObject();
            String id = category.get("id").getAsString();
            boolean usable = category.get("changes").getAsInt() > 0 && !category.has("problem");
            String label = (chosen.contains(id) ? "[x] " : "[ ] ") + id + ": " + (category.has("problem") ? category.get("problem").getAsString()
                : category.get("changes").getAsInt() + " to change, " + category.get("skipped").getAsInt() + " skipped");
            Rect rect = new Rect(x + 10, top, w - 20, 16);
            if (usable && rect.contains(mx, my)) g.fill(rect.x(), rect.y(), rect.x() + rect.width(), rect.y() + rect.height(), CARD);
            g.drawText(fit(g, label, w - 28), x + 14, top + 4, usable ? TEXT : MUTED);
            if (usable) hits.add(new Hit(rect, () -> { if (!chosen.remove(id)) chosen.add(id); }));
            top += 17;
            for (JsonElement line : category.getAsJsonArray("changeLines")) lines.add("+ " + line.getAsString());
            for (JsonElement line : category.getAsJsonArray("skippedLines")) lines.add("skipped: " + line.getAsString());
        }
        int bottom = y + h - 24;
        g.enableScissor(x + 8, top + 2, x + w - 8, bottom);
        offset = Math.max(0, Math.min(offset, Math.max(0, lines.size() * ROW - (bottom - top))));
        for (int i = 0; i < lines.size(); i++) {
            int ry = top + 4 + i * ROW - offset;
            if (ry > top - ROW && ry < bottom) g.drawText(fit(g, lines.get(i), w - 28), x + 14, ry, lines.get(i).startsWith("+") ? TEXT : MUTED);
        }
        g.disableScissor();
        if (!busy) {
            link(g, "Back", x + w - 100, y + h - 16, () -> selected = null, mx, my);
            if (!chosen.isEmpty()) link(g, "Import", x + w - 52, y + h - 16, this::apply, mx, my);
        }
    }

    private void link(LadsGraphics g, String text, int tx, int ty, Runnable action, int mx, int my) {
        Rect rect = new Rect(tx - 2, ty - 3, g.textWidth(text) + 4, 14);
        g.drawText(text, tx, ty, rect.contains(mx, my) ? TEXT : ACCENT);
        hits.add(new Hit(rect, action));
    }

    boolean click(double mx, double my, int button) {
        if (!open) return false;
        if (mx < x || mx >= x + w || my < y || my >= y + h) { if (!busy) open = false; return true; }
        if (button == 0) for (Hit hit : List.copyOf(hits)) if (hit.rect().contains(mx, my)) { hit.action().run(); break; }
        return true;
    }

    boolean wheel(double amount) { if (!open) return false; offset -= (int) (amount * 30 * SmoothScroll.FACTOR); return true; }

    boolean key(int key) {
        if (!open) return false;
        if (key == 256 && !busy) { if (selected != null) selected = null; else open = false; }
        return true;
    }
}
