package com.thelads.core.client.gui;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.client.gui.LadsSettingsScreen.Rect;
import com.thelads.core.client.sound.CustomSounds;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.config.SoundListOption;
import com.thelads.core.config.SoundOption;
import com.thelads.core.modules.ComboSoundsModule;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import static com.thelads.core.client.gui.MenuGraphics.*;

/**
 * The sound library every uploaded-sound slot picks from (Combo Sounds, Kill Banner "Your sounds", the bow sounds): the files in
 * the shared sounds folder, searchable by name, favourites first (Combo Sounds' Favourite Sounds, so on every version), each with
 * Play, Trim and Delete (asked first), and Upload new. A picked file, or Trim, opens the trimmer: the sound's waveform with
 * start and end handles (at most {@link CustomSounds#MAX_SECONDS} apart; the wheel zooms), Play selection, Save (a WAV of the
 * part beside the uploads, then chosen) and Use whole file. Opens over the settings screen like {@link ParticleRatesDialog}.
 */
public final class SoundLibraryDialog {
    private static final int ROW = 22;
    private static final double MIN_LENGTH = .05;
    /** Waveform buckets: the loudest sample of each run of this many frames. */
    private static final int BUCKET = 256;
    private record Hit(String id, Rect rect, Runnable action) {}
    private record Loaded(CustomSounds.Pcm pcm, short[] peaks) {}

    private final ConfirmDialog confirm;
    private final Runnable changed;
    private final List<Hit> hits = new ArrayList<>();
    /** Work finished off the game thread (a file dialog, a decode, a save), applied at the next frame. */
    private final Queue<Runnable> finished = new ConcurrentLinkedQueue<>();
    private boolean open;
    private String slot = "", current = "", query = "", notice = "";
    private double volume;
    private Consumer<String> onPick = file -> {};
    private List<String> files = List.of();
    private int offset, x, y, w, h;
    // The trimmer: its file (a library file, or a new one outside it), the decoded sound and the selection, in seconds.
    private boolean trimming;
    private Path trimSource;
    private String trimFile, trimName = "", trimError = "";
    private int trimToken, drag;
    private CustomSounds.Pcm pcm;
    private short[] peaks;
    private double total, start, end, viewStart, viewEnd, grab;
    private long previewAt;
    private Rect wave = new Rect(0, 0, 0, 0);

    SoundLibraryDialog(ConfirmDialog confirm, Runnable changed) {
        this.confirm = confirm;
        this.changed = changed;
    }

    public boolean isOpen() { return open; }
    public boolean isTrimming() { return open && trimming; }
    /** QA: the trimmer has its sound decoded. */
    public boolean trimmerReady() { return isTrimming() && pcm != null; }
    /** QA: the selection, {start, end, total} in seconds. */
    public double[] selection() { return new double[] {start, end, total}; }
    /** QA: the files listed, in their order. */
    public List<String> listed() { return filtered(); }
    /** QA: where the last frame drew a control ("upload", "row:<file>", "star:<file>", "trim:<file>", "wave", "save"...), or null. */
    public Rect bounds(String id) {
        if ("wave".equals(id)) return isTrimming() ? wave : null;
        for (Hit hit : hits) if (hit.id().equals(id)) return hit.rect();
        return null;
    }
    public String notice() { return notice; }

    /** Opens the library for {@code slot} ("Combo 1"); {@code pick} gets the chosen file; previews play at {@code volume}. */
    void open(String slot, String current, double volume, Consumer<String> pick) {
        this.slot = slot;
        this.current = current == null ? "" : current;
        this.volume = volume;
        this.onPick = pick;
        query = notice = "";
        offset = 0;
        trimming = false;
        open = true;
        refresh();
    }

    void close() {
        CustomSounds.stopPreview();
        trimToken++;
        open = trimming = false;
        pcm = null;
        peaks = null;
    }

    private void refresh() { files = CustomSounds.list(); }

    private static ComboSoundsModule combo() {
        return ModuleManager.getInstance().getModule(ComboSoundsModule.NAME) instanceof ComboSoundsModule module ? module : null;
    }

