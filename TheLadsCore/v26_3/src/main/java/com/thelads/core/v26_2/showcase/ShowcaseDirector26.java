package com.thelads.core.v26_2.showcase;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.platform.InputConstants;
import com.thelads.core.client.cosmetics.Emotes;
import com.thelads.core.client.gui.DraggableHudScreen;
import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.client.gui.LadsSettingsScreen.Rect;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.ColorOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.config.SliderOption;
import com.thelads.core.config.StringOption;
import com.thelads.core.config.TextOption;
import com.thelads.core.v26_2.cosmetics.anim.CosmeticsScreen26;
import com.thelads.core.v26_2.cosmetics.anim.EmoteWheelScreen26;
import com.thelads.core.v26_2.feature.crosshair.CrosshairDrawingScreen;
import com.thelads.core.v26_2.gui.DraggableHudScreen26;
import com.thelads.core.v26_2.gui.LadsSettingsScreen26;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLVideo;
import com.thelads.core.config.HudSettings;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ShowcaseDirector26 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final String DIRECTOR_ENV = System.getenv("LADS_SHOWCASE_DIRECTOR") != null
        ? System.getenv("LADS_SHOWCASE_DIRECTOR") : System.getProperty("lads.showcase.director");
    private static final String SERVER_TARGET = System.getenv("LADS_SHOWCASE_SERVER") != null
        ? System.getenv("LADS_SHOWCASE_SERVER") : System.getProperty("lads.showcase.server");
    private static final String SPOT_ENV = System.getenv("LADS_SHOWCASE_SPOT") != null
        ? System.getenv("LADS_SHOWCASE_SPOT") : System.getProperty("lads.showcase.spot");
    private static final String JOIN_CMD = System.getenv("LADS_SHOWCASE_JOIN_CMD") != null
        ? System.getenv("LADS_SHOWCASE_JOIN_CMD") : System.getProperty("lads.showcase.join_cmd");
    private static final boolean SECONDARY = "1".equals(System.getenv("LADS_SHOWCASE_SECONDARY"))
        || "1".equals(System.getProperty("lads.showcase.secondary"))
        || "true".equalsIgnoreCase(System.getenv("LADS_SHOWCASE_SECONDARY"))
        || "true".equalsIgnoreCase(System.getProperty("lads.showcase.secondary"));
    private static final String SAVE = System.getenv().getOrDefault("LADS_SHOWCASE_SAVE", "LadsShowcase"); // a second game needs its own world: saves is a shared junction
    private static final int FLOOR = 12;
    private static long connectAt;
    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ShowcaseDirector-Scheduler");
        t.setDaemon(true);
        return t;
    });

    private static final String[] SETUP_COMMANDS = new String[] {
        "gamerule advance_time false", "gamerule advance_weather false",
        "time set noon", "weather clear",
        "gamerule spawn_phantoms false", "gamerule show_death_messages false", "gamerule announce_advancements false",
        "advancement grant @s everything", "recipe give @s *",
        "gamerule spawn_monsters false", "gamerule spawnMonsters false", "gamerule doMobSpawning false",
        "gamerule announceAdvancements false", "difficulty easy"
    };

    public enum CommandRoute {
        SERVER_CHAT,
        INTEGRATED_SERVER
    }

    public static boolean isServerMode() {
        return SERVER_TARGET != null && !SERVER_TARGET.isBlank();
    }

    public static boolean isServerMode(String serverTarget) {
        return serverTarget != null && !serverTarget.isBlank();
    }

    public static boolean isSecondary() {
        return SECONDARY;
    }

    public static String getServerTarget() {
        return SERVER_TARGET;
    }

    public static String getJoinCmd() {
        return JOIN_CMD;
    }

    public static CommandRoute routeCommand(boolean isServerMode) {
        return isServerMode ? CommandRoute.SERVER_CHAT : CommandRoute.INTEGRATED_SERVER;
    }

    public static String resolveWindowTitle(boolean secondary, String playerName) {
        if (secondary) {
            return "Lads Feature Showcase (" + (playerName != null && !playerName.isBlank() ? playerName : "Player") + ")";
        }
        return "Lads Feature Showcase";
    }

    public static double[] parseSpot(String spotEnv) {
        if (spotEnv == null || spotEnv.isBlank()) {
            return new double[] { 0.0, 80.0, 0.0 };
        }
        String[] parts = spotEnv.trim().split("[,\\s]+");
        if (parts.length >= 3) {
            try {
                return new double[] {
                    Double.parseDouble(parts[0]),
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2])
                };
            } catch (NumberFormatException ignored) {}
        }
        return new double[] { 0.0, 80.0, 0.0 };
    }

    public static double[] parseSpotFromEnv() {
        String env = System.getenv("LADS_SHOWCASE_SPOT");
        if (env == null || env.isBlank()) {
            env = System.getProperty("lads.showcase.spot");
        }
        if (env == null || env.isBlank()) {
            env = SPOT_ENV;
        }
        return parseSpot(env);
    }

    public static String formatTpCommand(String target, double x, double y, double z, float yaw, float pitch) {
        return String.format(Locale.ROOT, "tp %s %.2f %.2f %.2f %.1f %.1f", target, x, y, z, yaw, pitch);
    }

    public static String formatPlayerCommand(String command) {
        if (command == null) return "";
        command = command.trim();
        if (command.startsWith("/")) {
            return command.substring(1);
        }
        return command;
    }

    public static void sendPlayerCommand(Minecraft mc, String command) {
        String cmd = formatPlayerCommand(command);
        if (cmd.isEmpty()) return;
        mc.execute(() -> {
            if (mc.player != null && mc.player.connection != null) {
                mc.player.connection.sendCommand(cmd);
            }
        });
    }

    public static List<String> buildResetCommands(boolean isServerMode, double spotX, double groundY, double spotZ) {
        List<String> list = new ArrayList<>();
        String who = isServerMode ? "@s" : "@a";
        list.add("kill @e[type=!player]");
        list.add("scoreboard objectives setdisplay sidebar"); // a sidebar from one take (hud-12) leaked into later ones
        list.add("title @a clear");
        if (!isServerMode) list.addAll(snapshotCommands(false, spotX, groundY, spotZ));
        list.add("clear " + who);
        list.add("gamemode creative " + who);
        list.add("effect clear " + who);
        list.add("time set noon");
        list.add("weather clear");
        list.add(formatTpCommand(isServerMode ? "@s" : "@a", spotX, groundY, spotZ, 0f, 0f));
        return list;
    }

    /**
     * Scripts change the world (fills, builds, drops); `reset` puts the blocks around the home lawn back. The box x/z +-32, y-6..y+30 is
     * cloned once into the deepslate right under it (y -60..-24) and cloned back on every reset.
     */
    public static List<String> snapshotCommands(boolean save, double spotX, double groundY, double spotZ) {
        int x = (int) Math.floor(spotX), y = (int) Math.floor(groundY), z = (int) Math.floor(spotZ);
        List<String> out = new ArrayList<>();
        if (save) { // a barrier shell around the store: cave water and lava would flow into its air and come back up on reset
            int a = x - 33, b = x + 33, c = z - 33, d = z + 33;
            out.add(String.format(Locale.ROOT, "fill %d -61 %d %d -61 %d barrier", a, c, b, d));
            out.add(String.format(Locale.ROOT, "fill %d -23 %d %d -23 %d barrier", a, c, b, d));
            out.add(String.format(Locale.ROOT, "fill %d -61 %d %d -23 %d barrier", a, c, a, d));
            out.add(String.format(Locale.ROOT, "fill %d -61 %d %d -23 %d barrier", b, c, b, d));
            out.add(String.format(Locale.ROOT, "fill %d -61 %d %d -23 %d barrier", a, c, b, c));
            out.add(String.format(Locale.ROOT, "fill %d -61 %d %d -23 %d barrier", a, d, b, d));
        }
        for (int z0 = z - 32; z0 <= z + 32; z0 += 13) { // 65 x 37 x 13 = 31,265 blocks per clone: under the default 32,768 limit
            int z1 = Math.min(z0 + 12, z + 32);
            out.add(save ? String.format(Locale.ROOT, "clone %d %d %d %d %d %d %d -60 %d", x - 32, y - 6, z0, x + 32, y + 30, z1, x - 32, z0)
                         : String.format(Locale.ROOT, "clone %d -60 %d %d -24 %d %d %d %d", x - 32, z0, x + 32, z1, x - 32, y - 6, z0));
        }
        return out;
    }

    private static double homeX, homeY, homeZ;

    public static volatile boolean active;
    private static Path dir;
    private static boolean worldAsked, setUp, ready;
    private static long t0, titleSince, nextPoll, nextFps;
    private static int lastCmd = -1;
    /** Watchdog: one step may not hold the director longer than this (a hung script blocked every lane's queue). */
    private static final long STEP_LIMIT_MS = 240_000;
    private static volatile boolean abortStep;
    private static volatile long stepStartedAt;
    private static volatile Thread currentWorker;
    private static double spotX, groundY, spotZ;
    private static final AtomicBoolean scriptRunning = new AtomicBoolean(false);
    private static volatile int currentStep = -1;
    private static volatile int checkStep = -1; // "<n> check <file>": a dry run that screenshots every mark, nothing captured in real takes

    // Baseline snapshot for reset verb
    private static final Map<String, Boolean> baselineModuleState = new HashMap<>();
    private static final Map<String, Map<String, JsonElement>> baselineOptions = new HashMap<>();
    private static int baselineFov = 70;
    private static int baselineGuiScale = 0;

    // Smooth motion interpolations (executed on render frame)
    private static volatile CameraInterpolation cameraInterp;
    private static volatile CursorInterpolation cursorInterp;
    private static volatile FovInterpolation fovInterp;

    /** `hotbar off`: ShowcaseHotbarMixin skips only the vanilla hotbar. */
    public static volatile boolean hideHotbar;
    private static final Map<String, int[]> baselineHudPositions = new HashMap<>();
    private static volatile long clickPulseAt;
    private static volatile double lastCursorX = -1, lastCursorY = -1;
    private static boolean windowApplied;

    private static class CameraInterpolation {
        final float startYaw, dyaw, startPitch, dpitch;
        final long startTime, durationMs;
        volatile boolean active = true;
        int frameCount = 0;

        CameraInterpolation(float startYaw, float targetYaw, float startPitch, float targetPitch, long durationMs) {
            this.startYaw = startYaw;
            this.dyaw = Mth.wrapDegrees(targetYaw - startYaw);
            this.startPitch = startPitch;
            this.dpitch = targetPitch - startPitch;
            this.startTime = System.currentTimeMillis();
            this.durationMs = Math.max(1, durationMs);
        }
    }

    private static class CursorInterpolation {
        final double startX, startY, targetX, targetY;
        final long startTime, durationMs;
        volatile boolean active = true;

        CursorInterpolation(double startX, double startY, double targetX, double targetY, long durationMs) {
            this.startX = startX;
            this.startY = startY;
            this.targetX = targetX;
            this.targetY = targetY;
            this.startTime = System.currentTimeMillis();
            this.durationMs = Math.max(1, durationMs);
        }
    }

    private static class FovInterpolation {
        final double startFov, targetFov;
        final long startTime, durationMs;
        volatile boolean active = true;

        FovInterpolation(double startFov, double targetFov, long durationMs) {
            this.startFov = startFov;
            this.targetFov = targetFov;
            this.startTime = System.currentTimeMillis();
            this.durationMs = Math.max(1, durationMs);
        }
    }

    /** Run on the client thread and wait: a bad NPC name fails the step instead of vanishing in a log. */
    private static <T> T onClient(Minecraft mc, java.util.function.Supplier<T> work) throws Exception {
        try {
            return mc.submit(work).get(5, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            throw e.getCause() instanceof Exception cause ? cause : e;
        }
    }

    /** "floss" means "lads:floss": a bare emote name silently did nothing. */
    private static String emoteId(String id) { return id.isEmpty() || id.contains(":") ? id : "lads:" + id; }

    private static boolean isOn(String value) {
        return value.equalsIgnoreCase("on") || value.equalsIgnoreCase("true") || value.equals("1");
    }

    /** The drawn cursor glides to (x, y) in GUI units and the step waits for it; a never-moved mouse starts at the center. */
    private static void glide(Minecraft mc, double x, double y, long ms) throws InterruptedException {
        double sx = mc.mouseHandler.getScaledXPos(mc.getWindow()), sy = mc.mouseHandler.getScaledYPos(mc.getWindow());
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        if (!(sx >= 0 && sx <= w && sy >= 0 && sy <= h)) { sx = w / 2.0; sy = h / 2.0; }
        cursorInterp = new CursorInterpolation(sx, sy, x, y, ms);
        Thread.sleep(ms + 20);
    }

    public static boolean isActive() { return active; }

    /**
     * LADS_SHOWCASE_WINDOW = WxH[@X,Y]: the 4K recording window (wide 3840x2160 or tall 2160x3840), borderless and placed off
     * the screens (the launch script picks X,Y past every monitor), so OBS game capture gets 4K on a 1080p desk.
     */
    private static void applyWindow(Minecraft mc) {
        windowApplied = true;
        String spec = System.getenv("LADS_SHOWCASE_WINDOW");
        if (spec == null || spec.isBlank() || mc.getWindow() == null) return;
        var m = java.util.regex.Pattern.compile("(\\d+)x(\\d+)(?:@(-?\\d+),(-?\\d+))?").matcher(spec.trim());
        if (!m.matches()) { LOGGER.error("Lads showcase director: bad LADS_SHOWCASE_WINDOW {}", spec); return; }
        long handle = mc.getWindow().handle();
        int w = Integer.parseInt(m.group(1)), h = Integer.parseInt(m.group(2));
        SDLVideo.SDL_SetWindowFullscreen(handle, false);
        SDLVideo.SDL_SetWindowBordered(handle, false);
        SDLVideo.SDL_SetWindowResizable(handle, false); // SDL then lets Windows size it past the desktop (min = max = this size)
        SDLVideo.SDL_SetWindowSize(handle, w, h);
        if (m.group(3) != null) SDLVideo.SDL_SetWindowPosition(handle, Integer.parseInt(m.group(3)), Integer.parseInt(m.group(4)));
        LOGGER.info("Lads showcase director: window {}x{} at {},{}", w, h, m.group(3), m.group(4));
    }

    /** Classic arrow, one char per cursor pixel: B outline, W fill, . clear. */
    private static final String[] ARROW = {
        "B...........", "BB..........", "BWB.........", "BWWB........", "BWWWB.......", "BWWWWB......", "BWWWWWB.....",
        "BWWWWWWB....", "BWWWWWWWB...", "BWWWWWWWWB..", "BWWWWWWWWWB.", "BWWWWWWBBBBB", "BWWWBWWB....", "BWWBBWWB....",
        "BWB..BWWB...", "BB...BWWB...", "B.....BWWB..", "......BWWB..", ".......BB..."};

    /** ShowcaseOverlayMixin, after a screen and its tooltip: the director's mouse (the real one never moves), with a pulse on each click. */
    public static void drawCursor(GuiGraphicsExtractor g) {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        var win = mc.getWindow();
        double scale = win.getGuiScale();
        float px = (float) Math.max(3, Math.round(Math.min(win.getWidth(), win.getHeight()) / 400.0)); // screen pixels per cursor pixel: ~95 px tall at 4K, big enough to follow in a video
        g.nextStratum();
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate((float) (mc.mouseHandler.xpos() / scale), (float) (mc.mouseHandler.ypos() / scale));
        pose.scale((float) (px / scale));
        long since = System.currentTimeMillis() - clickPulseAt;
        if (since < 350) {
            float k = since / 350f;
            int r = Math.round(4 + 10 * k), color = Math.round(120 * (1 - k)) << 24 | 0xFFFFFF;
            for (int dy = -r; dy <= r; dy++) {
                int dx = (int) Math.round(Math.sqrt(r * r - dy * dy));
                g.fill(-dx, dy, dx + 1, dy + 1, color);
            }
        }
        for (int row = 0; row < ARROW.length; row++) {
            String line = ARROW[row];
            for (int col = 0; col < line.length(); ) {
                char c = line.charAt(col);
                int end = col;
                while (end < line.length() && line.charAt(end) == c) end++;
                if (c != '.') g.fill(col, row, end, row + 1, c == 'B' ? 0xFF000000 : 0xFFFFFFFF);
                col = end;
            }
        }
        pose.popMatrix();
    }

    private ShowcaseDirector26() {}

    public static void register() {
        if (!"1".equals(DIRECTOR_ENV)) return;
        try {
            Path game = FabricLoader.getInstance().getGameDir().toRealPath();
            Path verification = game.getParent();
            if (verification == null || !verification.getFileName().toString().equals("verification")
                || !verification.getParent().getFileName().toString().equals("artifacts")
                || !Files.isRegularFile(verification.getParent().getParent().resolve("TheLadsCore/settings.gradle")))
                throw new IllegalStateException("Showcase director runs only in a game folder under artifacts/verification");
            String dirEnv = System.getenv("LADS_SHOWCASE_DIR");
            if (dirEnv == null) dirEnv = System.getProperty("lads.showcase.dir");
            dir = Path.of(dirEnv).toRealPath();
            if (!dir.startsWith(verification))
                throw new IllegalStateException("LADS_SHOWCASE_DIR must be under " + verification);
        } catch (Exception failure) {
            LOGGER.error("Lads showcase director OFF: {}", failure.toString());
            return;
        }

        active = true;
        ClientTickEvents.END_CLIENT_TICK.register(ShowcaseDirector26::tick);
        LOGGER.info("Lads showcase director BEGIN: control folder {}", dir);
    }

    /**
     * Called at the head of every render frame via GameRendererProbeMixin.
     */
    public static void onRenderFrame() {
        if (!active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        long now = System.currentTimeMillis();
        if (!windowApplied) applyWindow(mc);
        if (mc.gui.screen() != null && mc.getWindow() != null) { // a new screen put the mouse at the real (off-window) spot: keep the drawn cursor in view
            double mx = mc.mouseHandler.xpos(), my = mc.mouseHandler.ypos();
            int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
            if (!(mx >= 0 && mx <= w && my >= 0 && my <= h)) {
                double tx = lastCursorX >= 0 ? lastCursorX : w / 2.0, ty = lastCursorY >= 0 ? lastCursorY : h / 2.0;
                mc.mouseHandler.onMove(mc.getWindow().handle(), tx, ty, 0, 0);
            }
        }

        // 1. Camera interpolation (look / turn)
        CameraInterpolation cam = cameraInterp;
        if (cam != null && cam.active && mc.player != null) {
            double t = (now - cam.startTime) / (double) cam.durationMs;
            t = Math.max(0.0, Math.min(1.0, t));
            double k = easeInOut(t);
            float curYaw = cam.startYaw + (float) (cam.dyaw * k);
            float curPitch = cam.startPitch + (float) (cam.dpitch * k);

            mc.player.setYRot(curYaw);
            mc.player.yRotO = curYaw;
            mc.player.setYHeadRot(curYaw);
            mc.player.yHeadRotO = curYaw;
            mc.player.setXRot(curPitch);
            mc.player.xRotO = curPitch;

            cam.frameCount++;
            appendYawLog(now, cam.frameCount, now - cam.startTime, curYaw, curPitch);

            if (t >= 1.0) {
                cam.active = false;
            }
        }

        // 2. Cursor interpolation
        CursorInterpolation cur = cursorInterp;
        if (cur != null && cur.active && mc.getWindow() != null) {
            double t = (now - cur.startTime) / (double) cur.durationMs;
            t = Math.max(0.0, Math.min(1.0, t));
            double k = easeInOut(t);
            double curGuiX = cur.startX + (cur.targetX - cur.startX) * k;
            double curGuiY = cur.startY + (cur.targetY - cur.startY) * k;

            double scale = mc.getWindow().getGuiScale();
            double winX = curGuiX * scale;
            double winY = curGuiY * scale;

            mc.mouseHandler.onMove(mc.getWindow().handle(), winX, winY, 0, 0);
            lastCursorX = winX;
            lastCursorY = winY;

            if (t >= 1.0) {
                cur.active = false;
            }
        }

        // 3. FOV interpolation
        FovInterpolation fov = fovInterp;
        if (fov != null && fov.active && mc.options != null) {
            double t = (now - fov.startTime) / (double) fov.durationMs;
            t = Math.max(0.0, Math.min(1.0, t));
            double k = easeInOut(t);
            double curFov = fov.startFov + (fov.targetFov - fov.startFov) * k;
            mc.options.fov().set((int) Math.round(curFov));

            if (t >= 1.0) {
                fov.active = false;
            }
        }
    }

    private static double easeInOut(double t) {
        t = Math.max(0.0, Math.min(1.0, t));
        // Quintic smoothstep: 6t^5 - 15t^4 + 10t^3
        return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
    }

    private static void tick(Minecraft mc) {
        try {
            if (mc.level == null) {
                lobby(mc);
                return;
            }
            if (mc.player == null || (!setUp && mc.gui.screen() != null)) return;
            if (!setUp) setUp(mc);
            ShowcaseNpcs26.tick(mc);

            long now = System.currentTimeMillis();
            Thread worker = currentWorker;
            if (scriptRunning.get() && worker != null && !abortStep && now - stepStartedAt > STEP_LIMIT_MS) {
                LOGGER.error("Lads showcase director: step running {} s, aborting it", (now - stepStartedAt) / 1000);
                abortStep = true;
                worker.interrupt();
            }
            if (!ready && now - t0 > 3_000) {
                ready = true;
                status("ready " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight());
            }

            if (now >= nextFps) {
                nextFps = now + 500;
                write("fps.txt", String.valueOf(mc.getFps()));
            }

            if (ready && !scriptRunning.get() && now >= nextPoll) {
                nextPoll = now + 200;
                poll(mc);
            }
        } catch (Exception failure) {
            LOGGER.error("Lads showcase director tick failed", failure);
        }
    }

    private static void lobby(Minecraft mc) {
        if (setUp) {
            setUp = false;
            ready = false;
            titleSince = 0;
        }
        var screen = mc.gui.screen();
        long now = System.currentTimeMillis();

        if (isServerMode()) {
            if (screen instanceof ConnectScreen) {
                return;
            }
            if (screen instanceof DisconnectedScreen) {
                if (now < connectAt) return;
                connectAt = now + 10_000;
                LOGGER.info("Lads showcase director: retrying connection to {}...", SERVER_TARGET);
                ServerAddress addr = ServerAddress.parseString(SERVER_TARGET);
                ServerData data = new ServerData("Lads Showcase Server", SERVER_TARGET, ServerData.Type.OTHER);
                ConnectScreen.startConnecting(screen, mc, addr, data, false, null);
                return;
            }
            boolean title = screen instanceof TitleScreen || (screen != null && screen.getClass().getSimpleName().equals("LadsTitleScreen26"));
            if (!title || mc.gui.overlay() != null || !mc.isGameLoadFinished()) {
                titleSince = 0;
                return;
            }
            if (titleSince == 0) {
                titleSince = now;
                status("title");
            }
            if (now - titleSince < 3000) return;
            if (now < connectAt) return;
            connectAt = now + 10_000;
            LOGGER.info("Lads showcase director: connecting to {}...", SERVER_TARGET);
            ServerAddress addr = ServerAddress.parseString(SERVER_TARGET);
            ServerData data = new ServerData("Lads Showcase Server", SERVER_TARGET, ServerData.Type.OTHER);
            ConnectScreen.startConnecting(screen, mc, addr, data, false, null);
            return;
        }

        boolean title = screen instanceof TitleScreen || (screen != null && screen.getClass().getSimpleName().equals("LadsTitleScreen26"));
        if (!title || mc.gui.overlay() != null || !mc.isGameLoadFinished()) {
            titleSince = 0;
            return;
        }
        if (titleSince == 0) {
            titleSince = now;
            status("title");
        }
        if (now - titleSince < 3000) return;

        if (!worldAsked) {
            worldAsked = true;
            if (Files.isRegularFile(mc.gameDirectory.toPath().resolve("saves").resolve(SAVE).resolve("level.dat"))) {
                mc.createWorldOpenFlows().openWorld(SAVE, () -> LOGGER.error("Lads showcase director: world open cancelled"));
                return;
            }
            long seed = Long.parseLong(System.getenv().getOrDefault("LADS_SHOWCASE_SEED", "20261018"));
            LOGGER.info("Lads showcase director: fresh natural world, seed {}", seed);
            var settings = new net.minecraft.world.level.LevelSettings(
                "Lads Showcase",
                net.minecraft.world.level.GameType.CREATIVE,
                new net.minecraft.world.level.LevelSettings.DifficultySettings(net.minecraft.world.Difficulty.PEACEFUL, false, true),
                true,
                net.minecraft.world.level.WorldDataConfiguration.DEFAULT
            );
            mc.createWorldOpenFlows().createFreshLevel(
                SAVE,
                settings,
                new net.minecraft.world.level.levelgen.WorldOptions(seed, true, false),
                registries -> registries.lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                    .getOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.NORMAL).value().createWorldDimensions(),
                screen
            );
        }
    }

    /** What `reset` returns to: every module's on/off and options, FOV and GUI scale. Taken at startup and by the `baseline` verb. */
    private static void takeBaseline(Minecraft mc) {
        HudSettings.getInstance().setBackgrounds(true); // owner: HUD showcases always with backgrounds and text shadow
        HudSettings.getInstance().setTextShadow(true);
        for (Module m : ModuleManager.getInstance().getRegisteredModules()) { // modules with their own background switch (Action Bar, Scoreboard, Tab List)
            if (!"HUD".equals(String.valueOf(m.getCategory()))) continue;
            for (Option o : m.getOptions()) {
                if (o instanceof BoolOption bo && o.getName().matches("(?i)(show|render) background|text shadow")) bo.set(true);
                if (o instanceof DropdownOption dd && o.getName().equalsIgnoreCase("Background") && "Off".equalsIgnoreCase(dd.getValue())) dd.setIndex(0);
            }
        }
        baselineHudPositions.clear();
        HudSettings.getInstance().getPositions().forEach((k, v) -> baselineHudPositions.put(k, v.clone()));
        baselineModuleState.clear();
        baselineOptions.clear();
        for (Module m : ModuleManager.getInstance().getRegisteredModules()) {
            baselineModuleState.put(m.getName(), m.isEnabled());
            Map<String, JsonElement> opts = new HashMap<>();
            for (Option opt : m.getOptions()) {
                try {
                    opts.put(opt.getName(), opt.save());
                } catch (Exception ignored) {}
            }
            baselineOptions.put(m.getName(), opts);
        }
        baselineFov = mc.options.fov().get();
        baselineGuiScale = mc.options.guiScale().get();
    }

    private static void setUp(Minecraft mc) {
        setUp = true;
        t0 = System.currentTimeMillis();

        // Addendum 3: set window title
        String playerName = "Player";
        if (mc.getUser() != null && mc.getUser().getName() != null && !mc.getUser().getName().isBlank()) {
            playerName = mc.getUser().getName();
        } else if (mc.player != null && mc.player.getScoreboardName() != null && !mc.player.getScoreboardName().isBlank()) {
            playerName = mc.player.getScoreboardName();
        }
        mc.getWindow().setTitle(resolveWindowTitle(SECONDARY, playerName));

        // Keep Lads HUD VISIBLE
        if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
        mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);

        // Turn Dynamic FPS off while active
        var dynamicFps = ModuleManager.getInstance().getModule("DynamicFPS");
        if (dynamicFps != null && dynamicFps.isEnabled()) dynamicFps.setEnabled(false);

        takeBaseline(mc);
        try { com.thelads.core.v26_2.cosmetics.anim.ShowcaseEmote26.loadSkinsFor(mc, dir); } catch (Exception e) { LOGGER.error("Showcase skins", e); }

        if (isServerMode()) {
            double[] spot = parseSpotFromEnv();
            spotX = spot[0];
            groundY = spot[1];
            spotZ = spot[2];
            LOGGER.info("Showcase director server mode spot: {} {} {}", spotX, groundY, spotZ);

            if (JOIN_CMD != null && !JOIN_CMD.isBlank()) {
                sendPlayerCommand(mc, JOIN_CMD);
                SCHEDULER.schedule(() -> {
                    for (String c : SETUP_COMMANDS) {
                        sendPlayerCommand(mc, c);
                    }
                    sendPlayerCommand(mc, formatTpCommand("@s", spotX, groundY, spotZ, 0f, 0f));
                    mc.execute(() -> {
                        if (mc.player != null) {
                            mc.player.setPos(spotX, groundY, spotZ);
                            mc.player.setYRot(0f);
                            mc.player.yRotO = 0f;
                            mc.player.setYHeadRot(0f);
                            mc.player.yHeadRotO = 0f;
                            mc.player.setXRot(0f);
                            mc.player.xRotO = 0f;
                        }
                    });
                }, 800, TimeUnit.MILLISECONDS);
            } else {
                for (String c : SETUP_COMMANDS) {
                    sendPlayerCommand(mc, c);
                }
                sendPlayerCommand(mc, formatTpCommand("@s", spotX, groundY, spotZ, 0f, 0f));
                mc.execute(() -> {
                    if (mc.player != null) {
                        mc.player.setPos(spotX, groundY, spotZ);
                        mc.player.setYRot(0f);
                        mc.player.yRotO = 0f;
                        mc.player.setYHeadRot(0f);
                        mc.player.yHeadRotO = 0f;
                        mc.player.setXRot(0f);
                        mc.player.xRotO = 0f;
                    }
                });
            }
        } else {
            MinecraftServer server = mc.getSingleplayerServer();
            if (server != null) {
                server.execute(() -> {
                    for (String c : SETUP_COMMANDS) {
                        command(server, c.replace("@s", "@a"));
                    }

                    ServerLevel level = server.overworld();
                    establishLawn(server, level, null);
                    homeX = spotX; homeY = groundY; homeZ = spotZ;
                    // One snapshot per world (a reopened world keeps its first, clean one).
                    Path marker = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("lads-showcase-snapshot");
                    if (!Files.exists(marker)) {
                        command(server, String.format(Locale.ROOT, "forceload add %d %d %d %d", (int) spotX - 40, (int) spotZ - 40, (int) spotX + 40, (int) spotZ + 40));
                        for (String c : snapshotCommands(true, spotX, groundY, spotZ)) command(server, c);
                        try { Files.writeString(marker, "snapshot of " + spotX + " " + groundY + " " + spotZ); } catch (IOException e) { LOGGER.error("Showcase snapshot marker", e); }
                    }
                    command(server, "setworldspawn " + (int) Math.floor(spotX) + " " + (int) Math.floor(groundY) + " " + (int) Math.floor(spotZ));
                    tpNow(server, "@a", 0, 0, 0, 0, 0);
                });
            }
        }

        exportModulesAndScreens();
        status("world");
    }

    private static void establishLawn(MinecraftServer server, ServerLevel level, String biomeId) {
        BlockPos origin = new BlockPos(0, 80, 0);
        Holder<Biome> targetBiome = null;
        if (biomeId != null && !biomeId.isBlank() && !biomeId.equalsIgnoreCase("lawn")) {
            String q = biomeId.toLowerCase(Locale.ROOT);
            var pair = level.findClosestBiome3d(h -> h.unwrapKey().map(k -> k.identifier().toString().toLowerCase(Locale.ROOT).contains(q)).orElse(false), origin, 6400, 32, 64);
            if (pair != null) {
                origin = pair.getFirst();
                targetBiome = pair.getSecond();
            }
        } else {
            var found = level.findClosestBiome3d(h -> h.is(Biomes.PLAINS) || h.is(Biomes.SUNFLOWER_PLAINS), origin, 6400, 32, 64);
            if (found != null) {
                origin = found.getFirst();
                targetBiome = found.getSecond();
            }
        }

        int bestX = origin.getX(), bestZ = origin.getZ(), bestSpread = Integer.MAX_VALUE;
        for (int cx = origin.getX() - 96; cx <= origin.getX() + 96; cx += 6) {
            for (int cz = origin.getZ() - 96; cz <= origin.getZ() + 96; cz += 6) {
                int top = top(level, cx, cz);
                if (!level.getBlockState(new BlockPos(cx, top - 1, cz)).is(Blocks.GRASS_BLOCK)) continue;
                int lo = top, hi = top, wet = 0;
                for (int dx = -FLOOR; dx <= FLOOR; dx += 3) {
                    for (int dz = -FLOOR; dz <= FLOOR; dz += 3) {
                        int h = top(level, cx + dx, cz + dz);
                        lo = Math.min(lo, h);
                        hi = Math.max(hi, h);
                        if (!level.getBlockState(new BlockPos(cx + dx, h - 1, cz + dz)).is(Blocks.GRASS_BLOCK)) wet++;
                    }
                }
                if (hi - lo + wet < bestSpread) {
                    bestSpread = hi - lo + wet;
                    bestX = cx;
                    bestZ = cz;
                }
            }
        }

        spotX = bestX + 0.5;
        spotZ = bestZ + 0.5;
        int y = top(level, bestX, bestZ);
        groundY = y;
        LOGGER.info("Showcase director lawn spot: {} {} {} (biome {}, height spread {})", spotX, y, spotZ, targetBiome, bestSpread);

        String box = String.format(Locale.ROOT, "%d %%d %d %d %%d %d", bestX - FLOOR, bestZ - FLOOR, bestX + FLOOR, bestZ + FLOOR);
        command(server, "fill " + String.format(Locale.ROOT, box, y, y + 8) + " air");
        command(server, "fill " + String.format(Locale.ROOT, box, y - 3, y - 2) + " dirt");
        command(server, "fill " + String.format(Locale.ROOT, box, y - 1, y - 1) + " grass_block");
    }

    private static void exportModulesAndScreens() {
        try {
            // Write modules.txt
            StringBuilder modSb = new StringBuilder();
            modSb.append("# The Lads Modules & Settings Catalog\n\n");
            for (Module m : ModuleManager.getInstance().getRegisteredModules()) {
                modSb.append(String.format(Locale.ROOT, "[%s] enabled=%s category=%s\n", m.getName(), m.isEnabled(), m.getCategory()));
                for (Option opt : m.getOptions()) {
                    String type = opt.getClass().getSimpleName();
                    String val = "";
                    if (opt instanceof BoolOption bo) {
                        type = "Boolean";
                        val = String.valueOf(bo.get());
                    } else if (opt instanceof SliderOption so) {
                        type = String.format(Locale.ROOT, "Slider(min=%.1f, max=%.1f, step=%.1f)", so.getMin(), so.getMax(), so.getStep());
                        val = so.display();
                    } else if (opt instanceof DropdownOption dro) {
                        type = "Dropdown" + Arrays.toString(dro.getChoices());
                        val = dro.getValue();
                    } else if (opt instanceof ColorOption co) {
                        type = "Color";
                        val = String.format(Locale.ROOT, "0x%08X (global=%s)", co.getColor(), co.isUseGlobal());
                    } else if (opt instanceof TextOption || opt instanceof StringOption) {
                        type = "String";
                        val = opt.save().toString();
                    } else {
                        val = opt.save().toString();
                    }
                    modSb.append(String.format(Locale.ROOT, "  - %s | %s | %s\n", opt.getName(), type, val));
                }
                modSb.append("\n");
            }
            write("modules.txt", modSb.toString());

            // Write screens.txt
            StringBuilder scrSb = new StringBuilder();
            scrSb.append("# The Lads Showcase Screen Mappings\n\n");
            scrSb.append("lads -> com.thelads.core.v26_2.gui.LadsSettingsScreen26\n");
            scrSb.append("hudedit -> com.thelads.core.v26_2.gui.DraggableHudScreen26\n");
            scrSb.append("cosmetics -> com.thelads.core.v26_2.cosmetics.anim.CosmeticsScreen26\n");
            scrSb.append("crosshair -> com.thelads.core.v26_2.feature.crosshair.CrosshairDrawingScreen\n");
            scrSb.append("emotewheel -> com.thelads.core.v26_2.cosmetics.anim.EmoteWheelScreen26\n");
            scrSb.append("inventory -> net.minecraft.client.gui.screens.inventory.InventoryScreen\n");
            scrSb.append("chat -> net.minecraft.client.gui.screens.ChatScreen\n");
            scrSb.append("pause -> net.minecraft.client.gui.screens.PauseScreen\n");
            scrSb.append("options -> net.minecraft.client.gui.screens.options.OptionsScreen\n");
            scrSb.append("close -> null (close current screen)\n");
            scrSb.append("class <fqcn> -> Reflection instantiation (requires no-arg constructor)\n");
            write("screens.txt", scrSb.toString());
        } catch (Exception e) {
            LOGGER.error("Failed to export modules or screens catalog", e);
        }
    }

    private static void poll(Minecraft mc) throws Exception {
        Path file = dir.resolve("cmd.txt");
        if (!Files.isRegularFile(file)) return;
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty()) return;
        // The oldest step not run yet, not the last line: several lanes queue steps while one runs (the last-line rule skipped theirs).
        String lastLine = null;
        int n = Integer.MAX_VALUE;
        for (String raw : lines) {
            String l = raw.trim();
            if (l.startsWith("﻿")) l = l.substring(1).trim();
            if (l.isEmpty()) continue;
            String num = l.split("\\s+")[0].replaceAll("[^0-9-]", "");
            if (num.isEmpty()) continue;
            int k = Integer.parseInt(num);
            if (k > lastCmd && k < n) { n = k; lastLine = l; }
        }
        if (lastLine == null) return;
        String[] parts = lastLine.split("\\s+");
        lastCmd = n;
        if (parts.length < 2) return;

        String action = parts[1].toLowerCase(Locale.ROOT);
        LOGGER.info("Showcase director command step {}: {}", n, lastLine);

        if ("quit".equals(action)) {
            write("done.txt", String.valueOf(n));
            mc.execute(() -> {
                mc.stop();
                Thread guard = new Thread(() -> {
                    try { Thread.sleep(3000); } catch (Exception ignored) {}
                    System.exit(0);
                }, "ShowcaseDirector-ExitGuard");
                guard.setDaemon(true);
                guard.start();
            });
            return;
        }

        if (("run".equals(action) || "check".equals(action)) && parts.length > 2) {
            checkStep = "check".equals(action) ? n : -1;
            String scriptFileName = parts[2];
            Path scriptFile = dir.resolve(scriptFileName);
            if (!Files.isRegularFile(scriptFile)) scriptFile = Path.of(scriptFileName);
            final Path targetScript = scriptFile;

            if (scriptRunning.compareAndSet(false, true)) {
                final int step = n;
                Thread worker = new Thread(() -> executeScript(mc, step, targetScript), "ShowcaseDirector-Worker-" + step);
                abortStep = false;
                stepStartedAt = System.currentTimeMillis();
                currentWorker = worker;
                worker.start();
            }
        }
    }

    private static void executeScript(Minecraft mc, int stepNumber, Path scriptPath) {
        List<String> marks = Collections.synchronizedList(new ArrayList<>());
        long scriptStart = System.currentTimeMillis();
        currentStep = stepNumber;
        write("started-" + stepNumber + ".txt", "start " + scriptStart);
        Path errorFile = dir.resolve("error-" + stepNumber + ".txt");
        try { Files.deleteIfExists(errorFile); } catch (Exception ignored) {}
        CMD_ERRORS.clear();

        try {
            List<String> rawLines = Files.readAllLines(scriptPath, StandardCharsets.UTF_8);
            List<ShowcaseScriptParser.Command> commands = ShowcaseScriptParser.parseLines(rawLines);

            for (ShowcaseScriptParser.Command cmd : commands) {
                if (abortStep) {
                    Files.writeString(errorFile, String.format(Locale.ROOT, "ERROR %d step aborted: it ran longer than %d s (a verb never returned)%n",
                        cmd.lineNo(), STEP_LIMIT_MS / 1000), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    break;
                }
                try {
                    dispatchCommand(mc, cmd, scriptStart, marks);
                } catch (Exception ex) {
                    String msg = String.format(Locale.ROOT, "ERROR %d %s", cmd.lineNo(), ex.getMessage());
                    LOGGER.error(msg, ex);
                    try {
                        Files.writeString(errorFile, msg + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (IOException io) {
                        LOGGER.error("Cannot write to error file", io);
                    }
                }
            }

            Thread.sleep(250); // the server thread runs the last `cmd` lines a tick later
            for (String bad; (bad = CMD_ERRORS.poll()) != null; ) {
                Files.writeString(errorFile, bad + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }

            // Write marks
            StringBuilder markSb = new StringBuilder();
            for (String m : marks) markSb.append(m).append('\n');
            write("marks-" + stepNumber + ".txt", markSb.toString());

            // Write done
            write("done.txt", String.valueOf(stepNumber));
            LOGGER.info("Showcase director script step {} completed successfully", stepNumber);
        } catch (Exception failure) {
            LOGGER.error("Showcase director script execution failed for step {}", stepNumber, failure);
            try {
                Files.writeString(errorFile, "ERROR 0 " + failure.getMessage() + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception ignored) {}
            write("done.txt", String.valueOf(stepNumber));
        } finally {
            scriptRunning.set(false);
        }
    }

    private static void dispatchCommand(Minecraft mc, ShowcaseScriptParser.Command cmd, long scriptStart, List<String> marks) throws Exception {
        String verb = cmd.verb();
        switch (verb) {
            case "widgets" -> {
                long elapsed = System.currentTimeMillis() - scriptStart;
                CountDownLatch done = new CountDownLatch(1);
                String[] text = new String[1];
                mc.execute(() -> { try { text[0] = describeWidgets(mc); } finally { done.countDown(); } });
                done.await(5, TimeUnit.SECONDS);
                write("widgets-" + currentStep + "-" + elapsed + ".txt", text[0] == null ? "timeout" : text[0]);
            }
            case "clearchat" -> mc.execute(() -> mc.gui.hud.getChat().clearMessages(false)); // server replies (e.g. reset's "no entity found") land after reset's own clear
            case "baseline" -> {
                CountDownLatch done = new CountDownLatch(1);
                mc.execute(() -> { try { takeBaseline(mc); } finally { done.countDown(); } });
                done.await(5, TimeUnit.SECONDS);
            }
            case "reset" -> {
                if (!isServerMode() && homeY != 0) { spotX = homeX; groundY = homeY; spotZ = homeZ; } // a `spot` take moved it
                for (Module m : ModuleManager.getInstance().getRegisteredModules()) {
                    Boolean en = baselineModuleState.get(m.getName());
                    if (en != null && m.isEnabled() != en) {
                        m.setEnabled(en);
                    }
                    Map<String, JsonElement> opts = baselineOptions.get(m.getName());
                    if (opts != null) {
                        for (Option opt : m.getOptions()) {
                            JsonElement val = opts.get(opt.getName());
                            if (val != null) {
                                try {
                                    opt.load(val);
                                } catch (Exception ignored) {}
                            }
                        }
                    }
                }
                hideHotbar = false;
                mc.execute(() -> { // `hudpos` moves are per take
                    var hs = HudSettings.getInstance();
                    for (String k : new ArrayList<>(hs.getPositions().keySet())) if (!baselineHudPositions.containsKey(k)) hs.clearPosition(k);
                    baselineHudPositions.forEach((k, v) -> hs.setPosition(k, v[0], v[1]));
                });
                HudSettings.getInstance().setBackgrounds(true);
                HudSettings.getInstance().setTextShadow(true);
                mc.execute(() -> {
                    ShowcaseNpcs26.clear(mc);
                    com.thelads.core.v26_2.feature.NativeKillBanner.reset(); // v1's 5-kill take started at kill 3: the last take's streak lived on
                    if (mc.player != null) com.thelads.core.client.cosmetics.Emotes.stopLocal(mc.player.getUUID());
                    mc.setScreenAndShow(null);
                    mc.options.setCameraType(CameraType.FIRST_PERSON);
                    mc.options.fov().set(baselineFov);
                    mc.options.guiScale().set(baselineGuiScale);
                    if (mc.gui.hud.isHidden()) mc.gui.hud.toggle();
                    mc.gui.hud.getChat().clearMessages(false);
                    mc.gui.toastManager().clear();
                    if (mc.player != null) {
                        mc.player.getInventory().clearContent();
                        mc.player.setPos(spotX, groundY, spotZ);
                        mc.player.setYRot(0f);
                        mc.player.yRotO = 0f;
                        mc.player.setYHeadRot(0f);
                        mc.player.yHeadRotO = 0f;
                        mc.player.setXRot(0f);
                        mc.player.xRotO = 0f;
                    }
                });
                if (isServerMode()) {
                    for (String c : buildResetCommands(true, spotX, groundY, spotZ)) {
                        sendPlayerCommand(mc, c);
                    }
                } else {
                    MinecraftServer server = mc.getSingleplayerServer();
                    if (server != null) {
                        server.execute(() -> {
                            for (var ev : new ArrayList<>(server.getCustomBossEvents().getEvents())) { // v1: hud-13's Wither bar stayed in every later take
                                ev.removeAllPlayers();
                                server.getCustomBossEvents().remove(ev);
                            }
                            for (String c : buildResetCommands(false, spotX, groundY, spotZ)) {
                                command(server, c);
                            }
                        });
                    }
                }
            }
            case "wait" -> {
                long ms = cmd.longArg(0, 0);
                if (ms > 0) Thread.sleep(ms);
            }
            case "mark" -> {
                long elapsed = System.currentTimeMillis() - scriptStart;
                marks.add(elapsed + " " + cmd.tail(0));
                if (checkStep >= 0) shot(mc, dir.resolve("shots-" + checkStep).resolve(elapsed + "-" + cmd.tail(0).replaceAll("[^A-Za-z0-9]+", "_") + ".png"));
            }
            case "cmd" -> {
                String cmdText = cmd.tail(0);
                if (isServerMode()) {
                    sendPlayerCommand(mc, cmdText);
                } else {
                    MinecraftServer server = mc.getSingleplayerServer();
                    if (server != null) {
                        int line = cmd.lineNo();
                        server.execute(() -> {
                            String bad = syntaxError(server, cmdText);
                            if (bad != null) CMD_ERRORS.add(String.format(Locale.ROOT, "ERROR %d command does not parse: /%s (%s)", line, cmdText, bad));
                            commandAsPlayer(mc, server, cmdText);
                        });
                    }
                }
            }
            case "chat" -> {
                String text = cmd.tail(0);
                mc.execute(() -> {
                    if (mc.player != null && mc.player.connection != null) {
                        if (text.startsWith("/")) {
                            mc.player.connection.sendCommand(text.substring(1));
                        } else {
                            mc.player.connection.sendChat(text);
                        }
                    }
                });
            }
            case "tp" -> {
                double dx = cmd.doubleArg(0, 0);
                double dy = cmd.doubleArg(1, 0);
                double dz = cmd.doubleArg(2, 0);
                float yaw = (float) cmd.doubleArg(3, 0);
                float pitch = (float) cmd.doubleArg(4, 0);
                double targetX = spotX + dx;
                double targetY = groundY + dy;
                double targetZ = spotZ + dz;
                if (isServerMode()) {
                    sendPlayerCommand(mc, formatTpCommand("@s", targetX, targetY, targetZ, yaw, pitch));
                } else {
                    MinecraftServer server = mc.getSingleplayerServer();
                    if (server != null) {
                        server.execute(() -> tpNow(server, "@a", dx, dy, dz, yaw, pitch));
                    }
                }
                mc.execute(() -> {
                    if (mc.player != null) {
                        mc.player.setPos(targetX, targetY, targetZ);
                        mc.player.setYRot(yaw);
                        mc.player.yRotO = yaw;
                        mc.player.setYHeadRot(yaw);
                        mc.player.yHeadRotO = yaw;
                        mc.player.setXRot(pitch);
                        mc.player.xRotO = pitch;
                    }
                });
            }
            case "spot", "lawn" -> {
                if (isServerMode()) {
                    double[] spot = parseSpotFromEnv();
                    spotX = spot[0];
                    groundY = spot[1];
                    spotZ = spot[2];
                    sendPlayerCommand(mc, formatTpCommand("@s", spotX, groundY, spotZ, 0f, 0f));
                    mc.execute(() -> {
                        if (mc.player != null) {
                            mc.player.setPos(spotX, groundY, spotZ);
                            mc.player.setYRot(0f);
                            mc.player.yRotO = 0f;
                            mc.player.setYHeadRot(0f);
                            mc.player.yHeadRotO = 0f;
                            mc.player.setXRot(0f);
                            mc.player.xRotO = 0f;
                        }
                    });
                } else {
                    String biomeId = cmd.arg(0, "lawn");
                    MinecraftServer server = mc.getSingleplayerServer();
                    if (server != null) {
                        server.execute(() -> {
                            establishLawn(server, server.overworld(), biomeId);
                            tpNow(server, "@a", 0, 0, 0, 0, 0);
                        });
                    }
                }
            }
            case "look" -> {
                float targetYaw = (float) cmd.doubleArg(0, 0);
                float targetPitch = (float) cmd.doubleArg(1, 0);
                long ms = cmd.longArg(2, 0);
                if (mc.player != null) {
                    float curYaw = mc.player.getYRot();
                    float curPitch = mc.player.getXRot();
                    if (ms > 0) {
                        cameraInterp = new CameraInterpolation(curYaw, targetYaw, curPitch, targetPitch, ms);
                        Thread.sleep(ms);
                    } else {
                        mc.execute(() -> {
                            mc.player.setYRot(targetYaw);
                            mc.player.yRotO = targetYaw;
                            mc.player.setYHeadRot(targetYaw);
                            mc.player.yHeadRotO = targetYaw;
                            mc.player.setXRot(targetPitch);
                            mc.player.xRotO = targetPitch;
                        });
                    }
                }
            }
            case "turn" -> {
                float dyaw = (float) cmd.doubleArg(0, 0);
                float dpitch = (float) cmd.doubleArg(1, 0);
                long ms = cmd.longArg(2, 0);
                if (mc.player != null) {
                    float curYaw = mc.player.getYRot();
                    float curPitch = mc.player.getXRot();
                    float targetYaw = curYaw + dyaw;
                    float targetPitch = curPitch + dpitch;
                    if (ms > 0) {
                        cameraInterp = new CameraInterpolation(curYaw, targetYaw, curPitch, targetPitch, ms);
                        Thread.sleep(ms);
                    } else {
                        mc.execute(() -> {
                            mc.player.setYRot(targetYaw);
                            mc.player.yRotO = targetYaw;
                            mc.player.setYHeadRot(targetYaw);
                            mc.player.yHeadRotO = targetYaw;
                            mc.player.setXRot(targetPitch);
                            mc.player.xRotO = targetPitch;
                        });
                    }
                }
            }
            case "camera" -> {
                String mode = cmd.arg(0, "fp").toLowerCase(Locale.ROOT);
                CameraType type = switch (mode) {
                    case "back" -> CameraType.THIRD_PERSON_BACK;
                    case "front" -> CameraType.THIRD_PERSON_FRONT;
                    default -> CameraType.FIRST_PERSON;
                };
                mc.execute(() -> mc.options.setCameraType(type));
            }
            case "fov" -> {
                double targetFov = cmd.doubleArg(0, 70);
                long ms = cmd.longArg(1, 0);
                if (ms > 0) {
                    double curFov = mc.options.fov().get();
                    fovInterp = new FovInterpolation(curFov, targetFov, ms);
                } else {
                    mc.execute(() -> mc.options.fov().set((int) Math.round(targetFov)));
                }
            }
            case "hud" -> {
                boolean on = "on".equalsIgnoreCase(cmd.arg(0, "on"));
                mc.execute(() -> {
                    if (mc.gui.hud.isHidden() == on) mc.gui.hud.toggle();
                });
            }
            case "hold" -> {
                String keyName = cmd.arg(0, "");
                long ms = cmd.longArg(1, 0);
                KeyMapping mapping = resolveKeyMapping(mc, keyName);
                if (mapping != null) {
                    mc.execute(() -> {
                        mapping.setDown(true);
                        KeyMapping.click(mapping.getDefaultKey());
                    });
                    if (ms > 0) {
                        SCHEDULER.schedule(() -> mc.execute(() -> mapping.setDown(false)), ms, TimeUnit.MILLISECONDS);
                    }
                } else {
                    throw new IllegalArgumentException("Unknown key mapping: " + keyName);
                }
            }
            case "press" -> {
                String keyName = cmd.arg(0, "");
                KeyMapping mapping = resolveKeyMapping(mc, keyName);
                if (mapping != null) {
                    mc.execute(() -> {
                        mapping.setDown(true);
                        if (mapping.getDefaultKey() != InputConstants.UNKNOWN) {
                            KeyMapping.click(mapping.getDefaultKey());
                        }
                    });
                    SCHEDULER.schedule(() -> mc.execute(() -> mapping.setDown(false)), 50, TimeUnit.MILLISECONDS);
                } else {
                    throw new IllegalArgumentException("Unknown key mapping: " + keyName);
                }
            }
            case "attack" -> {
                int count = cmd.intArg(0, 1);
                long interval = cmd.longArg(1, 250);
                for (int i = 0; i < count; i++) {
                    SCHEDULER.schedule(() -> mc.execute(() -> {
                        KeyMapping.click(InputConstants.getKey(mc.options.keyAttack.saveString()));
                        if (mc.player != null) mc.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
                        if (mc.crosshairPickEntity != null) ShowcaseNpcs26.hit(mc, mc.crosshairPickEntity); // NPCs are client-only: flash + sounds here
                    }), i * interval, TimeUnit.MILLISECONDS);
                }
            }
            case "use" -> {
                int count = cmd.intArg(0, 1);
                long interval = cmd.longArg(1, 250);
                for (int i = 0; i < count; i++) {
                    SCHEDULER.schedule(() -> mc.execute(() -> {
                        KeyMapping.click(InputConstants.getKey(mc.options.keyUse.saveString()));
                    }), i * interval, TimeUnit.MILLISECONDS);
                }
            }
            case "slot" -> {
                int slot = cmd.intArg(0, 1);
                int index = Math.max(0, Math.min(8, slot - 1));
                mc.execute(() -> {
                    if (mc.player != null) {
                        mc.player.getInventory().setSelectedSlot(index);
                        if (mc.player.connection != null) {
                            mc.player.connection.send(new ServerboundSetCarriedItemPacket(index));
                        }
                    }
                });
            }
            case "screen" -> {
                String screenName = cmd.arg(0, "").toLowerCase(Locale.ROOT);
                if ("class".equals(screenName)) {
                    String fqcn = cmd.arg(1, "");
                    Class<?> clazz = Class.forName(fqcn);
                    Screen s;
                    Screen cur = mc.gui.screen();
                    try {
                        Constructor<?> ctor = clazz.getDeclaredConstructor(Screen.class);
                        ctor.setAccessible(true);
                        s = (Screen) ctor.newInstance(cur);
                    } catch (NoSuchMethodException e) {
                        Constructor<?> ctor = clazz.getDeclaredConstructor();
                        ctor.setAccessible(true);
                        s = (Screen) ctor.newInstance();
                    }
                    final Screen finalS = s;
                    mc.execute(() -> mc.setScreenAndShow(finalS));
                } else {
                    mc.execute(() -> openScreenByName(mc, screenName));
                }
            }
            case "cursor" -> {
                String arg0 = cmd.arg(0, "");
                long ms = 0;
                double targetX = 0, targetY = 0;

                if (arg0.startsWith("@")) {
                    String query = arg0.substring(1);
                    ms = cmd.longArg(1, 500); // glide so viewers see the mouse travel
                    double[] coords = findWidgetCoords(mc, query);
                    if (coords != null) {
                        targetX = coords[0];
                        targetY = coords[1];
                    } else {
                        throw new IllegalArgumentException("Widget not found for query @" + query);
                    }
                } else {
                    targetX = cmd.doubleArg(0, 0);
                    targetY = cmd.doubleArg(1, 0);
                    ms = cmd.longArg(2, 500);
                }

                double curGuiX = mc.mouseHandler.getScaledXPos(mc.getWindow());
                double curGuiY = mc.mouseHandler.getScaledYPos(mc.getWindow());
                int guiW = mc.getWindow().getGuiScaledWidth(), guiH = mc.getWindow().getGuiScaledHeight();
                if (!(curGuiX >= 0 && curGuiX <= guiW && curGuiY >= 0 && curGuiY <= guiH)) { // never moved yet: the real mouse is outside the off-screen window
                    curGuiX = guiW / 2.0;
                    curGuiY = guiH / 2.0;
                }

                if (ms > 0) {
                    cursorInterp = new CursorInterpolation(curGuiX, curGuiY, targetX, targetY, ms);
                    Thread.sleep(ms);
                } else {
                    final double fx = targetX, fy = targetY;
                    mc.execute(() -> {
                        double scale = mc.getWindow().getGuiScale();
                        mc.mouseHandler.onMove(mc.getWindow().handle(), fx * scale, fy * scale, 0, 0);
                    });
                }
            }
            case "click" -> {
                String btnStr = cmd.arg(0, "left").toLowerCase(Locale.ROOT);
                int btn = "right".equals(btnStr) ? InputConstants.MOUSE_BUTTON_RIGHT : InputConstants.MOUSE_BUTTON_LEFT; // 26.x (SDL): left 1, right 3
                var win = mc.getWindow(); // the editor zooms on each click (AGY-SHOW-V2 "Click zoom")
                marks.add((System.currentTimeMillis() - scriptStart) + String.format(Locale.ROOT, " click %.4f %.4f",
                    mc.mouseHandler.xpos() / win.getWidth(), mc.mouseHandler.ypos() / win.getHeight()));
                clickPulseAt = System.currentTimeMillis();
                mc.execute(() -> {
                    long handle = mc.getWindow().handle();
                    MouseButtonInfo info = new MouseButtonInfo(btn, 0);
                    mc.mouseHandler.onButton(handle, info, 1);
                    mc.mouseHandler.onButton(handle, info, 0);
                });
            }
            case "scroll" -> {
                double amount = cmd.doubleArg(0, 0);
                mc.execute(() -> mc.mouseHandler.onScroll(mc.getWindow().handle(), 0.0, amount));
            }
            case "type" -> {
                String text = cmd.arg(0, "");
                long msPerChar = cmd.longArg(1, 0);
                for (int i = 0; i < text.length(); i++) {
                    char ch = text.charAt(i);
                    mc.execute(() -> mc.keyboardHandler.charTyped(mc.getWindow().handle(), new CharacterEvent(ch)));
                    if (msPerChar > 0) Thread.sleep(msPerChar);
                }
            }
            case "key" -> {
                String keyName = cmd.arg(0, "").toLowerCase(Locale.ROOT);
                int keyCode = resolveKeyCode(keyName);
                if (keyCode >= 0) {
                    mc.execute(() -> {
                        long handle = mc.getWindow().handle();
                        KeyEvent press = new KeyEvent(keyCode, 0, 0);
                        mc.keyboardHandler.keyPress(handle, 1, press);
                        mc.keyboardHandler.keyPress(handle, 0, press);
                        if (keyName.length() == 1) {
                            mc.keyboardHandler.charTyped(handle, new CharacterEvent(keyName.charAt(0)));
                        }
                    });
                } else {
                    throw new IllegalArgumentException("Unknown raw key name: " + keyName);
                }
            }
            case "module" -> {
                String modName = cmd.arg(0, "");
                String state = cmd.arg(1, "on").toLowerCase(Locale.ROOT);
                boolean enabled = state.equals("on") || state.equals("true") || state.equals("1");
                Module m = findModule(modName);
                if (m != null) {
                    m.setEnabled(enabled);
                } else {
                    throw new IllegalArgumentException("Module not found: " + modName);
                }
            }
            case "set" -> {
                String modName = cmd.arg(0, "");
                String settingName = cmd.arg(1, "");
                String val = cmd.arg(2, "");
                Module m = findModule(modName);
                if (m == null) throw new IllegalArgumentException("Module not found: " + modName);
                Option opt = findOption(m, settingName);
                if (opt == null) throw new IllegalArgumentException("Setting not found on " + modName + ": " + settingName);

                if (opt instanceof BoolOption bo) {
                    bo.set(val.equalsIgnoreCase("on") || val.equalsIgnoreCase("true") || val.equals("1"));
                } else if (opt instanceof SliderOption so) {
                    so.parse(val).ifPresentOrElse(so::setValue, () -> so.setValue(Double.parseDouble(val)));
                } else if (opt instanceof DropdownOption dro) {
                    try {
                        dro.setIndex(Integer.parseInt(val));
                    } catch (Exception e) {
                        dro.load(new JsonPrimitive(val));
                    }
                } else if (opt instanceof ColorOption co) {
                    try {
                        long parsed = Long.decode(val);
                        co.setColor((int) parsed);
                    } catch (Exception e) {
                        co.load(new JsonPrimitive(val));
                    }
                } else {
                    opt.load(new JsonPrimitive(val));
                }
            }
            case "emote" -> {
                String id = emoteId(cmd.arg(0, ""));
                mc.execute(() -> {
                    if (mc.player != null) Emotes.playLocal(mc.player.getUUID(), id);
                });
            }
            case "mousedown", "mouseup" -> {
                int btn = "right".equalsIgnoreCase(cmd.arg(0, "left")) ? InputConstants.MOUSE_BUTTON_RIGHT : InputConstants.MOUSE_BUTTON_LEFT;
                int action = verb.equals("mousedown") ? 1 : 0;
                mc.execute(() -> mc.mouseHandler.onButton(mc.getWindow().handle(), new MouseButtonInfo(btn, 0), action));
            }
            case "drag" -> { // drag <x1> <y1> <x2> <y2> [ms] | drag @label <x2> <y2> [ms]: glide to the start, press, glide to the end, release
                double x1, y1;
                int i;
                if (cmd.arg(0, "").startsWith("@")) {
                    double[] c = findWidgetCoords(mc, cmd.arg(0, "").substring(1));
                    if (c == null) throw new IllegalArgumentException("Widget not found for query " + cmd.arg(0, ""));
                    x1 = c[0]; y1 = c[1]; i = 1;
                } else {
                    x1 = cmd.doubleArg(0, 0); y1 = cmd.doubleArg(1, 0); i = 2;
                }
                double x2 = cmd.doubleArg(i, 0), y2 = cmd.doubleArg(i + 1, 0);
                long ms = cmd.longArg(i + 2, 800);
                long handle = mc.getWindow().handle();
                glide(mc, x1, y1, 450);
                mc.execute(() -> mc.mouseHandler.onButton(handle, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0), 1));
                clickPulseAt = System.currentTimeMillis();
                Thread.sleep(120);
                glide(mc, x2, y2, ms);
                Thread.sleep(80);
                mc.execute(() -> mc.mouseHandler.onButton(handle, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0), 0));
            }
            case "hudpos" -> { // hudpos <Module> <x> <y> [center]: place a HUD module (GUI units, top-left; "center" = x/y is its center)
                Module m = findModule(cmd.arg(0, ""));
                if (m == null) throw new IllegalArgumentException("Module not found: " + cmd.arg(0, ""));
                int x = (int) cmd.doubleArg(1, 0), y = (int) cmd.doubleArg(2, 0);
                boolean center = "center".equalsIgnoreCase(cmd.arg(3, ""));
                onClient(mc, () -> {
                    if (center) {
                        for (var el : com.thelads.core.client.hud.HudManager.getInstance().getElements()) {
                            if (m.getName().equals(el.getModuleName())) {
                                HudSettings.getInstance().setPosition(m.getName(), x - el.getRenderWidth() / 2, y - el.getRenderHeight() / 2);
                                return null;
                            }
                        }
                    }
                    HudSettings.getInstance().setPosition(m.getName(), x, y);
                    return null;
                });
            }
            case "gui" -> { int n = cmd.intArg(0, 0); mc.execute(() -> mc.options.guiScale().set(n)); }
            case "hotbar" -> hideHotbar = !isOn(cmd.arg(0, "on"));
            case "npc" -> {
                String name = cmd.arg(0, ""), skin = cmd.arg(1, "steve");
                double x = spotX + cmd.doubleArg(2, 0), y = groundY + cmd.doubleArg(3, 0), z = spotZ + cmd.doubleArg(4, 0);
                float yaw = (float) cmd.doubleArg(5, 0);
                onClient(mc, () -> { ShowcaseNpcs26.put(mc, name, skin, x, y, z, yaw); return null; });
            }
            case "npcring" -> {
                int n = cmd.intArg(0, 10);
                double r = cmd.doubleArg(1, 4), cx = spotX + cmd.doubleArg(2, 0), cz = spotZ + cmd.doubleArg(3, 0), y = groundY;
                onClient(mc, () -> { ShowcaseNpcs26.ring(mc, n, r, cx, y, cz); return null; });
            }
            case "npcemote" -> {
                String id = emoteId(cmd.arg(1, ""));
                long stagger = cmd.longArg(2, 0);
                List<String> who = onClient(mc, () -> ShowcaseNpcs26.names(cmd.arg(0, "*")));
                for (int i = 0; i < who.size(); i++) {
                    String w = who.get(i);
                    SCHEDULER.schedule(() -> mc.execute(() -> ShowcaseNpcs26.emote(w, id)), i * stagger, TimeUnit.MILLISECONDS);
                }
            }
            case "npcstop" -> onClient(mc, () -> { ShowcaseNpcs26.names(cmd.arg(0, "*")).forEach(ShowcaseNpcs26::stop); return null; });
            case "npclook" -> {
                float yaw = (float) cmd.doubleArg(1, 0), pitch = (float) cmd.doubleArg(2, 0);
                onClient(mc, () -> { ShowcaseNpcs26.names(cmd.arg(0, "*")).forEach(w -> ShowcaseNpcs26.look(w, yaw, pitch)); return null; });
            }
            case "npcface" -> onClient(mc, () -> { ShowcaseNpcs26.names(cmd.arg(0, "*")).forEach(w -> ShowcaseNpcs26.face(mc, w)); return null; });
            case "npcwalk" -> {
                String w = cmd.arg(0, "");
                double x = spotX + cmd.doubleArg(1, 0), z = spotZ + cmd.doubleArg(2, 0);
                long ms = cmd.longArg(3, 1000);
                onClient(mc, () -> { ShowcaseNpcs26.walk(w, x, z, ms); return null; });
            }
            case "npcitem" -> {
                String item = cmd.arg(1, "minecraft:air");
                onClient(mc, () -> { ShowcaseNpcs26.names(cmd.arg(0, "*")).forEach(w -> ShowcaseNpcs26.item(w, item)); return null; });
            }
            case "npcswing" -> onClient(mc, () -> { ShowcaseNpcs26.names(cmd.arg(0, "*")).forEach(ShowcaseNpcs26::swing); return null; });
            case "npcsneak" -> {
                boolean on = isOn(cmd.arg(1, "on"));
                onClient(mc, () -> { ShowcaseNpcs26.names(cmd.arg(0, "*")).forEach(w -> ShowcaseNpcs26.sneak(w, on)); return null; });
            }
            case "npckill" -> { String w = cmd.arg(0, ""), how = cmd.arg(1, ""); onClient(mc, () -> { ShowcaseNpcs26.kill(mc, w, how); return null; }); } // npckill <name> [head|body]
            case "npcclear" -> onClient(mc, () -> { ShowcaseNpcs26.clear(mc); return null; });
            case "npctab" -> { boolean on = isOn(cmd.arg(0, "on")); onClient(mc, () -> { ShowcaseNpcs26.tab(mc, on); return null; }); }
            case "npcchat" -> { String w = cmd.arg(0, ""), text = cmd.tail(1); onClient(mc, () -> { ShowcaseNpcs26.chat(mc, w, text); return null; }); }
            default -> throw new IllegalArgumentException("Unknown showcase director verb: " + verb);
        }
    }

    private static void openScreenByName(Minecraft mc, String name) {
        Screen cur = mc.gui.screen();
        switch (name) {
            case "lads" -> mc.setScreenAndShow(new LadsSettingsScreen26(cur));
            case "hudedit" -> mc.setScreenAndShow(new DraggableHudScreen26(cur));
            case "cosmetics" -> mc.setScreenAndShow(new CosmeticsScreen26(cur));
            case "crosshair" -> mc.setScreenAndShow(new CrosshairDrawingScreen(cur));
            case "emotewheel" -> mc.setScreenAndShow(new EmoteWheelScreen26());
            case "inventory" -> mc.setScreenAndShow(new InventoryScreen(mc.player));
            case "chat" -> mc.setScreenAndShow(new ChatScreen("", false));
            case "pause" -> mc.setScreenAndShow(new PauseScreen(true));
            case "options" -> mc.setScreenAndShow(new OptionsScreen(cur, mc.options));
            case "close" -> mc.setScreenAndShow(null);
            default -> throw new IllegalArgumentException("Unknown screen alias: " + name);
        }
    }

    private static double[] findWidgetCoords(Minecraft mc, String query) {
        Screen s = mc.gui.screen();
        if (s == null) return null;
        String q = query.toLowerCase(Locale.ROOT);

        // 1. If LadsSettingsScreen26, check its internal controls
        if (s instanceof LadsSettingsScreen26 lads) {
            Rect r = lads.ui().controlBounds(query);
            if (r == null) r = lads.ui().controlBounds("card:" + query);
            if (r != null) return new double[] { r.x() + r.width() / 2.0, r.y() + r.height() / 2.0 };
            for (String cid : lads.ui().controlIds()) {
                if (cid.toLowerCase(Locale.ROOT).contains(q)) {
                    Rect cr = lads.ui().controlBounds(cid);
                    if (cr != null) return new double[] { cr.x() + cr.width() / 2.0, cr.y() + cr.height() / 2.0 };
                }
            }
        }

        // 2. Generic widget search in screen children
        AbstractWidget w = searchWidgets(s.children(), q);
        if (w != null) {
            return new double[] { w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0 };
        }
        return null;
    }

    /** `widgets`: every clickable thing on the current screen with its GUI-scaled bounds, for writing `cursor @<text>` lines. */
    private static String describeWidgets(Minecraft mc) {
        Screen s = mc.gui.screen();
        StringBuilder sb = new StringBuilder();
        sb.append("screen ").append(s == null ? "none" : s.getClass().getName())
          .append(" gui ").append(mc.getWindow().getGuiScaledWidth()).append("x").append(mc.getWindow().getGuiScaledHeight()).append(System.lineSeparator());
        if (s instanceof LadsSettingsScreen26 lads) {
            for (String cid : lads.ui().controlIds()) {
                Rect r = lads.ui().controlBounds(cid);
                if (r != null) sb.append(String.format(Locale.ROOT, "lads %s @ %d,%d %dx%d%n", cid, r.x(), r.y(), r.width(), r.height()));
            }
        }
        if (s != null) listWidgets(s.children(), sb);
        return sb.toString();
    }

    private static void listWidgets(List<?> elements, StringBuilder sb) {
        for (Object elem : elements) {
            if (elem instanceof AbstractWidget w && w.visible)
                sb.append(String.format(Locale.ROOT, "widget %s \"%s\" @ %d,%d %dx%d%n", w.getClass().getSimpleName(),
                    w.getMessage() == null ? "" : w.getMessage().getString(), w.getX(), w.getY(), w.getWidth(), w.getHeight()));
            if (elem instanceof ContainerEventHandler c) listWidgets(c.children(), sb);
        }
    }

    private static AbstractWidget searchWidgets(List<?> elements, String q) {
        for (Object elem : elements) {
            if (elem instanceof AbstractWidget widget) {
                if (widget.visible && widget.getMessage() != null) {
                    String msg = widget.getMessage().getString().toLowerCase(Locale.ROOT);
                    if (msg.contains(q)) return widget;
                }
            }
            if (elem instanceof ContainerEventHandler container) {
                AbstractWidget child = searchWidgets(container.children(), q);
                if (child != null) return child;
            }
        }
        return null;
    }

    private static KeyMapping resolveKeyMapping(Minecraft mc, String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "forward", "up" -> mc.options.keyUp;
            case "back", "down" -> mc.options.keyDown;
            case "left" -> mc.options.keyLeft;
            case "right" -> mc.options.keyRight;
            case "jump" -> mc.options.keyJump;
            case "sneak" -> mc.options.keyShift;
            case "sprint" -> mc.options.keySprint;
            case "attack" -> mc.options.keyAttack;
            case "use" -> mc.options.keyUse;
            case "tab", "playerlist" -> mc.options.keyPlayerList;
            case "chat" -> mc.options.keyChat;
            case "inventory" -> mc.options.keyInventory;
            case "drop" -> mc.options.keyDrop;
            case "perspective", "f5" -> mc.options.keyTogglePerspective;
            default -> {
                KeyMapping km = KeyMapping.get(name);
                if (km == null) {
                    for (KeyMapping k : mc.options.keyMappings) {
                        if (k.getName().equalsIgnoreCase(name) || k.getName().toLowerCase(Locale.ROOT).endsWith("." + lower)) {
                            km = k;
                            break;
                        }
                    }
                }
                yield km;
            }
        };
    }

    private static int resolveKeyCode(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return switch (n) {
            case "escape", "esc" -> InputConstants.KEY_ESCAPE;
            case "enter", "return" -> InputConstants.KEY_RETURN;
            case "tab" -> InputConstants.KEY_TAB;
            case "backspace" -> InputConstants.KEY_BACKSPACE;
            case "up" -> InputConstants.KEY_UP;
            case "down" -> InputConstants.KEY_DOWN;
            case "left" -> InputConstants.KEY_LEFT;
            case "right" -> InputConstants.KEY_RIGHT;
            case "space" -> InputConstants.KEY_SPACE;
            case "f1" -> InputConstants.KEY_F1;
            case "f2" -> InputConstants.KEY_F2;
            case "f3" -> InputConstants.KEY_F3;
            case "f4" -> InputConstants.KEY_F4;
            case "f5" -> InputConstants.KEY_F5;
            case "f6" -> InputConstants.KEY_F6;
            case "f7" -> InputConstants.KEY_F7;
            case "f8" -> InputConstants.KEY_F8;
            case "f9" -> InputConstants.KEY_F9;
            case "f10" -> InputConstants.KEY_F10;
            case "f11" -> InputConstants.KEY_F11;
            case "f12" -> InputConstants.KEY_F12;
            default -> {
                if (n.length() == 1) {
                    char c = Character.toUpperCase(n.charAt(0));
                    if (c >= 'A' && c <= 'Z') yield InputConstants.KEY_A + (c - 'A');
                    if (c >= '0' && c <= '9') yield InputConstants.KEY_0 + (c - '0');
                }
                try {
                    var k = InputConstants.getKey("key.keyboard." + n);
                    if (k != null && k != InputConstants.UNKNOWN) yield k.getValue();
                } catch (Exception ignored) {}
                yield -1;
            }
        };
    }

    /** Check runs only: the frame at a mark, to review a script without OBS. */
    private static void shot(Minecraft mc, Path file) {
        mc.execute(() -> {
            try {
                Files.createDirectories(file.getParent());
                Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
                    try { image.writeToFile(file); } catch (Exception e) { LOGGER.error("Showcase check screenshot failed", e); } finally { image.close(); }
                });
            } catch (Exception e) { LOGGER.error("Showcase check screenshot failed", e); }
        });
    }

    /** Module and option names match ignoring case and spaces ("Wavy Capes" = WavyCapes). */
    private static String key(String name) { return name.replace(" ", "").toLowerCase(Locale.ROOT); }

    private static Module findModule(String name) {
        Module m = ModuleManager.getInstance().getModule(name);
        if (m != null) return m;
        for (Module candidate : ModuleManager.getInstance().getModules()) {
            if (key(candidate.getName()).equals(key(name))) return candidate;
        }
        return null;
    }

    private static Option findOption(Module m, String name) {
        Option opt = m.getOption(name);
        if (opt != null) return opt;
        for (Option o : m.getOptions()) {
            if (key(o.getName()).equals(key(name))) return o;
        }
        return null;
    }

    private static void tpNow(MinecraftServer server, String who, double dx, double dy, double dz, float yaw, float pitch) {
        double x = spotX + dx, z = spotZ + dz;
        double y = groundY + dy;
        command(server, String.format(Locale.ROOT, "tp %s %.2f %.2f %.2f %.1f %.1f", who, x, y, z, yaw, pitch));
    }

    private static int top(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
    }

    private static void command(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static final java.util.concurrent.ConcurrentLinkedQueue<String> CMD_ERRORS = new java.util.concurrent.ConcurrentLinkedQueue<>();

    /** Why a command does not parse (unknown command, old item/NBT syntax...), or null when it parses. Server thread. */
    private static String syntaxError(MinecraftServer server, String command) {
        String c = command.startsWith("/") ? command.substring(1) : command;
        var parse = server.getCommands().getDispatcher().parse(c, server.createCommandSourceStack());
        if (!parse.getExceptions().isEmpty()) return parse.getExceptions().values().iterator().next().getMessage();
        return parse.getReader().canRead() ? "stops at: " + parse.getReader().getRemaining() : null;
    }

    private static void commandAsPlayer(Minecraft mc, MinecraftServer server, String command) {
        if (mc.player != null) {
            ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
            if (sp != null) {
                CommandSourceStack src = sp.createCommandSourceStack().withPermission(server.createCommandSourceStack().permissions()).withSuppressedOutput();
                server.getCommands().performPrefixedCommand(src, command);
                return;
            }
        }
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static void status(String text) {
        write("status.txt", text);
    }

    private static void write(String file, String text) {
        try {
            if (dir != null) {
                Files.writeString(dir.resolve(file), text, StandardCharsets.UTF_8);
            }
        } catch (Exception failure) {
            LOGGER.error("Showcase director cannot write {}", file, failure);
        }
    }

    private static void appendYawLog(long now, int frame, long dt, float yaw, float pitch) {
        try {
            if (dir != null) {
                String entry = String.format(Locale.ROOT, "%d frame=%d dt=%d yaw=%.4f pitch=%.4f\n", now, frame, dt, yaw, pitch);
                Files.writeString(dir.resolve("yaw-debug.txt"), entry, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (Exception ignored) {}
    }
}
