package com.thelads.core.v26_2.cosmetics.anim;

import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.client.cosmetics.Emotes;
import com.thelads.core.client.cosmetics.anim.EmoteLibrary;
import com.thelads.core.client.cosmetics.anim.EmoteWheel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Showcase only (LADS_SHOWCASE_EMOTE=host|join, LADS_SHOWCASE_DIR, inside artifacts/verification): an emote showcase recorded by OBS.
 * The host makes a natural world at noon and opens it to LAN (offline, port LADS_SHOWCASE_PORT); the join game connects to it. Skins
 * come by player name from DIR/skins/name.png (name.slim = slim arms). A controller appends "n verb shot" lines to DIR/cmd-name.txt
 * (verb prep|run|quit); each finished step writes n to DIR/done-name.txt. Camera, emotes and walking are driven from code: no OS input,
 * no focus.
 */
public final class ShowcaseEmote26 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final String ROLE = System.getenv("LADS_SHOWCASE_EMOTE");
    private static final String SAVE = "LadsShowcase";
    private static final int FLOOR = 12; // the lawn reaches this far from the spot
    private static final Map<String, PlayerSkin> SKINS = new HashMap<>();
    private static final TreeMap<Long, List<Runnable>> STEPS = new TreeMap<>();
    private static boolean active, skinsLoaded, worldAsked, setUp, ready;
    private static Path dir;
    private static String name;
    private static int port, lastCmd, running = -1;
    private static long t0, titleSince, nextPoll, connectAt, nextFps;
    private static double spotX, spotZ;
    private static long glideStart, glideEnd;

    private ShowcaseEmote26() {}

    public static void register() {
        if (ROLE == null) return;
        try {
            Path game = FabricLoader.getInstance().getGameDir().toRealPath();
            Path verification = game.getParent();
            if (!ROLE.matches("host|join") || verification == null || !verification.getFileName().toString().equals("verification")
                || !verification.getParent().getFileName().toString().equals("artifacts")
                || !Files.isRegularFile(verification.getParent().getParent().resolve("TheLadsCore/settings.gradle")))
                throw new IllegalStateException("the showcase runs only as host|join in a game folder under artifacts/verification");
            dir = Path.of(System.getenv("LADS_SHOWCASE_DIR")).toRealPath();
            if (!dir.startsWith(verification)) throw new IllegalStateException("LADS_SHOWCASE_DIR must be under " + verification);
            port = Integer.parseInt(System.getenv().getOrDefault("LADS_SHOWCASE_PORT", "25599"));
        } catch (Exception failure) { LOGGER.error("Lads showcase OFF: {}", failure.toString()); return; }
        active = true;
        ClientTickEvents.END_CLIENT_TICK.register(ShowcaseEmote26::tick);
        LOGGER.info("Lads showcase BEGIN: role {}, control folder {}", ROLE, dir);
    }

    /** First person shows the hand with the HUD hidden (F1), as a player sees it. */
    public static boolean handsWithHudHidden() { return active; }

    /** This player's showcase skin by name, or null. */
    public static PlayerSkin skin(AbstractClientPlayer player) { return active ? SKINS.get(player.getPlainTextName().toLowerCase(Locale.ROOT)) : null; }

    private static void tick(Minecraft mc) {
        try {
            if (!skinsLoaded) loadSkins(mc);
            if (mc.level == null) { lobby(mc); return; }
            if (mc.player == null || !setUp && mc.gui.screen() != null) return; // the world is still loading
            if (!setUp) setUp(mc);
            long now = System.currentTimeMillis();
            if (!ready && now - t0 > 15_000) { ready = true; status("ready " + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight()); }
            if (now >= nextFps) { nextFps = now + 500; write("fps-" + ROLE + ".txt", String.valueOf(mc.getFps())); } // the controller waits for a steady rate
            if (glideEnd > 0 && mc.gui.screen() instanceof EmoteWheelScreen26) glide(mc, now);
            if (running >= 0) {
                while (!STEPS.isEmpty() && STEPS.firstKey() <= now - t0) for (Runnable step : STEPS.pollFirstEntry().getValue()) step.run();
                if (STEPS.isEmpty()) { write("done-" + ROLE + ".txt", String.valueOf(running)); LOGGER.info("Lads showcase step {} done", running); running = -1; }
            } else if (ready && now >= nextPoll) { nextPoll = now + 200; poll(mc); }
        } catch (Exception failure) { LOGGER.error("Lads showcase step failed", failure); }
    }

    /** Title screen: the host makes or opens its world; the join game connects once the host is ready (and again after a refusal). */
    private static void lobby(Minecraft mc) {
        var screen = mc.gui.screen();
        boolean title = screen instanceof TitleScreen || screen != null && screen.getClass().getSimpleName().equals("LadsTitleScreen26");
        long now = System.currentTimeMillis();
        if (ROLE.equals("join") && screen instanceof DisconnectedScreen && now > connectAt) { mc.setScreenAndShow(new TitleScreen()); return; }
        if (!title || mc.gui.overlay() != null || !mc.isGameLoadFinished()) { titleSince = 0; return; }
        if (titleSince == 0) { titleSince = now; status("title"); }
        if (now - titleSince < 3000) return;
        if (ROLE.equals("host") && !worldAsked) {
            worldAsked = true;
            if (Files.isRegularFile(mc.gameDirectory.toPath().resolve("saves").resolve(SAVE).resolve("level.dat"))) {
                mc.createWorldOpenFlows().openWorld(SAVE, () -> LOGGER.error("Lads showcase: world open cancelled"));
                return;
            }
            long seed = Long.parseLong(System.getenv().getOrDefault("LADS_SHOWCASE_SEED", "20261018"));
            LOGGER.info("Lads showcase: new natural world, seed {}", seed);
            var settings = new net.minecraft.world.level.LevelSettings("Lads Showcase", net.minecraft.world.level.GameType.CREATIVE,
                new net.minecraft.world.level.LevelSettings.DifficultySettings(net.minecraft.world.Difficulty.PEACEFUL, false, true), true,
                net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
            mc.createWorldOpenFlows().createFreshLevel(SAVE, settings, new net.minecraft.world.level.levelgen.WorldOptions(seed, true, false),
                registries -> registries.lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                    .getOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.NORMAL).value().createWorldDimensions(), screen);
        } else if (ROLE.equals("join") && now > connectAt && Files.isRegularFile(dir.resolve("host-ready"))) {
            connectAt = now + 10_000;
            String address = "127.0.0.1:" + port;
            LOGGER.info("Lads showcase: joining {}", address);
            ConnectScreen.startConnecting(screen, mc, ServerAddress.parseString(address), new ServerData("Lads Showcase", address, ServerData.Type.OTHER), false, null);
        }
    }

    private static void setUp(Minecraft mc) {
        setUp = true;
        t0 = System.currentTimeMillis();
        name = mc.getUser().getName().toLowerCase(Locale.ROOT);
        hud(mc, true);
        mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        // Recorded off screen and unfocused: Dynamic FPS would hold it at its Unfocused FPS (60), so the showcase turns it off.
        var dynamicFps = com.thelads.core.config.ModuleManager.getInstance().getModule("DynamicFPS");
        if (dynamicFps != null && dynamicFps.isEnabled()) dynamicFps.setEnabled(false);
        if (ROLE.equals("join")) { status("joined"); return; }
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            for (String c : new String[] {"gamerule advance_time false", "gamerule advance_weather false", "time set noon", "weather clear",
                "gamerule spawn_phantoms false", "gamerule show_death_messages false", "gamerule announce_advancements false"}) command(server, c);
            ServerLevel level = server.overworld();
            BlockPos origin = new BlockPos(0, 80, 0); // fixed: a reopened world finds the same spot
            var found = level.findClosestBiome3d(h -> h.is(Biomes.PLAINS) || h.is(Biomes.SUNFLOWER_PLAINS), origin, 6400, 32, 64);
            BlockPos around = found == null ? origin : found.getFirst();
            // The flattest tree-free grass within 96 blocks (leaves count in MOTION_BLOCKING), 25 x 25 around it made a clean lawn.
            int bestX = around.getX(), bestZ = around.getZ(), bestSpread = Integer.MAX_VALUE;
            for (int cx = around.getX() - 96; cx <= around.getX() + 96; cx += 6)
                for (int cz = around.getZ() - 96; cz <= around.getZ() + 96; cz += 6) {
                    int top = top(level, cx, cz);
                    if (!level.getBlockState(new BlockPos(cx, top - 1, cz)).is(Blocks.GRASS_BLOCK)) continue;
                    int lo = top, hi = top, wet = 0; // wet: samples that are not grass (water, sand, stone)
                    for (int dx = -FLOOR; dx <= FLOOR; dx += 3) for (int dz = -FLOOR; dz <= FLOOR; dz += 3) {
                        int h = top(level, cx + dx, cz + dz);
                        lo = Math.min(lo, h); hi = Math.max(hi, h);
                        if (!level.getBlockState(new BlockPos(cx + dx, h - 1, cz + dz)).is(Blocks.GRASS_BLOCK)) wet++;
                    }
                    if (hi - lo + wet < bestSpread) { bestSpread = hi - lo + wet; bestX = cx; bestZ = cz; }
                }
            spotX = bestX + 0.5; spotZ = bestZ + 0.5;
            int y = top(level, bestX, bestZ);
            LOGGER.info("Lads showcase spot: {} {} {} (biome search {}, height spread {})", spotX, y, spotZ, found == null ? "none" : around, bestSpread);
            // Level lawn: no tall grass or flowers hiding the feet, no dips for the camera to sink into.
            String box = String.format(Locale.ROOT, "%d %%d %d %d %%d %d", bestX - FLOOR, bestZ - FLOOR, bestX + FLOOR, bestZ + FLOOR);
            command(server, "fill " + String.format(Locale.ROOT, box, y, y + 8) + " air");
            command(server, "fill " + String.format(Locale.ROOT, box, y - 3, y - 2) + " dirt");
            command(server, "fill " + String.format(Locale.ROOT, box, y - 1, y - 1) + " grass_block");
            command(server, "setworldspawn " + bestX + " " + y + " " + bestZ);
            tpNow(server, name, 0, 0, 0, 0);
            mc.execute(() -> { // as Open to LAN does it: on the client thread
                server.setUsesAuthentication(false); // offline showcase accounts on our own LAN game
                boolean published = server.publishServer(MinecraftServer.MultiplayerScope.LAN, true, port);
                server.setUsesAuthentication(false);
                LOGGER.info("Lads showcase: LAN on port {}: {}", port, published);
                write("host-ready", "port " + port);
            });
        });
        status("world");
    }

    /** The next controller line: "n verb shot". */
    private static void poll(Minecraft mc) throws Exception {
        Path file = dir.resolve("cmd-" + ROLE + ".txt");
        if (!Files.isRegularFile(file)) return;
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        if (lines.isEmpty()) return;
        String[] cmd = lines.get(lines.size() - 1).trim().split(" ");
        int n = Integer.parseInt(cmd[0]);
        if (n <= lastCmd) return;
        lastCmd = n;
        LOGGER.info("Lads showcase step {}: {} {}", n, cmd[1], cmd.length > 2 ? cmd[2] : "");
        if (cmd[1].equals("quit")) { write("done-" + ROLE + ".txt", String.valueOf(n)); mc.stop(); return; }
        if (cmd[1].equals("perf")) { // "n perf fps renderDistance": the recorded game runs fast, the other one light
            mc.options.framerateLimit().set(Integer.parseInt(cmd[2]));
            mc.options.renderDistance().set(Integer.parseInt(cmd[3]));
            write("done-" + ROLE + ".txt", String.valueOf(n));
            return;
        }
        STEPS.clear();
        t0 = System.currentTimeMillis();
        boolean host = ROLE.equals("host");
        if (cmd[1].equals("prep")) prep(mc, host, cmd[2]);
        else { write("started-" + ROLE + ".txt", n + " " + t0); run(mc, host, cmd[2]); } // the clip's timeline starts here
        at(0, () -> {}); // every step ends once its last action ran
        running = n;
    }

    private static void prep(Minecraft mc, boolean host, String shot) {
        var self = mc.player.getUUID();
        at(0, () -> {
            Emotes.stopLocal(self); mc.options.keyUp.setDown(false); if (mc.gui.screen() != null) mc.setScreenAndShow(null);
            MinecraftServer server = mc.getSingleplayerServer();
            // Drops from plants the lawn cut loose, and anything picked up; the other player watches unseen (spectator) during solos.
            if (server != null) server.execute(() -> {
                command(server, "kill @e[type=item]");
                command(server, "clear @a");
                command(server, "gamemode " + (shot.startsWith("solo:") ? "spectator" : "creative") + " @a[name=!" + name + "]");
            });
        });
        if (shot.startsWith("solo:")) { // each emote alone from the front, every one against another side of the view
            float yaw = 190 + 25 * Math.max(0, EmoteLibrary.ids().indexOf(shot.substring(5))); // the open side of the spot
            at(0, () -> { tp(mc, name, 0, 0, yaw, 0); camera(mc, CameraType.THIRD_PERSON_FRONT, true); });
        } else switch (shot) {
            case "stop3p" -> at(0, () -> { tp(mc, name, 0, 0, 0, 0); camera(mc, CameraType.THIRD_PERSON_FRONT, true); });
            case "back" -> at(0, () -> { tp(mc, name, 0, 0, 0, 0); camera(mc, CameraType.THIRD_PERSON_BACK, true); });
            case "fp" -> at(0, () -> { tp(mc, name, 0, 0, 0, 12); camera(mc, CameraType.FIRST_PERSON, true); });
            case "wheel" -> at(0, () -> { tp(mc, name, 0, 0, 240, 0); camera(mc, CameraType.THIRD_PERSON_FRONT, false); mc.gui.hud.getChat().clearMessages(false); });
            // Two players: the host places both. herobrine looks at arash_yt from 4 blocks; then side by side, 1.7 apart.
            case "duo-view" -> at(0, () -> {
                if (host) { tp(mc, name, 0, 0, 180, 0); tp(mc, "@a[name=!" + name + "]", 0, -4.2, 0, 8); }
                camera(mc, host ? CameraType.THIRD_PERSON_BACK : CameraType.FIRST_PERSON, true);
            });
            // arash_yt looks at herobrine from 4 blocks.
            case "duo-arash-view" -> at(0, () -> {
                if (host) { tp(mc, name, 0, 4.2, 180, 8); tp(mc, "@a[name=!" + name + "]", 0, 0, 0, 0); }
                camera(mc, host ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK, true);
            });
            case "duo-dance", "duo-host" -> at(0, () -> {
                if (host) { tp(mc, name, 1.7, 0, 0, 0); tp(mc, "@a[name=!" + name + "]", 0, 0, 0, 0); }
                camera(mc, CameraType.THIRD_PERSON_FRONT, true);
            });
            default -> LOGGER.warn("Lads showcase: unknown shot {}", shot);
        }
        end(2500);
    }

    private static void run(Minecraft mc, boolean host, String shot) {
        var self = mc.player.getUUID();
        long groove = period(EmoteLibrary.GROOVE), floss = period(EmoteLibrary.FLOSS);
        if (shot.startsWith("solo:")) { play(self, 500, shot.substring(5)); end(9500); return; }
        switch (shot) {
            // Groove, then walking: the arms swing back into the walk (blend-out).
            case "stop3p" -> { play(self, 700, EmoteLibrary.GROOVE); walk(mc, 700 + 3 * groove, 1300); end(700 + 3 * groove + 2800); }
            // From behind: Floss, then a tap of the key: it blends out standing.
            case "back" -> { play(self, 600, EmoteLibrary.FLOSS); at(600 + 3 * floss, () -> Emotes.stopLocal(self)); end(600 + 3 * floss + 1600); }
            // First person: the dancing arms (both hands), walking stops it; then Floss and walking again.
            case "fp" -> {
                play(self, 600, EmoteLibrary.GROOVE);
                walk(mc, 600 + 2 * groove, 800);
                long next = 600 + 2 * groove + 2000;
                play(self, next, EmoteLibrary.FLOSS);
                walk(mc, next + 2 * floss, 800);
                end(next + 2 * floss + 2000);
            }
            // The wheel (as the Emotes key opens it): the pointer glides to Floss, the key is let go over it.
            case "wheel" -> {
                at(700, () -> {
                    mc.setScreenAndShow(new EmoteWheelScreen26());
                    pointer(mc, 0);
                    glideStart = System.currentTimeMillis() + 500; glideEnd = glideStart + 1100;
                });
                at(3300, () -> {
                    if (mc.gui.screen() instanceof EmoteWheelScreen26 wheel) wheel.keyReleased(new KeyEvent(EmoteAnim26.KEY.getDefaultKey().getValue(), 0, 0));
                    EmoteWheelScreen26.qaPointer = null;
                    glideEnd = 0;
                });
                end(3300 + 3 * floss);
            }
            // herobrine's view: arash_yt grooves, then turns and walks off (herobrine sees it stop).
            case "duo-view" -> {
                if (host) {
                    play(self, 900, EmoteLibrary.GROOVE);
                    at(900 + 3 * groove, () -> mc.player.setYRot(0));
                    walk(mc, 900 + 3 * groove + 150, 1600);
                }
                end(900 + 3 * groove + 3000);
            }
            case "duo-dance" -> { play(self, 800, EmoteLibrary.GROOVE); play(self, 800 + 3 * groove, EmoteLibrary.FLOSS); end(800 + 3 * groove + 3 * floss + 400); }
            case "duo-host" -> { play(self, 800, EmoteLibrary.FLOSS); end(800 + 3 * floss + 400); }
            // herobrine dances for arash_yt: Floss, then Groove.
            case "duo-arash-view" -> {
                if (!host) { play(self, 900, EmoteLibrary.FLOSS); play(self, 900 + 3 * floss, EmoteLibrary.GROOVE); }
                end(900 + 3 * floss + 3 * groove + 400);
            }
            default -> LOGGER.warn("Lads showcase: unknown shot {}", shot);
        }
    }
    private static long period(String id) { return Math.round(EmoteLibrary.get(id).periodMs); }

    private static void end(long ms) { at(ms, () -> {}); }
    private static void at(long ms, Runnable step) { STEPS.computeIfAbsent(ms, k -> new ArrayList<>()).add(step); }

    private static void play(java.util.UUID self, long ms, String id) { at(ms, () -> Emotes.playLocal(self, id)); }

    private static void walk(Minecraft mc, long ms, long forMs) {
        at(ms, () -> mc.options.keyUp.setDown(true));
        at(ms + forMs, () -> mc.options.keyUp.setDown(false));
    }

    private static void camera(Minecraft mc, CameraType type, boolean hudHidden) { mc.options.setCameraType(type); hud(mc, hudHidden); }

    private static void hud(Minecraft mc, boolean hidden) { if (mc.gui.hud.isHidden() != hidden) mc.gui.hud.toggle(); }

    /** The wheel's virtual pointer from its middle to the Floss slot, eased. */
    private static void glide(Minecraft mc, long now) {
        float k = Math.max(0, Math.min(1, (now - glideStart) / (float) (glideEnd - glideStart)));
        k = k * k * (3 - 2 * k);
        pointer(mc, k);
    }

    private static void pointer(Minecraft mc, float amount) {
        int slot = Math.max(0, java.util.Arrays.asList(com.thelads.core.modules.CosmeticsModule.get().slots()).indexOf(EmoteLibrary.FLOSS));
        double a = 2 * Math.PI * slot / EmoteLibrary.SLOTS, r = (EmoteWheel.OUTER - 20) * amount;
        EmoteWheelScreen26.qaPointer = new int[] {mc.getWindow().getGuiScaledWidth() / 2 + (int) Math.round(Math.sin(a) * r),
            mc.getWindow().getGuiScaledHeight() / 2 - (int) Math.round(Math.cos(a) * r)};
    }

    /** Teleport a player (name or selector) to the spot plus (dx, dz), standing on the ground there. */
    private static void tp(Minecraft mc, String who, double dx, double dz, float yaw, float pitch) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) server.execute(() -> tpNow(server, who, dx, dz, yaw, pitch));
        else { mc.player.setYRot(yaw); mc.player.setYHeadRot(yaw); mc.player.setXRot(pitch); }
    }

    private static void tpNow(MinecraftServer server, String who, double dx, double dz, float yaw, float pitch) {
        double x = spotX + dx, z = spotZ + dz;
        int y = top(server.overworld(), (int) Math.floor(x), (int) Math.floor(z));
        command(server, String.format(Locale.ROOT, "tp %s %.2f %d %.2f %.1f %.1f", who, x, y, z, yaw, pitch));
    }

    /** The ground's top at x, z; the chunk is loaded (made) first: an unloaded one reads as the bottom of the world. */
    private static int top(ServerLevel level, int x, int z) {
        level.getChunk(x >> 4, z >> 4);
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
    }

    private static void command(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static void loadSkins(Minecraft mc) throws Exception {
        if (mc.getTextureManager() == null) return;
        skinsLoaded = true;
        try (var files = Files.list(dir.resolve("skins"))) {
            for (Path png : files.filter(p -> p.toString().endsWith(".png")).toList()) {
                String who = png.getFileName().toString().replace(".png", "").toLowerCase(Locale.ROOT);
                NativeImage image = com.thelads.core.v26_2.mixin.SkinTextureAccessor.ladsNormalizeSkin(NativeImage.read(Files.readAllBytes(png)), "Lads showcase skin");
                Identifier id = Identifier.fromNamespaceAndPath("theladscore", "showcase_skin/" + who);
                mc.getTextureManager().register(id, new DynamicTexture(() -> "Lads showcase skin " + who, image));
                boolean slim = Files.exists(dir.resolve("skins").resolve(who + ".slim"));
                SKINS.put(who, new PlayerSkin(new ClientAsset.ResourceTexture(id, id), null, null, slim ? PlayerModelType.SLIM : PlayerModelType.WIDE, false));
                LOGGER.info("Lads showcase skin: {} ({})", who, slim ? "slim" : "wide");
            }
        }
    }

    private static void status(String text) { write("status-" + ROLE + ".txt", text); }

    private static void write(String file, String text) {
        try { Files.writeString(dir.resolve(file), text, StandardCharsets.UTF_8); }
        catch (Exception failure) { LOGGER.error("Lads showcase: cannot write {}", file, failure); }
    }
}