    private boolean favourite(String file) {
        ComboSoundsModule combo = combo();
        return combo != null && combo.favourites.contains(file);
    }

    /** Matching the search, favourites first, then by name. */
    private List<String> filtered() {
        String needle = query.toLowerCase(Locale.ROOT);
        List<String> shown = new ArrayList<>();
        for (String file : files) if (favourite(file) && file.toLowerCase(Locale.ROOT).contains(needle)) shown.add(file);
        for (String file : files) if (!favourite(file) && file.toLowerCase(Locale.ROOT).contains(needle)) shown.add(file);
        return shown;
    }

    private void pick(String file, String message) {
        close();
        onPick.accept(file);
        notice = message;
    }

    // The file dialog: the bridge's (each version's own); the one call site.
    private void upload() {
        notice = "Choose an MP3 or WAV file...";
        LadsGameBridge.get().chooseSoundFile().whenComplete((path, failure) -> finished.add(() -> {
            if (failure != null) notice = "No file dialog: " + failure.getMessage();
            else if (path == null) notice = "";
            else if (!CustomSounds.supported(path.getFileName().toString())) notice = "Choose an .mp3 or .wav file";
            else trim(path, null);
        }));
    }

    /** Opens the trimmer on {@code source}: {@code file} is its library name, or null for a new file. */
    public void trim(Path source, String file) {
        CustomSounds.stopPreview();
        trimming = true;
        trimSource = source;
        trimFile = file;
        trimName = file != null ? file : source.getFileName().toString();
        trimError = notice = "";
        pcm = null;
        peaks = null;
        drag = 0;
        int token = ++trimToken;
        CustomSounds.decodeForTrim(source).thenApply(decoded -> new Loaded(decoded, peaks(decoded))).whenComplete((loaded, failure) -> finished.add(() -> {
            if (token != trimToken) return;
            if (failure != null) trimError = "Can't read that file: " + cause(failure).getMessage();
            else {
                pcm = loaded.pcm();
                peaks = loaded.peaks();
                total = pcm.seconds();
                start = 0;
                end = Math.min(total, CustomSounds.MAX_SECONDS);
                viewStart = 0;
                viewEnd = total;
            }
        }));
    }

    private static Throwable cause(Throwable failure) {
        return failure.getCause() != null && failure instanceof java.util.concurrent.CompletionException ? failure.getCause() : failure;
    }

    /** The loudest sample (any channel) of each {@link #BUCKET} frames. */
    static short[] peaks(CustomSounds.Pcm pcm) {
        byte[] data = pcm.data();
        int frame = pcm.format().getFrameSize();
        short[] peaks = new short[(int) ((pcm.frames() + BUCKET - 1) / BUCKET)];
        for (int i = 0; i + 1 < data.length; i += 2) {
            int bucket = i / frame / BUCKET, sample = Math.abs((short) (data[i] & 0xFF | data[i + 1] << 8));
            if (sample > peaks[bucket]) peaks[bucket] = (short) Math.min(Short.MAX_VALUE, sample);
        }
        return peaks;
    }

    private void playSelection() {
        if (pcm == null) return;
        double rate = pcm.format().getSampleRate();
        int frame = pcm.format().getFrameSize();
        long from = Math.round(start * rate), to = Math.min(pcm.frames(), Math.round(end * rate));
        if (to <= from) return;
        CustomSounds.preview(new CustomSounds.Pcm(pcm.format(), java.util.Arrays.copyOfRange(pcm.data(), (int) (from * frame), (int) (to * frame))), volume, trimName);
        previewAt = System.nanoTime();
    }

    private void save() {
        if (pcm == null) return;
        CustomSounds.Pcm sound = pcm;
        String name = trimName;
        double from = start, to = end;
        notice = "Saving...";
        CustomSounds.async(() -> CustomSounds.saveTrim(name, sound, from, to)).whenComplete((stored, failure) -> finished.add(() -> {
            if (failure != null) notice = "Can't save it: " + cause(failure).getMessage();
            else pick(stored, "Saved " + stored);
        }));
    }

