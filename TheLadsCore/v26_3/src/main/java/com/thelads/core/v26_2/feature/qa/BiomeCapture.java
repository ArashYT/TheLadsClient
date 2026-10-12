package com.thelads.core.v26_2.feature.qa;

import com.mojang.blaze3d.pipeline.RenderTarget;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (-Dthelads.verifyBiomeCapture=&lt;shard&gt;/&lt;shards&gt;, the harness's LADS_VERIFY_CAPTURE_BIOMES): photographs every
 * biome of this version for the Crosshair Tweaks preview backgrounds. At the title screen it makes (or reopens) a normal-terrain
 * creative world in the QA sandbox, then per biome: an interior spot found from the world generator (the surface biome there and
 * all around it), a view along the most open direction that stays in the biome, the camera just above everything near it
 * (spectator, HUD hidden, no clouds, clear weather, render distance 10, FOV 70), and screenshots/biomes/&lt;biome&gt;_&lt;time&gt;.png at
 * six times of day; sunrise, morning and night look east (sun and moon rise there), noon, sunset and midnight west. Cave, Nether
 * and End biomes get one shot each from the most open air pocket (caves with night vision). Two games split the list by shard.
 */
public final class BiomeCapture {
    private static final Logger LOG = LoggerFactory.getLogger("TheLadsCore");
    private static final String SPEC = System.getProperty("thelads.verifyBiomeCapture");
    /** Only a QA run started with -Dthelads.verifyBiomeCapture (the harness's LADS_VERIFY_CAPTURE_BIOMES); players never have it. */
    public static final boolean ACTIVE = SPEC != null;
    private static final String WORLD = "LadsBiomes";
    private static final long SEED = 20261011L;
    private static final int RENDER_DISTANCE = 10, SCAN_RADIUS = 4096, SCAN_STEP = 32;
    // East-facing times first, then the west-facing ones: one turn per biome.
    private static final String[] TIMES = {"sunrise", "morning", "night", "noon", "sunset", "midnight"};
    private static final int[] TICKS = {23400, 1000, 13800, 6000, 12600, 18000};
    private static final boolean[] EAST = {true, true, true, false, false, false};
    private static final float PITCH = 4;

    private record Target(String biome, ResourceKey<Level> dimension, int x, int y, int z, float east, float west, boolean pocket) {}
    private record Place(double x, double y, double z, float east, float west) {}

    private enum Phase { TITLE, TITLE_UI, WORLD, SETUP, SCAN, GOTO, LOAD, PLACE, SETTLE, SHOOT, SAVING, UI, DONE }
    /** -Dthelads.verifyBiomeCapture=ui: no captures; the Crosshair Tweaks page and drawing editor with every background instead. */
    private static final boolean UI = "ui".equals(SPEC);
    private record UiStep(String shot, Runnable action) {}
    private static final List<UiStep> UI_STEPS = new ArrayList<>();
    private static int uiStep;
    private static boolean titleShot;
    private static Phase phase = Phase.TITLE;
    private static long since, firstTitle, titleSince, loadStable, renderedSince;
    private static int lastChunks, index, shot, saved, failed;
    private static volatile List<Target> targets;
    private static volatile Place place;
    private static volatile String serverFailure;
    private static List<Target> mine;
    private static float yaw = Float.NaN;
    private static boolean nightVision, turned, skip;
    /** The last camera move, sent again when the camera is not there 15 s later. */
    private static String tp;
    private static long tpAt;
    private static final StringBuilder MANIFEST = new StringBuilder("file,biome,dimension,x,y,z,yaw,pitch,ticks,camera_biome\n");

    private BiomeCapture() {}

    /** Each completed frame (RenderScaleMixin). */
    public static void frame(RenderTarget target) {
        if (!ACTIVE || phase == Phase.DONE) return;
        try { step(Minecraft.getInstance(), target, System.nanoTime()); }
        catch (Throwable failure) { finish("FAILED: " + failure); LOG.error("Lads biome capture FAILED", failure); }
    }

    private static void step(Minecraft mc, RenderTarget target, long now) throws Exception {
        if (serverFailure != null) throw new IllegalStateException("server: " + serverFailure);
        switch (phase) {
            case TITLE -> {
                boolean title = mc.gui.screen() instanceof TitleScreen || mc.gui.screen() != null && mc.gui.screen().getClass().getSimpleName().startsWith("LadsTitleScreen");
                if (!mc.isGameLoadFinished() || mc.gui.overlay() != null || !title) { titleSince = 0; return; }
                if (firstTitle == 0) firstTitle = now;
                if (titleSince == 0) titleSince = now;
                // The title-screen probes of a --title run (reconnect, narrator, shared content) go first.
                if (now - titleSince < 20_000_000_000L || now - firstTitle < 40_000_000_000L) return;
                Path game = mc.gameDirectory.toPath().toRealPath();
                if (game.getParent() == null || !game.getParent().getFileName().toString().equals("verification")
                    || game.getParent().getParent() == null || !game.getParent().getParent().getFileName().toString().equals("artifacts"))
                    throw new IllegalStateException("not an isolated QA folder: " + game);
                if (UI && !titleShot) {
                    // Live Game on the title screen: the chosen biome stands in.
                    titleShot = true;
                    crosshair().background.setIndex(com.thelads.core.client.CrosshairBackground.LIVE);
                    openSettings(mc);
                    next(Phase.TITLE_UI, now);
                    return;
                }
                if (mc.getLevelSource().levelExists(WORLD)) {
                    LOG.info("Lads biome capture: opening {}", WORLD);
                    mc.createWorldOpenFlows().openWorld(WORLD, () -> finish("FAILED: world open cancelled"));
                } else {
                    LOG.info("Lads biome capture: creating {} (normal terrain, seed {}, creative, peaceful, cheats on)", WORLD, SEED);
                    var settings = new net.minecraft.world.level.LevelSettings(WORLD, net.minecraft.world.level.GameType.CREATIVE,
                        new net.minecraft.world.level.LevelSettings.DifficultySettings(net.minecraft.world.Difficulty.PEACEFUL, false, false), true,
                        net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
                    mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new net.minecraft.world.level.levelgen.WorldOptions(SEED, true, false),
                        registries -> registries.lookupOrThrow(Registries.WORLD_PRESET)
                            .getOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.NORMAL).value().createWorldDimensions(), mc.gui.screen());
                }
                next(Phase.WORLD, now);
            }
            case TITLE_UI -> {
                if (now - since < 2_500_000_000L) return;
                next(Phase.SAVING, now);
                shoot(target, "ui-0-title-live-fallback", () -> {
                    if (Minecraft.getInstance().gui.screen() != null) Minecraft.getInstance().gui.screen().onClose(); // back to the title
                    titleSince = System.nanoTime() - 20_000_000_000L;
                    next(Phase.TITLE, System.nanoTime());
                });
            }
            case UI -> {
                if (now - since < 2_000_000_000L) return;
                UiStep step = UI_STEPS.get(uiStep);
                next(Phase.SAVING, now);
                shoot(target, step.shot(), () -> {
                    if (++uiStep >= UI_STEPS.size()) { finish("END"); return; }
                    UI_STEPS.get(uiStep).action().run();
                    next(Phase.UI, System.nanoTime());
                });
            }
            case WORLD -> {
                if (mc.gui.screen() != null && mc.gui.screen().getClass() == PauseScreen.class) mc.setScreenAndShow(null);
                if (mc.level == null || mc.player == null || mc.gui.screen() != null || mc.getSingleplayerServer() == null) { since = now; return; }
                if (now - since > 8_000_000_000L) next(Phase.SETUP, now); // the in-world one-shot probes go first
            }
            case SETUP -> {
                if (UI) { uiSteps(mc); uiStep = 0; UI_STEPS.get(0).action().run(); next(Phase.UI, now); return; }
                mc.options.renderDistance().set(RENDER_DISTANCE);
                mc.options.cloudStatus().set(CloudStatus.OFF);
                mc.options.fov().set(70);
                MinecraftServer server = mc.getSingleplayerServer();
                server.execute(() -> {
                    command(server, "gamemode spectator @a");
                    command(server, "effect clear @a"); // a reopened world keeps an earlier run's night vision
                    command(server, "difficulty peaceful");
                    command(server, "weather clear");
                    command(server, "time set 6000");
                    command(server, "time pause");
                    server.getGameRules().set(GameRules.ADVANCE_WEATHER, false, server);
                    try { targets = scan(server); } catch (Throwable failure) { serverFailure = String.valueOf(failure); LOG.error("Lads biome capture scan", failure); }
                });
                next(Phase.SCAN, now);
            }
            case SCAN -> {
                if (targets == null) return;
                String[] shard = SPEC.contains("/") ? SPEC.split("/") : new String[] {"0", "1"};
                int part = Integer.parseInt(shard[0]), parts = Math.max(1, Integer.parseInt(shard[1]));
                mine = new ArrayList<>();
                // A redo: <game>/.lads-qa-biomes names the biomes to photograph again (one id per line), whatever the shard.
                Path only = mc.gameDirectory.toPath().resolve(".lads-qa-biomes");
                java.util.Set<String> redo = Files.isRegularFile(only) ? new java.util.HashSet<>(Files.readAllLines(only).stream().map(String::trim).toList()) : null;
                for (int i = 0; i < targets.size(); i++) if (redo != null ? redo.contains(targets.get(i).biome()) : i % parts == part) mine.add(targets.get(i));
                LOG.info("Lads biome capture BEGIN: shard {}/{}: {} of {} biomes: {}", part, parts, mine.size(), targets.size(),
                    mine.stream().map(Target::biome).toList());
                index = 0;
                next(Phase.GOTO, now);
            }
            case GOTO -> {
                if (index >= mine.size()) { finish("END"); return; }
                Target t = mine.get(index);
                hideHud(mc);
                MinecraftServer server = mc.getSingleplayerServer();
                boolean vision = t.pocket() && t.dimension() == Level.OVERWORLD;
                boolean clear = nightVision && !vision;
                nightVision = vision;
                server.execute(() -> {
                    command(server, "weather clear");
                    if (vision) command(server, "effect give @a minecraft:night_vision infinite 0 true");
                    if (clear) command(server, "effect clear @a");
                });
                // High above the spot (surface) or at it (pockets), so its chunks load around the camera.
                teleport(server, "execute in " + t.dimension().identifier() + " run tp @a " + t.x() + " " + (t.pocket() ? t.y() : t.y() + 40) + " " + t.z() + " " + t.east() + " " + PITCH);
                place = null;
                lastChunks = -1;
                loadStable = renderedSince = 0;
                LOG.info("Lads biome capture: {} ({}/{}) -> {} {} {} {}", t.biome(), index + 1, mine.size(), t.dimension().identifier(), t.x(), t.y(), t.z());
                next(Phase.LOAD, now);
            }
            case LOAD -> {
                Target t = mine.get(index);
                if (!loaded(mc, t.dimension(), t.x(), t.z(), now)) return;
                if (skipped(now)) return;
                MinecraftServer server = mc.getSingleplayerServer();
                server.execute(() -> {
                    try { place = place(server.getLevel(t.dimension()), t); }
                    catch (Throwable failure) { serverFailure = String.valueOf(failure); LOG.error("Lads biome capture place", failure); }
                });
                next(Phase.PLACE, now);
            }
            case PLACE -> {
                Place p = place;
                if (p == null) return;
                shot = 0;
                yaw = Float.NaN;
                look(mc, p, shotYaw(p));
                next(Phase.SETTLE, now);
            }
            case SETTLE -> {
                Target t = mine.get(index);
                Place p = place;
                if (turned && !loaded(mc, t.dimension(), (int) Math.floor(p.x()), (int) Math.floor(p.z()), now)) return;
                if (skipped(now)) return;
                turned = false;
                if (!single(t)) {
                    MinecraftServer server = mc.getSingleplayerServer();
                    int ticks = TICKS[shot];
                    server.execute(() -> command(server, "time set " + ticks));
                }
                next(Phase.SHOOT, now);
            }
            case SHOOT -> {
                if (now - since < 1_500_000_000L) return; // the sky and light follow the new time
                Target t = mine.get(index);
                Place p = place;
                String name = single(t) ? t.biome() : t.biome() + "_" + TIMES[shot];
                Path folder = Files.createDirectories(mc.gameDirectory.toPath().resolve("screenshots").resolve("biomes"));
                Path output = folder.resolve(name + ".png");
                var eye = mc.player.getEyePosition();
                String camera = mc.level.getBiome(BlockPos.containing(eye)).unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
                MANIFEST.append(String.format(Locale.ROOT, "%s,%s,%s,%.1f,%.1f,%.1f,%.1f,%.1f,%d,%s%n", output.getFileName(), t.biome(),
                    t.dimension().identifier().getPath(), eye.x, eye.y, eye.z, mc.player.getYRot(), mc.player.getXRot(), single(t) ? -1 : TICKS[shot], camera));
                mc.gui.toastManager().clear();
                hideHud(mc);
                next(Phase.SAVING, now);
                Screenshot.takeScreenshot(target, image -> {
                    try (image) { image.writeToFile(output); saved++; LOG.info("Lads biome frame {} (camera in {})", output.getFileName(), camera); }
                    catch (Exception failure) { failed++; LOG.error("Lads biome capture frame {} failed", output, failure); }
                    finally { Minecraft.getInstance().execute(BiomeCapture::shotDone); }
                });
            }
            case SAVING, DONE -> { }
        }
    }

    private static void shotDone() {
        Minecraft mc = Minecraft.getInstance();
        Target t = mine.get(index);
        if (single(t) || ++shot >= TIMES.length) { index++; next(Phase.GOTO, System.nanoTime()); return; }
        look(mc, place, shotYaw(place));
        next(Phase.SETTLE, System.nanoTime());
    }

    private static com.thelads.core.modules.CrosshairModule crosshair() { return com.thelads.core.v26_2.feature.crosshair.NativeCrosshair.module(); }

    private static void openSettings(Minecraft mc) {
        var settings = new com.thelads.core.v26_2.gui.LadsSettingsScreen26(mc.gui.screen());
        mc.setScreenAndShow(settings);
        settings.openModule("Crosshair Tweaks");
    }

    /** The UI pages: each background on the settings page, then the drawing editor with the live game and a Nether biome. */
    private static void uiSteps(Minecraft mc) {
        var m = crosshair();
        var ids = java.util.Arrays.asList(com.thelads.core.client.CrosshairBackground.biomeIds());
        UI_STEPS.add(new UiStep("ui-1-biome-meadow-sunset", () -> {
            m.background.setIndex(com.thelads.core.client.CrosshairBackground.BIOME);
            m.biome.setIndex(ids.indexOf("meadow"));
            m.time.setIndex(3);
            openSettings(mc);
        }));
        UI_STEPS.add(new UiStep("ui-2-gradient", () -> m.background.setIndex(com.thelads.core.client.CrosshairBackground.GRADIENT)));
        UI_STEPS.add(new UiStep("ui-3-solid", () -> m.background.setIndex(com.thelads.core.client.CrosshairBackground.SOLID)));
        UI_STEPS.add(new UiStep("ui-4-your-image", () -> {
            try {
                Path file = Files.createDirectories(mc.gameDirectory.toPath().resolve("screenshots")).resolve("qa-background-source.png");
                var image = new java.awt.image.BufferedImage(1600, 900, java.awt.image.BufferedImage.TYPE_INT_RGB);
                var g = image.createGraphics();
                for (int i = 0; i < 16; i++) { g.setColor(java.awt.Color.getHSBColor(i / 16f, .6f, .9f)); g.fillRect(i * 100, 0, 100, 900); }
                g.dispose();
                javax.imageio.ImageIO.write(image, "png", file.toFile());
                com.thelads.core.client.CrosshairBackground.use(file); // as the file dialog's pick; the next preview frame takes it
            } catch (Exception failure) { failed++; LOG.error("Lads biome capture: your-image source", failure); }
        }));
        UI_STEPS.add(new UiStep("ui-5-live", () -> {
            boolean kept = m.background.getIndex() == com.thelads.core.client.CrosshairBackground.IMAGE
                && com.thelads.core.client.CrosshairBackground.imageFile(m.image.getValue()).isFile();
            LOG.info("Lads biome capture: your image chosen and kept in the shared folder: {} ({})", kept, m.image.getValue());
            if (!kept) failed++;
            m.background.setIndex(com.thelads.core.client.CrosshairBackground.LIVE);
        }));
        UI_STEPS.add(new UiStep("ui-6-editor-live", () -> mc.setScreenAndShow(new com.thelads.core.v26_2.feature.crosshair.CrosshairDrawingScreen(mc.gui.screen()))));
        UI_STEPS.add(new UiStep("ui-7-editor-nether", () -> {
            m.background.setIndex(com.thelads.core.client.CrosshairBackground.BIOME);
            m.biome.setIndex(ids.indexOf("nether_wastes"));
        }));
    }

    private static void shoot(RenderTarget target, String name, Runnable then) {
        try {
            Path output = Files.createDirectories(Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots")).resolve(name + ".png");
            Screenshot.takeScreenshot(target, image -> {
                try (image) { image.writeToFile(output); saved++; LOG.info("Lads biome capture UI frame {}", output.getFileName()); }
                catch (Exception failure) { failed++; LOG.error("Lads biome capture UI frame {} failed", output, failure); }
                finally { Minecraft.getInstance().execute(then); }
            });
        } catch (Exception failure) { failed++; LOG.error("Lads biome capture UI frame {} failed", name, failure); Minecraft.getInstance().execute(then); }
    }

    private static boolean single(Target t) { return t.pocket() || t.dimension() != Level.OVERWORLD; }
    private static float shotYaw(Place p) { return EAST[shot] ? p.east() : p.west(); }

    /** Turns the camera (and puts it at the place); a turn re-waits for the newly visible sections. */
    private static void look(Minecraft mc, Place p, float to) {
        if (to == yaw) return;
        yaw = to;
        turned = true;
        renderedSince = 0;
        // Pockets look a little down: their floors (sculk, nylium, moss) say which biome it is.
        teleport(mc.getSingleplayerServer(), String.format(Locale.ROOT, "execute in %s run tp @a %.2f %.2f %.2f %.1f %.1f",
            mine.get(index).dimension().identifier(), p.x(), p.y(), p.z(), to, mine.get(index).pocket() ? 15f : PITCH));
    }

    /** The camera is in that dimension near x, z, its chunks stopped arriving and every section is built (at most 90 s). */
    private static boolean loaded(Minecraft mc, ResourceKey<Level> dimension, int x, int z, long now) {
        var screen = mc.gui.screen();
        if (screen instanceof net.minecraft.client.gui.screens.WinScreen || screen instanceof net.minecraft.client.gui.screens.DeathScreen) {
            LOG.warn("Lads biome capture: {} in the way of {}; closing it", screen.getClass().getSimpleName(), mine.get(index).biome());
            if (mc.player != null && mc.player.isDeadOrDying()) mc.player.respawn();
            screen.onClose();
        }
        if (mc.level == null || mc.player == null || mc.gui.screen() != null || mc.level.dimension() != dimension
            || Math.abs(mc.player.getX() - x) > 3 || Math.abs(mc.player.getZ() - z) > 3) {
            loadStable = renderedSince = 0;
            if (now - since > 150_000_000_000L) { skip = true; return true; }
            if (now - tpAt > 15_000_000_000L && mc.getSingleplayerServer() != null) {
                LOG.warn("Lads biome capture: camera not at {} after 15 s ({} at {}, screen {}); moving it again", mine.get(index).biome(),
                    mc.level == null ? "-" : mc.level.dimension().identifier(), mc.player == null ? "-" : mc.player.blockPosition().toShortString(), mc.gui.screen());
                teleport(mc.getSingleplayerServer(), tp);
            }
            return false;
        }
        int chunks = mc.level.getChunkSource().getLoadedChunksCount();
        if (chunks != lastChunks) { lastChunks = chunks; loadStable = now; }
        if (mc.levelRenderer.hasRenderedAllSections()) { if (renderedSince == 0) renderedSince = now; }
        else renderedSince = 0;
        boolean ready = now - since > 3_000_000_000L && now - loadStable > 2_000_000_000L && renderedSince != 0 && now - renderedSince > 1_000_000_000L;
        if (!ready && now - since > 90_000_000_000L) {
            LOG.warn("Lads biome capture: {} still loading after 90 s ({} chunks, sections built {}); shooting anyway", mine.get(index).biome(), chunks, renderedSince != 0);
            return true;
        }
        return ready;
    }

    private static void teleport(MinecraftServer server, String command) {
        tp = command;
        tpAt = System.nanoTime();
        server.execute(() -> command(server, command));
    }

    /** A target whose camera never got there in 150 s is left out (logged; the run then fails). */
    private static boolean skipped(long now) {
        if (!skip) return false;
        skip = false;
        failed++;
        LOG.error("Lads biome capture: {} skipped, the camera never got there", mine.get(index).biome());
        index++;
        next(Phase.GOTO, now);
        return true;
    }

    private static void hideHud(Minecraft mc) { if (!mc.gui.hud.isHidden()) mc.gui.hud.toggle(); }
    private static void next(Phase to, long now) { phase = to; since = now; }

    private static void finish(String result) {
        phase = Phase.DONE;
        if (!UI) try { Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots").resolve("biomes").resolve("manifest-" + SPEC.replace("/", "of") + ".csv"), MANIFEST, StandardCharsets.UTF_8); }
        catch (Exception failure) { LOG.warn("Lads biome capture manifest", failure); }
        if (result.equals("END") && failed == 0) LOG.info("Lads biome capture END: {} passed, 0 failed; {} biomes", saved, mine == null ? 0 : mine.size());
        else LOG.error("Lads biome capture FAILED: {} saved, {} failed; {}", saved, failed, result);
    }

    private static void command(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    // ---- server thread ----

    /** Every biome of the three dimensions with a spot: surface biomes (Overworld, End) from a grid of the generator's surface, the rest 3D. */
    private static List<Target> scan(MinecraftServer server) {
        long started = System.nanoTime();
        var registry = server.registryAccess().lookupOrThrow(Registries.BIOME);
        List<Target> result = new ArrayList<>();
        Map<ResourceKey<Level>, TreeSet<String>> possible = new TreeMap<>((a, b) -> a.identifier().compareTo(b.identifier()));
        for (var dimension : List.of(Level.OVERWORLD, Level.NETHER, Level.END)) {
            ServerLevel level = server.getLevel(dimension);
            var ids = new TreeSet<String>();
            for (Holder<Biome> biome : level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes())
                biome.unwrapKey().ifPresent(key -> ids.add(key.identifier().getPath()));
            possible.put(dimension, ids);
        }
        LOG.info("Lads biome capture: biomes per dimension {} (registry has {})", possible, registry.listElements().count());
        for (var dimension : List.of(Level.OVERWORLD, Level.END)) {
            ServerLevel level = server.getLevel(dimension);
            // Caves are photographed in their air (CrosshairBackground.timeless); the End's biomes from its islands' ground.
            var wanted = new TreeSet<String>();
            for (String id : possible.get(dimension)) if (dimension == Level.END || !com.thelads.core.client.CrosshairBackground.timeless(id)) wanted.add(id);
            var found = surface(level, wanted, SCAN_RADIUS, SCAN_STEP);
            var missing = new TreeSet<>(wanted);
            missing.removeAll(found.keySet());
            if (!missing.isEmpty()) {
                LOG.info("Lads biome capture: {} not within {} blocks, scanning twice as far", missing, SCAN_RADIUS);
                found.putAll(surface(level, missing, SCAN_RADIUS * 2, SCAN_STEP * 2));
            }
            result.addAll(found.values());
            for (String id : possible.get(dimension)) if (!wanted.contains(id)) result.add(pocket(level, id));
            for (String id : wanted) if (!found.containsKey(id)) LOG.warn("Lads biome capture: {} not found on the surface", id);
        }
        // The Nether's biomes do not change with height: its most interior cells come from the same grid (at the bedrock roof's
        // height), then the camera goes to the air pocket around the middle of the Nether's height there.
        ServerLevel nether = server.getLevel(Level.NETHER);
        var inside = surface(nether, possible.get(Level.NETHER), 1024, 32);
        for (String id : possible.get(Level.NETHER)) {
            Target t = inside.get(id);
            result.add(t == null ? pocket(nether, id) : new Target(id, Level.NETHER, t.x(), 64, t.z(), -90, -90, true));
        }
        result.removeIf(java.util.Objects::isNull);
        LOG.info("Lads biome capture: scan took {} ms, {} targets", (System.nanoTime() - started) / 1_000_000, result.size());
        return result;
    }

    /** Surface biome per grid cell; per biome its most interior cell (most of the 5x5 cells around it alike), nearest the origin on ties. */
    private static Map<String, Target> surface(ServerLevel level, java.util.Set<String> wanted, int radius, int step) {
        int n = radius * 2 / step + 1;
        String[][] grid = new String[n][n];
        int[][] height = new int[n][n];
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
            int x = -radius + i * step, z = -radius + j * step;
            height[i][j] = surfaceHeight(level, x, z);
            grid[i][j] = biome(level, x, height[i][j], z);
        }
        Map<String, int[]> best = new HashMap<>();
        for (int i = 2; i < n - 2; i++) for (int j = 2; j < n - 2; j++) {
            String id = grid[i][j];
            if (!wanted.contains(id)) continue;
            int alike = 0;
            for (int a = -2; a <= 2; a++) for (int b = -2; b <= 2; b++) if (id.equals(grid[i + a][j + b])) alike++;
            long distance = (long) (i - n / 2) * (i - n / 2) + (long) (j - n / 2) * (j - n / 2);
            int[] old = best.get(id);
            if (old == null || alike > old[2] || alike == old[2] && distance < old[3]) best.put(id, new int[] {i, j, alike, (int) Math.min(Integer.MAX_VALUE, distance)});
        }
        Map<String, Target> result = new TreeMap<>();
        for (var entry : best.entrySet()) {
            int[] cell = entry.getValue();
            int x = -radius + cell[0] * step, z = -radius + cell[1] * step, y = height[cell[0]][cell[1]];
            float east = view(level, entry.getKey(), x, y + 3, z, -90), west = view(level, entry.getKey(), x, y + 3, z, 90);
            LOG.info("Lads biome capture: {} {} at {} {} {} ({} of 25 cells alike), views east {} west {}", level.dimension().identifier().getPath(),
                entry.getKey(), x, y, z, cell[2], east, west);
            result.put(entry.getKey(), new Target(entry.getKey(), level.dimension(), x, y, z, east, west, false));
        }
        return result;
    }

    /** Of the yaws within 67.5 degrees of {@code centre}, the one whose next 160 blocks stay in the biome and rise least above the eye. */
    private static float view(ServerLevel level, String biome, int x, int eye, int z, float centre) {
        float bestYaw = centre;
        double bestScore = -1e9;
        for (int k = -3; k <= 3; k++) {
            float yaw = centre + k * 22.5f;
            double dx = -Math.sin(Math.toRadians(yaw)), dz = Math.cos(Math.toRadians(yaw));
            int alike = 0;
            double rise = 0;
            for (int d = 16; d <= 160; d += 16) {
                int sx = (int) Math.round(x + dx * d), sz = (int) Math.round(z + dz * d), h = surfaceHeight(level, sx, sz);
                if (biome.equals(biome(level, sx, h, sz))) alike++;
                rise = Math.max(rise, (h - eye) / (double) d);
            }
            double score = alike / 10.0 - 3 * Math.max(0, rise) - Math.abs(k) * 0.01;
            if (score > bestScore) { bestScore = score; bestYaw = yaw; }
        }
        return bestYaw;
    }

    private static int surfaceHeight(ServerLevel level, int x, int z) {
        var source = level.getChunkSource();
        return source.getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, source.randomState());
    }

    private static String biome(ServerLevel level, int x, int y, int z) {
        return level.getUncachedNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z)).unwrapKey()
            .map(key -> key.identifier().getPath()).orElse("?");
    }

    /** A biome off the surface (caves, the Nether): the nearest place the generator puts it, its air pocket found once loaded. */
    private static Target pocket(ServerLevel level, String id) {
        var found = level.findClosestBiome3d(holder -> holder.unwrapKey().map(key -> key.identifier().getPath().equals(id)).orElse(false),
            new BlockPos(0, level.dimension() == Level.NETHER ? 64 : 0, 0), 6400, 32, 16);
        if (found == null) { LOG.warn("Lads biome capture: {} not found within 6400 blocks", id); return null; }
        // The nearest point is the biome's edge: move to the point around it with the most of the biome near it.
        BlockPos p = found.getFirst(), centre = p;
        int bestAlike = -1;
        for (int dx = -48; dx <= 48; dx += 16) for (int dz = -48; dz <= 48; dz += 16) for (int dy = -16; dy <= 16; dy += 8) {
            BlockPos q = p.offset(dx, dy, dz);
            int alike = 0;
            for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) for (int c = -1; c <= 1; c++)
                if (id.equals(biome(level, q.getX() + a * 8, q.getY() + b * 8, q.getZ() + c * 8))) alike++;
            if (alike > bestAlike) { bestAlike = alike; centre = q; }
        }
        p = centre;
        if (id.equals("deep_dark")) {
            // Its open halls with sculk are in its ancient cities (floors at y -51); the biome's nearest point is mostly solid rock.
            BlockPos city = level.findNearestMapStructure(net.minecraft.tags.StructureTags.ON_ANCIENT_CITY_MAPS, BlockPos.ZERO, 100, false);
            if (city != null) p = new BlockPos(city.getX(), -40, city.getZ());
            LOG.info("Lads biome capture: deep_dark ancient city {}", city == null ? "not found" : city.toShortString());
        }
        LOG.info("Lads biome capture: {} {} at {} (pocket, {} of 27 alike)", level.dimension().identifier().getPath(), id, p.toShortString(), bestAlike);
        return new Target(id, level.dimension(), p.getX(), p.getY(), p.getZ(), -90, -90, true);
    }

    /** Once the chunks are loaded: surface spots go just above every block near them; pockets to the air with the longest view in the biome. */
    private static Place place(ServerLevel level, Target t) {
        if (!t.pocket()) {
            int top = level.getMinY();
            for (int a = -2; a <= 2; a++) for (int b = -2; b <= 2; b++) top = Math.max(top, level.getHeight(Heightmap.Types.WORLD_SURFACE, t.x() + a, t.z() + b));
            if (top <= level.getMinY()) top = 64; // void (End islands)
            // Now that the blocks (trees included) exist: per half the yaw and height whose view stays in the biome and open.
            double[] east = look(level, t, top, -90), west = look(level, t, top, 90);
            double feet = Math.max(east[1], west[1]);
            LOG.info("Lads biome capture: {} camera at {} above ground {} (view east {} / west {})", t.biome(), feet, top, east, west);
            return new Place(t.x() + 0.5, feet, t.z() + 0.5, (float) east[0], (float) west[0]);
        }
        boolean nether = level.dimension() == Level.NETHER;
        int reach = 32, step = nether ? 4 : 3, below = nether ? 30 : 20, above = nether ? 40 : 20;
        double bestScore = -1;
        Place best = null;
        var pos = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach; dx += step) for (int dz = -reach; dz <= reach; dz += step) for (int dy = -below; dy <= above; dy++) {
            int x = t.x() + dx, y = t.y() + dy, z = t.z() + dz;
            if (y <= level.getMinY() + 1 || y >= level.getMinY() + level.getHeight() - 4) continue; // never in the void under bedrock
            if (!air(level, pos.set(x, y, z)) || !air(level, pos.set(x, y + 1, z)) || !air(level, pos.set(x, y + 2, z))) continue;
            if (!t.biome().equals(level.getBiome(pos.set(x, y, z)).unwrapKey().map(k -> k.identifier().getPath()).orElse(""))) continue;
            // Standing on the biome's ground (nylium, soul soil, moss, sculk): mid-air views over a Nether void show no biome.
            var ground = level.getBlockState(pos.set(x, y - 1, z));
            if (!ground.isSolid() || !ground.getFluidState().isEmpty() || ground.is(net.minecraft.world.level.block.Blocks.GLOWSTONE)
                || ground.is(net.minecraft.world.level.block.Blocks.SHROOMLIGHT)) continue; // not on a lamp hanging from the ceiling
            for (int k = 0; k < 8; k++) {
                float yaw = k * 45 - 180;
                // The open air ahead that is still the biome (a long view out of it shows another biome), straight ahead and 25
                // degrees to each side: a wall beside the camera fills half the picture.
                int alike = 0;
                for (int side = -1; side <= 1; side++) {
                    double vx = -Math.sin(Math.toRadians(yaw + side * 25)), vz = Math.cos(Math.toRadians(yaw + side * 25));
                    for (int free = 1; free <= 24 && air(level, pos.set((int) Math.floor(x + 0.5 + vx * free), y + 1, (int) Math.floor(z + 0.5 + vz * free))); free++)
                        if (t.biome().equals(level.getBiome(pos).unwrapKey().map(key -> key.identifier().getPath()).orElse(""))) alike++;
                }
                double score = alike - 0.001 * (dx * dx + dy * dy + dz * dz); // ties: the one nearest the biome's middle
                if (score > bestScore) { bestScore = score; best = new Place(x + 0.5, y, z + 0.5, yaw, yaw); }
            }
        }
        if (best == null) {
            LOG.warn("Lads biome capture: no air pocket in {} near {} {} {}", t.biome(), t.x(), t.y(), t.z());
            return new Place(t.x() + 0.5, t.y(), t.z() + 0.5, -90, -90);
        }
        LOG.info("Lads biome capture: {} pocket {} {} {} yaw {} (view {})", t.biome(), best.x(), best.y(), best.z(), best.east(), bestScore);
        return best;
    }

    /**
     * {yaw, feet, score} for the half around {@code centre}: of 7 yaws 22.5 degrees apart and feet 0.2, 4 or 10 blocks above the
     * highest block near the spot, the one whose 128 blocks ahead stay most in the biome and rise least into the view.
     */
    private static double[] look(ServerLevel level, Target t, int top, float centre) {
        double[] best = {centre, top + 0.2, -1e9};
        for (double lift : new double[] {0.2, 4, 10}) for (int k = -3; k <= 3; k++) {
            float yaw = centre + k * 22.5f;
            double dx = -Math.sin(Math.toRadians(yaw)), dz = Math.cos(Math.toRadians(yaw)), eye = top + lift + 1.62, slope = -1;
            int alike = 0, samples = 0;
            for (int d = 3; d <= 128; d += 3) {
                int x = (int) Math.floor(t.x() + 0.5 + dx * d), z = (int) Math.floor(t.z() + 0.5 + dz * d);
                int h = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                slope = Math.max(slope, (h - eye) / d);
                samples++;
                if (t.biome().equals(level.getBiome(new BlockPos(x, Math.max(h - 1, level.getMinY()), z)).unwrapKey().map(key -> key.identifier().getPath()).orElse(""))) alike++;
            }
            double score = alike / (double) samples - 3 * Math.max(0, slope) - lift * 0.015 - Math.abs(k) * 0.01;
            if (score > best[2]) best = new double[] {yaw, top + lift, score};
        }
        return best;
    }

    /** Air or anything see-through enough to stand in (no fluids: a lava or water view is not a pocket). */
    private static boolean air(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.isAir() || !state.isSolid() && state.getFluidState().isEmpty();
    }
}
