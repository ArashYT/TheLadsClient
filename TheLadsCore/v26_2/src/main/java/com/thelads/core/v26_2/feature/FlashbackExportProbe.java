package com.thelads.core.v26_2.feature;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.combo_options.ExportProjection;
import com.moulberry.flashback.combo_options.VideoCodec;
import com.moulberry.flashback.combo_options.VideoContainer;
import com.moulberry.flashback.exporting.ExportJob;
import com.moulberry.flashback.exporting.ExportSettings;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.screen.select_replay.SelectReplayScreen;
import com.thelads.core.v26_2.feature.flashback.NativeFlashback;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/**
 * QA only (-Dthelads.verifyFlashback, ".lads-qa-flashback" from the harness's LADS_VERIFY_FLASHBACK): records a real replay into
 * a Lads replay folder, reopens it, exports one clip as stock Flashback and with Lads' faster exports (timing each, checking the
 * frames), and shows the replay in Flashback's browser.
 */
public final class FlashbackExportProbe {
    private record Run(String name, boolean stock, VideoContainer container, VideoCodec codec, String encoder, int width, int height, int fps, int end) {
        boolean png() { return container == VideoContainer.PNG_SEQUENCE; }
    }
    private static List<Run> runs;
    private static boolean done;
    private static int stage, ticks, runIndex, passed;
    private static long deadline, runStarted, browserAt, listedAt;
    private static Path game, folder, ownFolder, replay, exports, output;
    private static Set<Path> ownBefore, folderBefore;
    private static String previousFolder;
    private static boolean quicksave, browserShot;
    private static Boolean previousGpu;
    private static float yaw, cameraYaw, cameraPitch;
    private static net.minecraft.world.phys.Vec3 camera;
    private static final List<String> results = new ArrayList<>();
    private FlashbackExportProbe() {}

    public static void register() { ClientTickEvents.END_CLIENT_TICK.register(mc -> tick()); }