    private void useWhole() {
        if (trimFile != null) { pick(trimFile, ""); return; }
        Path source = trimSource;
        notice = "Copying...";
        CustomSounds.async(() -> CustomSounds.upload(source)).whenComplete((stored, failure) -> finished.add(() -> {
            if (failure != null) notice = "Can't use that file: " + cause(failure).getMessage();
            else pick(stored, "Added " + stored);
        }));
    }

    private void cancelTrim() {
        CustomSounds.stopPreview();
        trimToken++;
        pcm = null;
        peaks = null;
        trimming = false;
        refresh();
    }

    private void delete(String file) {
        confirm.open("Delete this sound?", file + " leaves your sounds folder (every version). Slots using it go back to their built-in sound.", () -> {
            try {
                CustomSounds.delete(file);
                for (Module module : ModuleManager.getInstance().getModules())
                    for (Option option : module.getOptions()) {
                        if (option instanceof SoundOption sound && sound.get().equals(file)) sound.set("");
                        if (option instanceof SoundListOption list) list.removeAll(file);
                    }
                ComboSoundsModule combo = combo();
                if (combo != null) combo.forget(file);
                if (current.equals(file)) current = "";
                changed.run();
                notice = "Deleted " + file;
            } catch (Exception failure) {
                notice = "Can't delete it: " + failure.getMessage();
            }
            refresh();
        });
    }

    // Drawing ---------------------------------------------------------------------------------------------------------------

    void render(LadsGraphics g, int mx, int my) {
        for (Runnable done; (done = finished.poll()) != null; ) done.run();
        if (!open) return;
        if (confirm.isOpen()) mx = my = -100000; // the delete question is in front
        hits.clear();
        w = Math.min(460, g.getScaledWidth() - 24);
        h = Math.min(320, g.getScaledHeight() - 24);
        x = (g.getScaledWidth() - w) / 2;
        y = (g.getScaledHeight() - h) / 2;
        g.fill(0, 0, g.getScaledWidth(), g.getScaledHeight(), 0xB8000000);
        round(g, x, y, w, h, LadsPalette.BORDER);
        round(g, x + 1, y + 1, w - 2, h - 2, PANEL);
        g.fill(x + 1, y, x + w - 1, y + 2, ACCENT);
        if (trimming) renderTrimmer(g, mx, my);
        else renderLibrary(g, mx, my);
    }

    private void button(LadsGraphics g, String id, String label, Rect r, Runnable action, boolean enabled, boolean primary, int mx, int my) {
        boolean hover = enabled && r.contains(mx, my);
        int color = !enabled ? CARD : primary ? hover ? LadsPalette.PRIMARY_HOVER : LadsPalette.PRIMARY : hover ? LadsPalette.HOVER : CARD;
        round(g, r.x(), r.y(), r.width(), r.height(), color);
        g.drawCenteredText(fit(g, label, r.width() - 6), r.x() + r.width() / 2, r.y() + (r.height() - g.fontHeight()) / 2 + 1, enabled ? TEXT : LadsPalette.DISABLED);
        if (enabled) hits.add(new Hit(id, r, action));
    }

