package com.thelads.core.v26_2.feature;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QA only (LADS_QA_SHADERTOGGLE="step,step,..."; the Verification harness passes the environment on): drives the shader toggle through
 * the real screens, one step at a time, logging "Lads qa shaders: ..." and saving screenshots/shaders-&lt;LADS_QA_SHADERTOGGLE_TAG&gt;-*.png.
 * <ul>
 * <li>{@code world}: from the title screen, opens (first run: creates) the world LadsQA-shaders; {@code ready}: waits for a world that
 *     is already loading (after the launcher's relaunch put the game back into it).</li>
 * <li>{@code video}: Sodium's video settings; {@code page}: clicks its "Shader packs..." entry (else logs that it is missing and opens the
 *     confirm screen directly); {@code restart}: presses the open screen's restart button (the game then writes its request files and quits).</li>
 * <li>{@code iris}: logs whether Iris is loaded; {@code packscreen}: opens Iris's shader pack screen with no pack and closes it, which must
 *     bring up the "Shaders are off" warning; {@code warn}: waits for a toggle screen; {@code quit}: stops the game.</li>
 * </ul>
 */
public final class ShaderToggleQa26 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final String PLAN = System.getenv("LADS_QA_SHADERTOGGLE"), TAG = System.getenv().getOrDefault("LADS_QA_SHADERTOGGLE_TAG", "x");
    private static final String TOGGLE = "com.thelads.core.v26_2.gui.ShaderToggleScreen26", SAVE = "LadsQA-shaders";
    private static String[] steps;
    private static int index, wait, held;
    private static boolean started;

    private ShaderToggleQa26() {}

    public static void register() {
        if (PLAN == null || PLAN.isBlank()) return;
        steps = PLAN.split(",");
        ClientTickEvents.END_CLIENT_TICK.register(ShaderToggleQa26::tick);
    }

    private static void tick(Minecraft mc) {
        if (index >= steps.length || --wait > 0) return;
        try {
            String step = steps[index].trim();
            if (run(mc, step)) {
                LOGGER.info("Lads qa shaders: step {} done ({})", index + 1, step);
                index++;
                held = 0;
                wait = 20;
            }
        } catch (Exception e) {
            LOGGER.error("Lads qa shaders FAILED at step {}: {}", index + 1, steps[index], e);
            index = steps.length;
        }
    }

    /** True when the step is finished; false keeps it running on the next tick. */
    private static boolean run(Minecraft mc, String step) throws Exception {
        Screen screen = mc.gui.screen();
        String[] parts = step.split(" ");
        switch (parts[0]) {
            case "dump" -> { dump(screen, 0); return true; }
            case "shot" -> { return shot(mc, parts[1]); }
            case "scroll" -> { // "scroll <x> <y> <ticks>": the wheel turned down over GUI position x, y
                screen.mouseScrolled(Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), 0.0, -8.0);
                return ++held >= Integer.parseInt(parts[3]);
            }
            case "click" -> { // "click <x> <y>": a left click at GUI position x, y
                screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                    new net.minecraft.client.input.MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                return true;
            }
            default -> { }
        }
        switch (step) {
            case "world" -> {
                if (!started) {
                    if (!(screen instanceof TitleScreen) || mc.gui.overlay() != null || !mc.isGameLoadFinished() || ++held < 100) return false;
                    started = true;
                    Path saves = mc.gameDirectory.toPath().resolve("saves");
                    Files.createDirectories(saves);
                    if (Files.isRegularFile(saves.resolve(SAVE).resolve("level.dat"))) {
                        LOGGER.info("Lads qa shaders: opening {}", SAVE);
                        mc.createWorldOpenFlows().openWorld(SAVE, () -> LOGGER.error("Lads qa shaders FAILED: world open cancelled"));
                    } else {
                        LOGGER.info("Lads qa shaders: creating {}", SAVE);
                        var settings = new net.minecraft.world.level.LevelSettings("Lads shader QA", net.minecraft.world.level.GameType.CREATIVE,
                            new net.minecraft.world.level.LevelSettings.DifficultySettings(net.minecraft.world.Difficulty.PEACEFUL, false, true), true,
                            net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
                        mc.createWorldOpenFlows().createFreshLevel(SAVE, settings, new net.minecraft.world.level.levelgen.WorldOptions(135L, false, false),
                            registries -> registries.lookupOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                                .getOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(), screen);
                    }
                    return false;
                }
                return worldReady(mc);
            }
            case "ready" -> { return worldReady(mc); }
            case "video" -> {
                if (held == 0) mc.gui.setScreen((Screen) Class.forName("net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen")
                    .getMethod("createScreen", Screen.class).invoke(null, (Object) null));
                if (++held < 60) return false;
                LOGGER.info("Lads qa shaders: video settings screen is {}", mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getName());
                return shot(mc, "video");
            }
            case "page" -> {
                if (++held < 30) return false;
                // Reese's screen: the overlay button bottom left, clicked through the screen's mouse event as Fabric dispatches it.
                boolean taken = !net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(screen).invoker().allowMouseClick(screen,
                    new net.minecraft.client.input.MouseButtonEvent(16, screen.height - 16, new net.minecraft.client.input.MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)));
                AbstractWidget entry = taken ? null : find(screen, "Shader packs...");
                if (taken) {
                    LOGGER.info("Lads qa shaders: the 'Shader packs...' button bottom left of {} took the click", screen.getClass().getName());
                } else if (entry != null) {
                    LOGGER.info("Lads qa shaders: clicking '{}' at {},{} on {}", entry.getMessage().getString(), entry.getX(), entry.getY(), screen.getClass().getName());
                    screen.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(entry.getX() + entry.getWidth() / 2.0, entry.getY() + entry.getHeight() / 2.0,
                        new net.minecraft.client.input.MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)), false);
                } else {
                    LOGGER.warn("Lads qa shaders: no 'Shader packs...' entry in {}; opening the confirm screen directly", screen == null ? "no screen" : screen.getClass().getName());
                    mc.gui.setScreen((Screen) Class.forName(TOGGLE).getConstructor(Screen.class, boolean.class).newInstance(screen, true));
                }
                held = 0;
                return true;
            }
            case "restart" -> {
                if (screen == null || !screen.getClass().getName().equals(TOGGLE)) return ++held > 400 ? fail("no toggle screen to restart from") : false;
                if (++held == 40) shot(mc, "toggle");
                if (held < 60) return false;
                Method restart = screen.getClass().getDeclaredMethod("restart");
                restart.setAccessible(true);
                LOGGER.info("Lads qa shaders: pressing restart on '{}'", screen.getTitle().getString());
                restart.invoke(screen);
                return true;
            }
            case "iris" -> {
                LOGGER.info("Lads qa shaders: iris loaded={}, tag {}", FabricLoader.getInstance().isModLoaded("iris"), TAG);
                return true;
            }
            case "packscreen" -> {
                if (held == 0) {
                    mc.gui.setScreen((Screen) Class.forName("net.irisshaders.iris.gui.screen.ShaderPackScreen").getConstructor(Screen.class).newInstance((Object) null));
                } else if (held == 40) {
                    shot(mc, "packscreen");
                    mc.gui.setScreen(null); // closed with no pack: the warning must follow
                    held = 0;
                    return true;
                }
                held++;
                return false;
            }
            case "warn" -> {
                if (screen == null || !screen.getClass().getName().equals(TOGGLE)) return ++held > 200 ? fail("the shaders-off warning never appeared") : false;
                if (++held < 40) return false;
                shot(mc, "warning");
                return true;
            }
            case "quit" -> {
                mc.stop();
                return true;
            }
            default -> throw new IllegalArgumentException("unknown step " + step);
        }
    }

    private static void dump(GuiEventListener root, int depth) {
        if (depth > 7 || root == null) return;
        LOGGER.info("Lads qa shaders: widget {}{} {}", " ".repeat(depth), root.getClass().getName(), root instanceof AbstractWidget w ? "'" + w.getMessage().getString() + "'" : "");
        if (root instanceof ContainerEventHandler container) for (GuiEventListener child : container.children()) dump(child, depth + 1);
    }

    private static boolean worldReady(Minecraft mc) {
        return mc.level != null && mc.player != null && mc.gui.screen() == null && mc.gui.overlay() == null && mc.player.tickCount >= 100 && !mc.isPaused();
    }

    private static boolean fail(String message) {
        throw new IllegalStateException(message);
    }

    /** The first widget (any depth) of {@code root} whose message is {@code text}. */
    private static AbstractWidget find(GuiEventListener root, String text) {
        if (root instanceof AbstractWidget widget && widget.getMessage().getString().equals(text)) return widget;
        if (root instanceof ContainerEventHandler container)
            for (GuiEventListener child : container.children()) {
                AbstractWidget found = find(child, text);
                if (found != null) return found;
            }
        return null;
    }

    /** The last finished frame (the screen has been up for a while, so it is on it). */
    private static boolean shot(Minecraft mc, String name) throws Exception {
        Path output = mc.gameDirectory.toPath().resolve("screenshots").resolve("shaders-" + TAG + "-" + name + ".png");
        Files.createDirectories(output.getParent());
        net.minecraft.client.Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
            try { image.writeToFile(output); LOGGER.info("Lads qa shaders: shot {}", output.getFileName()); }
            catch (Exception failure) { LOGGER.error("Lads qa shaders FAILED: {}", output, failure); }
            finally { image.close(); }
        });
        return true;
    }
}
