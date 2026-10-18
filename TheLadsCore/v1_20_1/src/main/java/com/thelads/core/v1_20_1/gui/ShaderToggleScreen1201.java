package com.thelads.core.v1_20_1.gui;

import com.thelads.core.client.ShaderToggle;
import com.thelads.core.client.gui.LadsPalette;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Shaders on demand on 1.20.1 (common ShaderToggle; no Vulkan here, only whether Iris is loaded). While the launcher keeps Iris
 * suspended, the video settings (Sodium's, Reese's Sodium Options' or vanilla's) get a "Shader packs..." button that asks to
 * restart with shaders. With Iris loaded the same place offers "Turn shaders off (restart)", and closing Iris's shader pack screen
 * with no pack in use warns once that Iris is still loaded. Iris and the options screens are reached by name and reflection only.
 */
public class ShaderToggleScreen1201 extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final Path DIR = FabricLoader.getInstance().getGameDir();
    /** Sodium 0.5's and Reese's Sodium Options' replacements of vanilla's video settings. */
    private static final Set<String> VIDEO_SCREENS = Set.of("me.jellysquid.mods.sodium.client.gui.SodiumOptionsGUI",
        "me.flashyreese.mods.reeses_sodium_options.client.gui.SodiumVideoOptionsScreen");
    private static final int CARD_HEIGHT = 170;
    private static Method irisInstance, irisPackInUse;
    private static final Map<Screen, Button> BUTTONS = new WeakHashMap<>();

    private final Screen parent;
    private final ShaderToggle.Mode mode;
    private final boolean launcher = ShaderToggle.launcherStarted(DIR);
    private final String world, server, destination;
    private String error = null;
    private int cardX, cardY, cardWidth;

    public ShaderToggleScreen1201(Screen parent, ShaderToggle.Mode mode) {
        super(Component.literal(ShaderToggle.title(mode)));
        this.parent = parent;
        this.mode = mode;
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer local = mc.getSingleplayerServer();
        ServerData remote = mc.getCurrentServer();
        if (local != null) {
            world = local.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
            server = null;
            destination = "\"" + local.getWorldData().getLevelName() + "\"";
        } else if (remote != null && !remote.isLan()) { // a LAN game has no fixed address to return to (and a Realm looks like any server here)
            world = null;
            server = remote.ip;
            destination = "\"" + remote.name + "\"";
        } else {
            world = null;
            server = null;
            destination = "the title screen";
        }
    }

    /** Needs Iris suspended (the launcher's state file) to offer shaders, or Iris loaded to offer to turn them off. */
    public static void register() {
        boolean loaded = FabricLoader.getInstance().isModLoaded("iris");
        if (loaded) watchIris();
        if (!loaded && !ShaderToggle.irisSuspended(DIR) || loaded && !ShaderToggle.launcherStarted(DIR)) return;
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof VideoSettingsScreen || VIDEO_SCREENS.contains(screen.getClass().getName())) addButton(screen, !loaded);
        });
    }

    /**
     * Drawn and clicked through Fabric's screen events instead of being a child of the screen: Reese's Sodium Options draws its own
     * widgets and never renders vanilla ones. Bottom left: clear of the donate and search row, the option pages and the Apply/Done buttons in all three screens.
     */
    private static void addButton(Screen screen, boolean on) {
        Minecraft mc = Minecraft.getInstance();
        String label = on ? "Shader packs..." : "Turn shaders off (restart)";
        int w = mc.font.width(label) + 20;
        Button b = Button.builder(Component.literal(label), press -> mc.setScreen(
            new ShaderToggleScreen1201(screen, on ? ShaderToggle.Mode.TURN_ON : ShaderToggle.Mode.TURN_OFF))).bounds(6, screen.height - 26, w, 20).build();
        if (BUTTONS.size() > 4) BUTTONS.clear(); // the button holds its screen, so a weak key alone would never go: only the latest few matter
        BUTTONS.put(screen, b);
        ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, delta) -> b.render(graphics, mouseX, mouseY, delta));
        ScreenMouseEvents.allowMouseClick(screen).register((s, mouseX, mouseY, mouseButton) -> !b.mouseClicked(mouseX, mouseY, mouseButton));
    }

    /** QA (the Verification script's {@code press}): the button above is not a child of its screen, so press finds it here and clicks it through the screen's mouse event. */
    public static boolean pressForQa(Screen screen, String label) {
        Button b = BUTTONS.get(screen);
        if (b == null || !b.getMessage().getString().toLowerCase(Locale.ROOT).equals(label.toLowerCase(Locale.ROOT))) return false;
        boolean handled = !ScreenMouseEvents.allowMouseClick(screen).invoker().allowMouseClick(screen, b.getX() + b.getWidth() / 2.0, b.getY() + b.getHeight() / 2.0, 0);
        LOGGER.info("Lads shaders: QA clicked '{}' on {}: {}", label, screen.getClass().getName(), handled ? "taken by the button" : "NOT taken");
        return handled;
    }

    @Override
    protected void init() {
        cardWidth = Math.min(380, width - 20);
        cardX = (width - cardWidth) / 2;
        cardY = (height - CARD_HEIGHT) / 2;
        addRenderableWidget(Button.builder(Component.literal(ShaderToggle.restartLabel(mode, false, launcher)), b -> restart())
            .bounds(cardX + 16, cardY + CARD_HEIGHT - 62, cardWidth - 32, 22).build());
        addRenderableWidget(Button.builder(Component.literal(ShaderToggle.cancelLabel(mode, false)), b -> onClose())
            .bounds(cardX + 16, cardY + CARD_HEIGHT - 34, cardWidth - 32, 22).build());
    }

    private void restart() {
        boolean on = mode.on();
        // Written before quitting: the launcher starts the game again (same profile) once it has closed.
        if (!ShaderToggle.choose(DIR, !on) || !ShaderToggle.requestRelaunch(DIR, on, world, server)) {
            error = "Could not save the request for the launcher.";
            return;
        }
        LOGGER.info("Lads shaders: quitting to restart {} Iris (world {}, server {})", on ? "with" : "without", world, server);
        // Like Switch Versions: stop() leaves the world through the normal shutdown (FastQuit waits for the save once the game is stopping).
        if (minecraft != null) minecraft.stop();
    }

    @Override
    public void renderBackground(GuiGraphics g) {}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xAA000000);
        g.fill(cardX, cardY, cardX + cardWidth, cardY + CARD_HEIGHT, LadsPalette.CARD);
        boolean on = mode.on();
        g.fill(cardX + 4, cardY, cardX + cardWidth - 4, cardY + (on ? 2 : 4), LadsPalette.ACCENT);
        g.drawString(font, title.getString(), cardX + 18, cardY + 16, on ? LadsPalette.TEXT : LadsPalette.ACCENT, false);
        List<FormattedCharSequence> lines = font.split(Component.literal(ShaderToggle.body(mode, false, launcher, destination)), cardWidth - 36);
        int y = cardY + 38;
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, cardX + 18, y, LadsPalette.TEXT, false);
            y += 10;
        }
        if (error != null) g.drawString(font, error, cardX + 18, y + 6, LadsPalette.ACCENT, false);
        super.render(g, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        // Keeping Iris (Esc too) is for this session only: the next launch has shaders off again.
        if (mode == ShaderToggle.Mode.WARN_OFF) ShaderToggle.choose(DIR, true);
        if (minecraft != null) minecraft.setScreen(parent);
    }

    private static void watchIris() {
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            irisInstance = api.getMethod("getInstance");
            irisPackInUse = api.getMethod("isShaderPackInUse");
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.warn("Lads shaders: Iris API missing, no shaders-off warning ({})", e.toString());
            return;
        }
        ShaderToggle.Watcher watcher = new ShaderToggle.Watcher();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (irisPackInUse == null || client.getOverlay() != null) return;
            boolean inUse;
            try {
                inUse = (Boolean) irisPackInUse.invoke(irisInstance.invoke(null));
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOGGER.warn("Lads shaders: Iris API failed, no shaders-off warning ({})", e.toString());
                irisPackInUse = null;
                return;
            }
            Screen screen = client.screen;
            switch (watcher.update(screen != null && screen.getClass().getName().equals("net.irisshaders.iris.gui.screen.ShaderPackScreen"), inUse)) {
                case OFF -> client.setScreen(new ShaderToggleScreen1201(screen, ShaderToggle.Mode.WARN_OFF));
                case ON -> ShaderToggle.choose(DIR, false);
                default -> {}
            }
        });
    }
}
