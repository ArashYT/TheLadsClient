package com.thelads.core.v26_2.gui;

import com.thelads.core.client.ShaderToggle;
import com.thelads.core.client.gui.LadsPalette;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Shaders on demand (common ShaderToggle). With Iris suspended for Vulkan, Sodium's video settings get a "Shader packs..." page
 * where Iris's own sits (Iris 1.11.7 registers its "Shader Packs" page through Sodium's config API, its only entry with Sodium);
 * it asks to restart into OpenGL with Iris. With Iris loaded, closing its shader pack screen (or a toggle) with no pack in use
 * warns once that OpenGL is still costing FPS. Iris and Sodium are reached by reflection only.
 */
public class ShaderToggleScreen26 extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final Path DIR = FabricLoader.getInstance().getGameDir();
    private static final String SODIUM_API = "net.caffeinemc.mods.sodium.api.config.";
    private static final int CARD_HEIGHT = 170;
    private static Method irisInstance, irisPackInUse;

    private final Screen parent;
    private final ShaderToggle.Mode mode;
    private final boolean on;
    private final boolean launcher = ShaderToggle.launcherStarted(DIR);
    private final String world, server, destination;
    private String error = null;
    private int cardX, cardY, cardWidth;

    public ShaderToggleScreen26(Screen parent, boolean on) {
        this(parent, on ? ShaderToggle.Mode.TURN_ON : ShaderToggle.Mode.WARN_OFF);
    }

    public ShaderToggleScreen26(Screen parent, ShaderToggle.Mode mode) {
        super(Component.literal(ShaderToggle.title(mode)));
        this.parent = parent;
        this.mode = mode;
        this.on = mode.on();
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer local = mc.getSingleplayerServer();
        ServerData remote = mc.getCurrentServer();
        if (local != null) {
            world = local.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName().toString();
            server = null;
            destination = "\"" + local.getWorldData().getLevelName() + "\"";
        } else if (remote != null && !remote.isRealm()) {
            world = null;
            server = remote.ip;
            destination = "\"" + remote.name + "\"";
        } else {
            world = null;
            server = null;
            destination = "the title screen";
        }
    }

    public static void register() {
        if (FabricLoader.getInstance().isModLoaded("iris")) watchIris();
        else if (ShaderToggle.irisSuspended(DIR)) {
            addSodiumPage();
            addReesesButton();
        }
    }

    /**
     * Reese's Sodium Options lists a mod with a single page only as a header ("Shaders / Off") far down its tab list, which is easy to
     * miss: it also gets a "Shader packs..." button bottom left, clear of everything else, drawn and clicked through Fabric's screen
     * events (the same as on 1.20.1) so it never touches Reese's widgets.
     */
    private static void addReesesButton() {
        if (!FabricLoader.getInstance().isModLoaded("reeses-sodium-options")) return;
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!screen.getClass().getName().equals("me.flashyreese.mods.reeses_sodium_options.client.gui.SodiumVideoOptionsScreen")) return;
            String label = "Shader packs...";
            Button b = Button.builder(Component.literal(label), press -> client.setScreenAndShow(new ShaderToggleScreen26(screen, true)))
                .bounds(6, screen.height - 26, client.font.width(label) + 20, 20).build();
            ScreenEvents.afterExtract(screen).register((s, graphics, mouseX, mouseY, delta) -> b.extractRenderState(graphics, mouseX, mouseY, delta));
            ScreenMouseEvents.allowMouseClick(screen).register((s, click) -> !b.mouseClicked(click, false));
        });
    }

    @Override
    protected void init() {
        cardWidth = Math.min(380, width - 20);
        cardX = (width - cardWidth) / 2;
        cardY = (height - CARD_HEIGHT) / 2;
        addRenderableWidget(Button.builder(Component.literal(ShaderToggle.restartLabel(mode, true, launcher)), b -> restart())
            .bounds(cardX + 16, cardY + CARD_HEIGHT - 62, cardWidth - 32, 22).build());
        addRenderableWidget(Button.builder(Component.literal(ShaderToggle.cancelLabel(mode, true)), b -> onClose())
            .bounds(cardX + 16, cardY + CARD_HEIGHT - 34, cardWidth - 32, 22).build());
    }

    private void restart() {
        // Written before quitting: the launcher starts the game again (same profile or Experience) once it has closed.
        if (!ShaderToggle.choose(DIR, !on) || !ShaderToggle.requestRelaunch(DIR, on, world, server)) {
            error = "Could not save the request for the launcher.";
            return;
        }
        LOGGER.info("Lads shaders: quitting to restart on {} (world {}, server {})", on ? "OpenGL with Iris" : "Vulkan", world, server);
        if (minecraft == null) return;
        // Save and Quit's own path (the world is saved, FastQuit's background save is awaited before the game closes).
        if (minecraft.level != null) minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
        minecraft.stop();
    }

    private String body() {
        return ShaderToggle.body(mode, true, launcher, destination);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xAA000000);
        g.fill(cardX, cardY, cardX + cardWidth, cardY + CARD_HEIGHT, LadsPalette.CARD);
        g.fill(cardX + 4, cardY, cardX + cardWidth - 4, cardY + (on ? 2 : 4), LadsPalette.ACCENT);
        g.text(font, title.getString(), cardX + 18, cardY + 16, on ? LadsPalette.TEXT : LadsPalette.ACCENT, false);
        int next = g.textWithWordWrap(font, Component.literal(body()), cardX + 18, cardY + 38, cardWidth - 36, LadsPalette.TEXT, false);
        if (error != null) g.text(font, error, cardX + 18, next + 6, LadsPalette.ACCENT, false);
        super.extractRenderState(g, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        // Keeping OpenGL (Esc too) is for this session only: the next launch is Vulkan again.
        if (mode == ShaderToggle.Mode.WARN_OFF) ShaderToggle.choose(DIR, true);
        if (minecraft != null) minecraft.setScreenAndShow(parent);
    }

    /** Sodium's ConfigManager.registerConfigEntryPoint(Supplier, modId), fed a proxy ConfigEntryPoint; Sodium collects it at postInit. */
    private static void addSodiumPage() {
        try {
            Class<?> entry = Class.forName(SODIUM_API + "ConfigEntryPoint");
            Object user = Proxy.newProxyInstance(entry.getClassLoader(), new Class<?>[]{entry}, (proxy, method, args) -> switch (method.getName()) {
                case "registerConfigLate" -> { addPage(args[0]); yield null; }
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> "Lads shader packs page";
                default -> null;
            });
            Class.forName("net.caffeinemc.mods.sodium.client.config.ConfigManager")
                .getMethod("registerConfigEntryPoint", Supplier.class, String.class).invoke(null, (Supplier<Object>) () -> user, "theladscore");
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.warn("Lads shaders: no Shader packs page in the video settings ({})", e.toString());
        }
    }

    private static void addPage(Object config) {
        try {
            // The page first: options without a page would fail Sodium's whole config build.
            Object page = sodium(config, "ConfigBuilder", "createExternalPage");
            sodium(page, "ExternalPageBuilder", "setName", Component.literal("Shader packs..."));
            sodium(page, "ExternalPageBuilder", "setScreenConsumer",
                (Consumer<Screen>) parent -> Minecraft.getInstance().setScreenAndShow(new ShaderToggleScreen26(parent, true)));
            sodium(sodium(config, "ConfigBuilder", "registerModOptions", "theladscore", "Shaders", "Off"), "ModOptionsBuilder", "addPage", page);
        } catch (Exception e) {
            LOGGER.warn("Lads shaders: could not add the Shader packs page ({})", e.toString());
        }
    }

    private static Object sodium(Object target, String api, String name, Object... args) throws ReflectiveOperationException {
        for (Method m : Class.forName(SODIUM_API + "structure." + api).getMethods())
            if (m.getName().equals(name) && m.getParameterCount() == args.length) return m.invoke(target, args);
        throw new NoSuchMethodException(api + "." + name);
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
            if (irisPackInUse == null || !client.isGameLoadFinished() || client.gui.overlay() != null) return;
            boolean inUse;
            try {
                inUse = (Boolean) irisPackInUse.invoke(irisInstance.invoke(null));
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOGGER.warn("Lads shaders: Iris API failed, no shaders-off warning ({})", e.toString());
                irisPackInUse = null;
                return;
            }
            Screen screen = client.gui.screen();
            switch (watcher.update(screen != null && screen.getClass().getName().equals("net.irisshaders.iris.gui.screen.ShaderPackScreen"), inUse)) {
                case OFF -> client.setScreenAndShow(new ShaderToggleScreen26(screen, false));
                case ON -> ShaderToggle.choose(DIR, false);
                default -> {}
            }
        });
    }
}