    private void renderLibrary(LadsGraphics g, int mx, int my) {
        g.drawText(fit(g, slot.isEmpty() ? "Your sounds" : "Sounds for " + slot, w - 80), x + 12, y + 11, TEXT);
        button(g, "done", "Done", new Rect(x + w - 58, y + 7, 48, 18), this::close, true, false, mx, my);
        int uploadW = g.textWidth("Upload new...") + 16;
        Rect search = new Rect(x + 10, y + 30, w - 26 - uploadW, 20);
        round(g, search.x(), search.y(), search.width(), search.height(), CARD);
        g.drawText(fit(g, query.isEmpty() ? "Search sounds..." : query + "|", search.width() - 12), search.x() + 6, search.y() + 6, query.isEmpty() ? MUTED : TEXT);
        button(g, "upload", "Upload new...", new Rect(x + w - 10 - uploadW, y + 30, uploadW, 20), this::upload, true, true, mx, my);
        int top = y + 56, bottom = y + h - 20;
        List<String> shown = filtered();
        offset = Math.max(0, Math.min(offset, shown.size() * ROW - (bottom - top)));
        int playW = g.textWidth("Play") + 10, trimW = g.textWidth("Trim") + 10, delW = g.textWidth("Delete") + 10;
        g.enableScissor(x + 6, top, x + w - 6, bottom);
        for (int i = 0; i < shown.size(); i++) {
            String file = shown.get(i);
            int ry = top + i * ROW - offset;
            if (ry + ROW <= top || ry >= bottom) continue;
            Rect row = new Rect(x + 8, ry, w - 16, ROW - 2);
            boolean chosen = file.equals(current), hover = row.contains(mx, my) && my >= top && my < bottom;
            round(g, row.x(), row.y(), row.width(), row.height(), chosen ? LadsPalette.PRIMARY : hover ? LadsPalette.HOVER : CARD);
            boolean star = favourite(file);
            Rect starRect = new Rect(row.x() + 3, ry + 2, 16, 16);
            round(g, starRect.x(), starRect.y(), 16, 16, starRect.contains(mx, my) ? 0x40FFFFFF : 0x30000000);
            g.drawCenteredText(star ? "*" : "+", starRect.x() + 8, starRect.y() + 5, star ? 0xFFFFD24A : MUTED);
            int right = row.x() + row.width() - 3;
            Rect del = new Rect(right - delW, ry + 2, delW, 16), cut = new Rect(del.x() - 3 - trimW, ry + 2, trimW, 16), play = new Rect(cut.x() - 3 - playW, ry + 2, playW, 16);
            g.drawText(fit(g, file, play.x() - row.x() - 30), row.x() + 24, ry + 6, TEXT);
            if (ry >= top && ry + ROW - 2 <= bottom) { // whole rows only take clicks
                hits.add(new Hit("star:" + file, starRect, () -> { ComboSoundsModule combo = combo(); if (combo != null) { combo.favourites.toggle(file); changed.run(); } }));
                button(g, "play:" + file, "Play", play, () -> CustomSounds.play(file, volume), true, false, mx, my);
                button(g, "trim:" + file, "Trim", cut, () -> trim(CustomSounds.folder().resolve(file), file), true, false, mx, my);
                button(g, "delete:" + file, "Delete", del, () -> delete(file), true, false, mx, my);
                hits.add(new Hit("row:" + file, row, () -> pick(file, slot.isEmpty() ? "" : slot + ": " + file)));
            } else {
                button(g, "", "Play", play, null, false, false, mx, my);
                button(g, "", "Trim", cut, null, false, false, mx, my);
                button(g, "", "Delete", del, null, false, false, mx, my);
            }
        }
        g.disableScissor();
        if (shown.isEmpty()) g.drawText(files.isEmpty() ? "No sounds yet: Upload new adds an MP3 or WAV." : "No matching sounds.", x + 14, top + 6, MUTED);
        String hint = notice.isEmpty() ? files.size() + " sounds. Click one to use it; + stars a favourite." : notice;
        g.drawText(fit(g, hint, w - 24), x + 12, y + h - 14, notice.isEmpty() ? MUTED : ACCENT);
    }

    private double timeAt(double px) {
        return viewStart + (px - wave.x()) / Math.max(1, wave.width()) * (viewEnd - viewStart);
    }

    private int pixelAt(double time) {
        return wave.x() + (int) Math.round((time - viewStart) / Math.max(1e-9, viewEnd - viewStart) * wave.width());
    }

    private static String clock(double seconds) {
        int minutes = (int) (seconds / 60);
        return String.format(Locale.ROOT, "%d:%04.1f", minutes, seconds - minutes * 60);
    }