    private static void tick() {
        if (done) return;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (stage == 0) { begin(mc); return; }
            if (System.nanoTime() > deadline) throw new IllegalStateException("timed out at stage " + stage);
            switch (stage) {
                case 1 -> record(mc);
                case 2 -> saved();
                case 3 -> opened(mc);
                case 4 -> exported(mc);
                case 5, 6 -> browser(mc);
                default -> {}
            }
        } catch (Throwable failure) {
            done = true;
            NativeFlashback.qaStock = false;
            try {
                restore();
                if (stage == 1 && Flashback.RECORDER != null) { Flashback.finishRecordingReplay(); Flashback.getConfig().recordingControls.quicksave = quicksave; }
            } catch (Throwable suppressed) { failure.addSuppressed(suppressed); }
            try { if (game != null) Files.writeString(game.resolve(".lads-qa-flashback-failed"), failure.toString()); } catch (Exception ignored) {}
            NativeFlashback.LOGGER.error("Lads Flashback probe FAILED at stage " + stage, failure);
        }
    }

    private static void begin(Minecraft mc) throws Exception {
        Path request = mc.gameDirectory.toPath().resolve(".lads-qa-flashback");
        if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS) || !NativeWorldVerification.worldReady()) return;
        game = NativeWorldVerification.checkedGameDirectory(mc.gameDirectory.toPath());
        Files.delete(request);
        var module = NativeFlashback.module();
        previousFolder = module.replayFolder.getValue();
        previousGpu = module.gpuEncoder.get();
        module.gpuEncoder.set(true);
        module.replayFolder.setValue("");
        ownFolder = Flashback.getReplayFolder().toAbsolutePath().normalize();
        check(ownFolder.startsWith(game), "Flashback's own replay folder is in the sandbox: " + ownFolder);
        Path notAFolder = game.resolve(".lads-qa-not-a-folder");
        Files.writeString(notAFolder, "a file, not a folder");
        module.replayFolder.setValue(notAFolder.toString());
        check(Flashback.getReplayFolder().toAbsolutePath().normalize().equals(ownFolder), "an unusable replay folder falls back to Flashback's own");
        Files.delete(notAFolder);
        folder = game.resolve("lads-qa-replays");
        module.replayFolder.setValue(folder.toString());
        check(Flashback.getReplayFolder().equals(folder) && Files.isDirectory(folder), "Flashback.getReplayFolder() is the Lads folder, created");
        ownBefore = zips(ownFolder);
        folderBefore = zips(folder);
        check(Flashback.RECORDER == null, "no recording is running");
        var controls = Flashback.getConfig().recordingControls;
        quicksave = controls.quicksave;
        controls.quicksave = true;
        yaw = mc.player.getYRot();
        Flashback.startRecordingReplay();
        check(Flashback.RECORDER != null, "Flashback started recording");
        stage = 1;
        deadline = System.nanoTime() + 480_000_000_000L;
        NativeFlashback.LOGGER.info("Lads Flashback probe BEGIN: recording 100 ticks into {}; H.264 encoders working here: {}", folder,
            Arrays.toString(VideoCodec.H264.getEncoders()));
    }

    private static void record(Minecraft mc) throws Exception {
        if (!NativeWorldVerification.worldReady()) return;
        mc.player.setYRot(yaw + ++ticks * .9f);
        if (ticks < 100) return;
        Flashback.finishRecordingReplay();
        mc.player.setYRot(yaw);
        Flashback.getConfig().recordingControls.quicksave = quicksave;
        check(Flashback.RECORDER == null, "Flashback finished the recording");
        stage = 2;
    }

    private static void saved() throws Exception {
        Set<Path> created = zips(folder);
        created.removeAll(folderBefore);
        if (created.isEmpty()) return;
        check(created.size() == 1, "one replay saved in the Lads folder: " + created);
        replay = created.iterator().next();
        check(Files.size(replay) > 1024, "the replay holds recorded data");
        check(zips(ownFolder).equals(ownBefore), "nothing was saved to Flashback's own folder");
        NativeFlashback.LOGGER.info("Lads Flashback probe SAVED: {} ({} bytes)", replay, Files.size(replay));
        Flashback.openReplayWorld(replay);
        stage = 3;
    }

    private static void opened(Minecraft mc) throws Exception {
        ReplayServer server = Flashback.getReplayServer();
        if (server == null || !server.isReady() || mc.level == null || mc.player == null) return;
        check(Flashback.isInReplay() && server.getTotalReplayTicks() >= 80, "the Lads-folder replay opened with its recorded ticks");
        exports = Files.createDirectories(game.resolve("flashback-qa-" + System.currentTimeMillis()));
        // One camera for every export, so stock and Lads frames can be compared pixel for pixel.
        camera = mc.player.position().add(0, mc.player.getEyeHeight(), 0);
        cameraYaw = mc.player.getYRot();
        cameraPitch = mc.player.getXRot();
        VideoContainer png = VideoContainer.PNG_SEQUENCE, mp4 = VideoContainer.MP4;
        runs = List.of(
            new Run("warmup", true, png, VideoCodec.PNG, "png", 320, 180, 20, 20),
            new Run("png-stock", true, png, VideoCodec.PNG, "png", 1920, 1080, 30, 70),
            new Run("png-lads", false, png, VideoCodec.PNG, "png", 1920, 1080, 30, 70),
            new Run("png-stock-2", true, png, VideoCodec.PNG, "png", 1920, 1080, 30, 70),
            new Run("png-lads-2", false, png, VideoCodec.PNG, "png", 1920, 1080, 30, 70),
            new Run("mp4-openh264-stock", true, mp4, VideoCodec.H264, "libopenh264", 1920, 1080, 30, 70),
            new Run("mp4-openh264-lads", false, mp4, VideoCodec.H264, "libopenh264", 1920, 1080, 30, 70),
            new Run("mp4-openh264-stock-1440p60", true, mp4, VideoCodec.H264, "libopenh264", 2560, 1440, 60, 90),
            new Run("mp4-openh264-lads-1440p60", false, mp4, VideoCodec.H264, "libopenh264", 2560, 1440, 60, 90));
        start(server, mc);
        stage = 4;
    }

    private static void start(ReplayServer server, Minecraft mc) throws Exception {
        Run run = runs.get(runIndex);
        NativeFlashback.qaStock = run.stock();
        output = run.png() ? Files.createDirectories(exports.resolve(run.name())) : exports.resolve(run.name() + ".mp4");
        var settings = new ExportSettings(run.name(), server.getEditorState(), camera, cameraYaw, cameraPitch, run.width(), run.height(), 10, run.end(), ExportProjection.PERSPECTIVE, 1f, run.fps(),
            true, false, run.container(), run.codec(), run.encoder(), run.png() ? 0 : 20_000_000, false, false, true, false, null, output, "frame-%04d");
        check(Flashback.EXPORT_JOB == null, "no other export is running");
        Flashback.EXPORT_JOB = new ExportJob(settings);
        runStarted = System.nanoTime();
    }

    private static void exported(Minecraft mc) throws Exception {
        if (Flashback.EXPORT_JOB != null) return;
        long ms = (System.nanoTime() - runStarted) / 1_000_000;
        Run run = runs.get(runIndex);
        String line;
        if (run.png()) {
            List<Path> frames;
            try (var files = Files.list(output)) { frames = files.filter(p -> p.toString().endsWith(".png")).sorted().toList(); }
            check(frames.size() >= 10, run.name() + " wrote PNG frames");
            BufferedImage first = javax.imageio.ImageIO.read(frames.getFirst().toFile()), last = javax.imageio.ImageIO.read(frames.getLast().toFile());
            check(first.getWidth() == run.width() && last.getHeight() == run.height(), run.name() + " frames decode at the asked size");
            long bytes = 0;
            for (Path frame : frames) bytes += Files.size(frame);
            line = String.format("%s: %d ms, %d frames, %d bytes, alpha=%s", run.name(), ms, frames.size(), bytes, first.getColorModel().hasAlpha());
            if (run.name().startsWith("png-lads")) {
                check(!first.getColorModel().hasAlpha(), "Lads PNG frames carry no alpha channel");
                List<Path> stock;
                try (var files = Files.list(exports.resolve("png-stock"))) { stock = files.filter(p -> p.toString().endsWith(".png")).sorted().toList(); }
                check(stock.size() == frames.size(), "same frame count as stock Flashback (" + stock.size() + ")");
                line += ", RGB pixels differing from stock in the middle frame: " + differing(stock.get(stock.size() / 2), frames.get(frames.size() / 2));
            }
        } else {
            check(Files.isRegularFile(output) && Files.size(output) > 10_000, run.name() + " wrote a video");
            line = String.format("%s: %d ms, %d bytes", run.name(), ms, Files.size(output));
        }
        results.add(line + " -> " + output);
        NativeFlashback.LOGGER.info("Lads Flashback probe RUN {}", line);
        if (++runIndex < runs.size()) { start(Flashback.getReplayServer(), mc); return; }
        NativeFlashback.qaStock = false;
        SelectReplayScreen browser = new SelectReplayScreen(mc.gui.screen());
        check(browser.path.equals(folder), "Flashback's replay browser opens the Lads folder");
        mc.setScreenAndShow(browser);
        browserAt = System.nanoTime();
        stage = 5;
    }

    /** Flashback's browser on the Lads folder, then the Flashback Settings page of the Lads menu: each checked and photographed. */
    private static void browser(Minecraft mc) throws Exception {
        long since = System.nanoTime() - browserAt;
        if (stage == 5) {
            if (since < 2_000_000_000L) return;
            if (listedAt == 0) {
                // The browser reads the folder in the background, which a busy PC can take longer than 2 s for: polled for up to 20 s.
                var list = SelectReplayScreen.class.getDeclaredField("list");
                list.setAccessible(true);
                var entries = ((net.minecraft.client.gui.components.AbstractSelectionList<?>) list.get(mc.gui.screen())).children();
                boolean listed = entries.stream().anyMatch(entry -> ((Object) entry).getClass().getSimpleName().equals("ReplayListEntry"));
                if (!listed && since < 20_000_000_000L) return;
                check(listed, "the browser lists the saved replay (after " + since / 1_000_000 + " ms)");
                listedAt = System.nanoTime();
                return;
            }
            long shown = System.nanoTime() - listedAt;
            if (shown < 500_000_000L) return; // drawn with the replay listed before the frame is taken
            if (!browserShot) { browserShot = true; shot(mc, "flashback-browser"); return; }
            if (shown < 1_500_000_000L) return;
            var menu = new com.thelads.core.v26_2.gui.LadsSettingsScreen26(null);
            mc.setScreenAndShow(menu);
            var ui = com.thelads.core.v26_2.gui.LadsSettingsScreen26.class.getDeclaredField("ui");
            ui.setAccessible(true);
            ((com.thelads.core.client.gui.LadsSettingsScreen) ui.get(menu)).openModule(com.thelads.core.modules.FlashbackModule.NAME);
            browserAt = System.nanoTime();
            browserShot = false;
            stage = 6;
            return;
        }
        if (since < 1_500_000_000L) return;
        if (!browserShot) { browserShot = true; shot(mc, "flashback-settings-menu"); return; }
        if (since < 2_500_000_000L) return;
        mc.setScreenAndShow(null);
        restore();
        check(Flashback.getReplayFolder().toAbsolutePath().normalize().equals(ownFolder), "a blank folder is Flashback's own again");
        Files.writeString(game.resolve(".lads-qa-flashback-done"), "replay=" + replay + System.lineSeparator() + String.join(System.lineSeparator(), results) + System.lineSeparator());
        done = true;
        NativeFlashback.LOGGER.info("Lads Flashback probe END: {} passed, 0 failed; {}", passed, String.join("; ", results));
    }

    private static void shot(Minecraft mc, String name) throws Exception {
        Path file = Files.createDirectories(game.resolve("screenshots")).resolve(name + "-" + System.currentTimeMillis() + ".png");
        net.minecraft.client.Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
            try { image.writeToFile(file); results.add(name + " screenshot -> " + file); } catch (Exception e) { NativeFlashback.LOGGER.warn(name + " capture", e); }
            finally { image.close(); }
        });
    }

    private static int differing(Path stock, Path lads) throws Exception {
        BufferedImage a = javax.imageio.ImageIO.read(stock.toFile()), b = javax.imageio.ImageIO.read(lads.toFile());
        int count = 0;
        for (int y = 0; y < a.getHeight(); y++) for (int x = 0; x < a.getWidth(); x++) if (((a.getRGB(x, y) ^ b.getRGB(x, y)) & 0xFFFFFF) != 0) count++;
        return count;
    }

    private static void restore() {
        if (previousGpu != null) NativeFlashback.module().gpuEncoder.set(previousGpu);
        previousGpu = null;
        if (previousFolder != null) NativeFlashback.module().replayFolder.setValue(previousFolder);
        previousFolder = null;
    }

    private static Set<Path> zips(Path dir) throws Exception {
        if (!Files.isDirectory(dir)) return new HashSet<>();
        try (var files = Files.list(dir)) { return new HashSet<>(files.filter(p -> p.toString().endsWith(".zip")).toList()); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
        passed++;
    }
}
