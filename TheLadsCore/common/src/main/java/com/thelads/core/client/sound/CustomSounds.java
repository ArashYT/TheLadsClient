package com.thelads.core.client.sound;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.shadow.javamp3.Sound;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sounds the player uploads (Combo Sounds, Kill Banner "Your sounds"), played through javax.sound.sampled on daemon threads, so
 * one player serves every game version without its sound engine. Uploads are copied into the shared Lads folder's sounds/ (next
 * to the shared config), so a choice follows the player across versions. MP3 (MPEG-1 Layer III, the usual 44.1/48 kHz file;
 * JavaMP3, vendored) and WAV. Each file is decoded once to 16-bit PCM and cached; sounds overlap (rapid hits), up to
 * {@link #VOICES} at once. A file that cannot be read is logged once and plays nothing; nothing here runs on the game thread
 * but the hand-off.
 */
public final class CustomSounds {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore/Sounds");
    /** Upload limit, and the longest sound kept (longer ones are cut), so a cache of ten sounds stays a few MB. */
    static final long MAX_FILE_BYTES = 20L << 20;
    static final int MAX_SECONDS = 12;
    static final int VOICES = 8;
    private static final Pcm FAILED = new Pcm(null, new byte[0]);
    private static final Map<String, Pcm> CACHE = new ConcurrentHashMap<>();
    private static final AtomicInteger PLAYING = new AtomicInteger();
    private static final ExecutorService POOL = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "Lads custom sound");
        thread.setDaemon(true);
        return thread;
    });
    /** QA: lines opened since start, and the last file played. */
    private static final AtomicInteger OPENED = new AtomicInteger();
    private static volatile String lastPlayed = "";

    private CustomSounds() {}

    /** Decoded sound: signed 16-bit little-endian PCM. */
    public record Pcm(AudioFormat format, byte[] data) {
        public long frames() { return format == null ? 0 : data.length / format.getFrameSize(); }
        public double seconds() { return format == null ? 0 : frames() / (double) format.getSampleRate(); }
    }

    public static Path folder() {
        return ClientPaths.getSharedDir().resolve("sounds");
    }

    public static boolean supported(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        return name.endsWith(".mp3") || name.endsWith(".wav");
    }

    /**
     * Copies {@code source} into {@link #folder} and returns the name stored in the option. The same file uploaded again reuses
     * its copy; another file with the same name gets " (2)", " (3)"... Throws with a message for the player when it can't.
     */
    public static String upload(Path source) throws IOException {
        String name = source.getFileName().toString().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (!supported(name)) throw new IOException("Choose an .mp3 or .wav file");
        if (Files.size(source) > MAX_FILE_BYTES) throw new IOException("That file is over 20 MB");
        byte[] bytes = Files.readAllBytes(source);
        decode(name, new java.io.ByteArrayInputStream(bytes)); // refuse a file that won't play now, not at the next hit
        Path folder = folder();
        Files.createDirectories(folder);
        int dot = name.lastIndexOf('.');
        String stem = name.substring(0, dot), ext = name.substring(dot);
        for (int n = 1; ; n++) {
            String candidate = n == 1 ? name : stem + " (" + n + ")" + ext;
            Path target = folder.resolve(candidate);
            if (!Files.exists(target)) { Files.write(target, bytes); return candidate; }
            if (Files.size(target) == bytes.length && Arrays.equals(Files.readAllBytes(target), bytes)) return candidate;
        }
    }

    /** Plays the uploaded {@code file} at {@code volume} (0 to 1) times the game's master volume; returns at once. */
    public static void play(String file, double volume) {
        if (file == null || file.isEmpty()) return;
        float gain = (float) Math.max(0, Math.min(1, volume * LadsGameBridge.get().getMasterVolume()));
        if (gain <= 0 || CACHE.get(file) == FAILED) return;
        if (PLAYING.incrementAndGet() > VOICES) { PLAYING.decrementAndGet(); return; }
        try {
            POOL.execute(() -> {
                try {
                    Pcm pcm = load(file);
                    if (pcm != FAILED) write(pcm, gain, file);
                } finally {
                    PLAYING.decrementAndGet();
                }
            });
        } catch (RuntimeException rejected) {
            PLAYING.decrementAndGet();
        }
    }

    /** The cached sound, decoded now if new; FAILED (logged once) when it can't be read. */
    static Pcm load(String file) {
        return CACHE.computeIfAbsent(file, name -> {
            Path path = folder().resolve(name);
            try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
                Pcm pcm = decode(name, in);
                LOGGER.info("Custom sound {}: {} frames, {} s, {} Hz, {} channel(s)", name, pcm.frames(),
                    String.format(Locale.ROOT, "%.2f", pcm.seconds()), (int) pcm.format().getSampleRate(), pcm.format().getChannels());
                return pcm;
            } catch (Exception | LinkageError failure) {
                LOGGER.warn("Custom sound {} can't be played ({}): {}", name, path, failure.toString());
                return FAILED;
            }
        });
    }

    /** Decodes an MP3 or WAV (by name) to 16-bit little-endian PCM, at most {@link #MAX_SECONDS}. */
    static Pcm decode(String name, InputStream in) throws IOException {
        if (name.toLowerCase(Locale.ROOT).endsWith(".mp3")) {
            in = skipId3(in);
            try (Sound sound = new Sound(in)) {
                return new Pcm(sound.getAudioFormat(), readCapped(sound, sound.getAudioFormat()));
            }
        }
        try (AudioInputStream source = AudioSystem.getAudioInputStream(in.markSupported() ? in : new BufferedInputStream(in))) {
            AudioFormat from = source.getFormat();
            AudioFormat pcm = new AudioFormat(from.getSampleRate(), 16, from.getChannels(), true, false);
            try (AudioInputStream converted = AudioSystem.getAudioInputStream(pcm, source)) {
                return new Pcm(pcm, readCapped(converted, pcm));
            }
        } catch (javax.sound.sampled.UnsupportedAudioFileException | IllegalArgumentException unsupported) {
            throw new IOException("Not a supported sound file: " + unsupported.getMessage(), unsupported);
        }
    }

    /** An ID3v2 tag before the first frame is skipped whole (its picture could look like an MPEG frame start). */
    private static InputStream skipId3(InputStream in) throws IOException {
        InputStream buffered = in.markSupported() ? in : new BufferedInputStream(in);
        buffered.mark(10);
        byte[] header = new byte[10];
        int read = 0;
        for (int n; read < 10 && (n = buffered.read(header, read, 10 - read)) > 0; ) read += n;
        if (read == 10 && header[0] == 'I' && header[1] == 'D' && header[2] == '3') {
            long size = (header[6] & 0x7F) << 21 | (header[7] & 0x7F) << 14 | (header[8] & 0x7F) << 7 | header[9] & 0x7F;
            if ((header[5] & 0x10) != 0) size += 10; // footer
            while (size > 0) {
                long skipped = buffered.skip(size);
                if (skipped <= 0) { if (buffered.read() < 0) break; skipped = 1; }
                size -= skipped;
            }
        } else buffered.reset();
        return buffered;
    }

    private static byte[] readCapped(InputStream pcm, AudioFormat format) throws IOException {
        long cap = (long) (format.getSampleRate() * format.getFrameSize() * MAX_SECONDS);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int n; out.size() < cap && (n = pcm.read(buffer)) > 0; ) out.write(buffer, 0, n);
        byte[] data = out.toByteArray();
        int whole = (int) Math.min(data.length, cap) / format.getFrameSize() * format.getFrameSize();
        if (whole == 0) throw new IOException("No sound in the file");
        return whole == data.length ? data : Arrays.copyOf(data, whole);
    }

    /** Streams the sound into a new line at {@code gain}, on the calling (pool) thread. */
    private static void write(Pcm pcm, float gain, String file) {
        try (SourceDataLine line = AudioSystem.getSourceDataLine(pcm.format())) {
            line.open(pcm.format());
            line.start();
            OPENED.incrementAndGet();
            lastPlayed = file;
            byte[] data = pcm.data(), chunk = new byte[4096];
            for (int at = 0; at < data.length; at += chunk.length) {
                int n = Math.min(chunk.length, data.length - at);
                for (int i = 0; i + 1 < n; i += 2) {
                    int sample = Math.round((short) (data[at + i] & 0xFF | data[at + i + 1] << 8) * gain);
                    chunk[i] = (byte) sample;
                    chunk[i + 1] = (byte) (sample >> 8);
                }
                line.write(chunk, 0, n);
            }
            line.drain();
        } catch (Exception | LinkageError failure) {
            if (FAILED_LINES.add(failure.getClass().getName())) LOGGER.warn("Custom sound {}: no audio line ({})", file, failure.toString());
        }
    }
    private static final java.util.Set<String> FAILED_LINES = ConcurrentHashMap.newKeySet();
    private static final java.util.concurrent.atomic.AtomicBoolean DIALOG_OPEN = new java.util.concurrent.atomic.AtomicBoolean();

    /** QA: sound lines opened so far, and the last file played. */
    public static int linesOpened() { return OPENED.get(); }
    public static String lastPlayed() { return lastPlayed; }
    /** QA/preview: the decoded sound (null when it can't be read). */
    public static Pcm decoded(String file) { Pcm pcm = load(file); return pcm == FAILED ? null : pcm; }

    /**
     * The open-file dialog where the game has no native one (1.8.9): AWT's, on its own thread so the game keeps drawing.
     * Completes with null when cancelled.
     */
    public static CompletableFuture<Path> awtChooser(String title) {
        return awtChooser(title, "*.mp3;*.wav");
    }

    /** The same dialog listing only files that match {@code patterns} ("*.png;*.jpg"). */
    public static CompletableFuture<Path> awtChooser(String title, String patterns) {
        CompletableFuture<Path> result = new CompletableFuture<>();
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            result.completeExceptionally(new IllegalStateException("No file dialog in this game"));
            return result;
        }
        if (!DIALOG_OPEN.compareAndSet(false, true)) {
            result.completeExceptionally(new IllegalStateException("A file dialog is already open"));
            return result;
        }
        Thread thread = new Thread(() -> {
            java.awt.FileDialog dialog = null;
            try {
                dialog = new java.awt.FileDialog((java.awt.Frame) null, title, java.awt.FileDialog.LOAD);
                dialog.setFile(patterns); // the Windows dialog's filter
                dialog.setFilenameFilter((dir, name) -> java.util.Arrays.stream(patterns.split(";"))
                    .anyMatch(pattern -> name.toLowerCase(Locale.ROOT).endsWith(pattern.substring(1).toLowerCase(Locale.ROOT))));
                dialog.setAlwaysOnTop(true);
                dialog.setVisible(true);
                String file = dialog.getFile(), directory = dialog.getDirectory();
                result.complete(file == null ? null : directory == null ? Path.of(file) : Path.of(directory, file));
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            } finally {
                if (dialog != null) dialog.dispose();
                DIALOG_OPEN.set(false);
            }
        }, "Lads file dialog");
        thread.setDaemon(true);
        thread.start();
        return result;
    }
}