    private void renderTrimmer(LadsGraphics g, int mx, int my) {
        g.drawText(fit(g, "Trim " + trimName, w - 90), x + 12, y + 11, TEXT);
        button(g, "cancel", "Cancel", new Rect(x + w - 58, y + 7, 48, 18), this::cancelTrim, true, false, mx, my);
        wave = new Rect(x + 10, y + 44, w - 20, Math.max(30, h - 44 - 64));
        round(g, wave.x(), wave.y(), wave.width(), wave.height(), CARD);
        if (pcm == null) {
            String text = trimError.isEmpty() ? "Reading " + trimName + "..." : trimError;
            g.drawCenteredText(fit(g, text, wave.width() - 10), wave.x() + wave.width() / 2, wave.y() + wave.height() / 2 - 4, trimError.isEmpty() ? MUTED : ACCENT);
            return;
        }
        double length = end - start;
        g.drawText(fit(g, String.format(Locale.ROOT, "Length %s. Selection %.2f s to %.2f s (%.2f s of at most %d s)", clock(total), start, end, length,
            CustomSounds.MAX_SECONDS), w - 24), x + 12, y + 28, MUTED);
        // The waveform, each column the loudest bucket under it; the selection bright, the rest dim.
        int mid = wave.y() + wave.height() / 2, half = wave.height() / 2 - 3;
        int sx = pixelAt(start), ex = pixelAt(end);
        g.enableScissor(wave.x(), wave.y(), wave.x() + wave.width(), wave.y() + wave.height());
        g.fill(Math.max(wave.x(), sx), wave.y(), Math.min(wave.x() + wave.width(), ex), wave.y() + wave.height(), 0x40FF6666);
        double bucketSeconds = BUCKET / (double) pcm.format().getSampleRate();
        for (int c = 0; c < wave.width(); c++) {
            double t0 = timeAt(wave.x() + c), t1 = timeAt(wave.x() + c + 1);
            int b0 = (int) Math.max(0, Math.floor(t0 / bucketSeconds)), b1 = (int) Math.min(peaks.length, Math.max(b0 + 1, Math.ceil(t1 / bucketSeconds)));
            int peak = 0;
            for (int b = b0; b < b1; b++) peak = Math.max(peak, peaks[b]);
            int bar = Math.max(1, Math.round(peak / 32767f * half));
            double t = (t0 + t1) / 2;
            g.fill(wave.x() + c, mid - bar, wave.x() + c + 1, mid + bar, t >= start && t <= end ? ACCENT : 0xFF6E4A52);
        }
        for (int handle : new int[] {sx, ex}) { // the handles: a line and a grip at the top
            g.fill(handle - 1, wave.y(), handle + 1, wave.y() + wave.height(), TEXT);
            g.fill(handle - 3, wave.y(), handle + 3, wave.y() + 8, TEXT);
        }
        double played = (System.nanoTime() - previewAt) / 1e9;
        if (previewAt != 0 && played < length) {
            int px = pixelAt(start + played);
            g.fill(px, wave.y(), px + 1, wave.y() + wave.height(), 0xFFFFFFFF);
        }
        g.disableScissor();
        int labelY = wave.y() + wave.height() + 3;
        g.drawText(clock(viewStart), wave.x(), labelY, MUTED);
        String last = clock(viewEnd);
        g.drawText(last, wave.x() + wave.width() - g.textWidth(last), labelY, MUTED);
        String from = String.format(Locale.ROOT, "%.2f", start), to = String.format(Locale.ROOT, "%.2f", end);
        int fromX = Math.max(wave.x() + 30, Math.min(sx - g.textWidth(from) / 2, wave.x() + wave.width() - 30 - g.textWidth(from)));
        int toX = Math.max(fromX + g.textWidth(from) + 4, Math.min(ex - g.textWidth(to) / 2, wave.x() + wave.width() - 30 - g.textWidth(to)));
        g.drawText(from, fromX, labelY + 10, TEXT);
        g.drawText(to, toX, labelY + 10, TEXT);
        int by = y + h - 26, gap = 4, bw = (w - 20 - 3 * gap) / 4;
        button(g, "play", "Play selection", new Rect(x + 10, by, bw, 20), this::playSelection, true, false, mx, my);
        button(g, "save", "Save", new Rect(x + 10 + bw + gap, by, bw, 20), this::save, true, true, mx, my);
        button(g, "whole", total > CustomSounds.MAX_SECONDS ? "Use whole (first 12 s)" : "Use whole file", new Rect(x + 10 + 2 * (bw + gap), by, bw, 20), this::useWhole, true, false, mx, my);
        button(g, "back", trimFile == null ? "Cancel" : "Back", new Rect(x + 10 + 3 * (bw + gap), by, bw, 20), this::cancelTrim, true, false, mx, my);
        String hint = notice.isEmpty() ? "Drag the handles or the selection. Wheel zooms. Space plays, Enter saves." : notice;
        g.drawText(fit(g, hint, w - 24), x + 12, by - 12, notice.isEmpty() ? MUTED : ACCENT);
    }

    // Input -----------------------------------------------------------------------------------------------------------------

    boolean click(double mx, double my, int button) {
        if (!open) return false;
        if (!trimming && !new Rect(x, y, w, h).contains(mx, my)) { close(); return true; }
        if (button != 0) return true;
        if (trimming && pcm != null && wave.contains(mx, my)) { press(mx); return true; }
        for (Hit hit : List.copyOf(hits)) if (hit.rect().contains(mx, my)) { hit.action().run(); return true; }
        return true;
    }

    /** A press on the waveform: a handle (or the side of the selection it is on) moves; inside the selection the window moves. */
    private void press(double mx) {
        double t = Math.max(0, Math.min(total, timeAt(mx)));
        int sx = pixelAt(start), ex = pixelAt(end);
        if (Math.abs(mx - sx) <= 4 && Math.abs(mx - sx) <= Math.abs(mx - ex)) drag = 1;
        else if (Math.abs(mx - ex) <= 4) drag = 2;
        else if (t > start && t < end) { drag = 3; grab = t - start; }
        else drag = t < start ? 1 : 2;
        move(mx);
    }

    private void move(double mx) {
        double t = Math.max(0, Math.min(total, timeAt(mx))), max = CustomSounds.MAX_SECONDS;
        if (drag == 1) {
            start = Math.max(0, Math.min(t, end - MIN_LENGTH));
            if (end - start > max) end = start + max;
        } else if (drag == 2) {
            end = Math.min(total, Math.max(t, start + MIN_LENGTH));
            if (end - start > max) start = end - max;
        } else if (drag == 3) {
            double length = end - start;
            start = Math.max(0, Math.min(total - length, t - grab));
            end = start + length;
        }
    }

    boolean drag(double mx) {
        if (!open || drag == 0) return false;
        move(mx);
        return true;
    }

    boolean release() {
        if (!open || drag == 0) return false;
        drag = 0;
        return true;
    }

    boolean wheel(double mx, double amount) {
        if (!open) return false;
        if (!trimming) { offset -= (int) (amount * 30); return true; }
        if (pcm == null || total <= 0) return true;
        // Zoom about the pointer: down to a second across, out to the whole sound.
        double at = Math.max(viewStart, Math.min(viewEnd, timeAt(mx))), span = viewEnd - viewStart;
        double next = Math.max(Math.min(1, total), Math.min(total, span * (amount > 0 ? .8 : 1.25)));
        viewStart = Math.max(0, Math.min(total - next, at - (at - viewStart) * next / span));
        viewEnd = viewStart + next;
        return true;
    }

    boolean key(int key, int modifiers) {
        if (!open) return false;
        if (key == 256) { if (trimming) cancelTrim(); else close(); return true; }
        if (trimming) {
            if (key == 257 || key == 335) save();
            return true;
        }
        if (key == 259 && !query.isEmpty()) { query = query.substring(0, query.offsetByCodePoints(query.length(), -1)); offset = 0; }
        if (key == 65 && (modifiers & 2) != 0) { query = ""; offset = 0; }
        return true;
    }

    boolean type(int cp) {
        if (!open) return false;
        if (trimming) { if (cp == ' ') playSelection(); return true; }
        if (Character.isValidCodePoint(cp) && !Character.isISOControl(cp) && query.length() < 64) { query += new String(Character.toChars(cp)); offset = 0; }
        return true;
    }
}
