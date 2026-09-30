package com.thelads.core.v1_8_9.feature;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.gui.LadsSettingsScreen;
import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.ModuleSupport;
import com.thelads.core.mods.CoreCatalogExporter;
import com.thelads.core.shared.SharedContentPaths;
import com.thelads.core.v1_8_9.adapter.VanillaGameBridge189;
import com.thelads.core.v1_8_9.gui.LadsPauseButton;
import com.thelads.core.v1_8_9.gui.LadsSettingsScreen189;
import com.thelads.core.v1_8_9.log.Log4jServiceProvider;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * QA only (-Dthelads.verify189Core=true, sandbox game folders only): the C1 checks through 1.8.9's real input paths.
 * Synthetic events go into LWJGL's own keyboard and mouse queues, so GuiScreen.handleInput and Minecraft.runTick read them
 * like typed ones: Right Shift on the title screen, typing in the Lads menu, Right Shift in a QA world, Escape to the pause
 * menu and a click on its "Lads Client" button. Then the launcher catalog is checked. Screenshots: game/lads-qa/screenshots.
 */
public final class CoreProbe {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static final String WORLD = "Client QA 1_8_9";
    private interface Step { boolean run(Minecraft mc) throws Exception; }
    private static final List<Step> STEPS = Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::title, CoreProbe::menuAtTitle,
        CoreProbe::menuRendered, CoreProbe::searchClicked, CoreProbe::typed, CoreProbe::erased, CoreProbe::editingLeft,
        CoreProbe::closedToTitle, CoreProbe::worldReady, CoreProbe::bridgeInWorld, CoreProbe::menuInWorld, CoreProbe::closedToGame,
        CoreProbe::pauseMenu, CoreProbe::pauseClicked, CoreProbe::menuFromPause, CoreProbe::closedToPause, CoreProbe::menuKeyAtPause,
        CoreProbe::catalog, CoreProbe::leftWorld);
    private static int step, passed, delay, waited;
    private static boolean finished, pauseOnLostFocus;
    private static GuiScreen title, pause;
    private static LadsSettingsScreen189 menu;
    private CoreProbe() {}

    /** Every client tick (END); one step at a time, each after the previous one's screens had frames to render. */
    public static void tick() {
        if (finished) return;
        if (delay > 0) { delay--; return; }
        try {
            if (!STEPS.get(step).run(Minecraft.getMinecraft())) {
                if (++waited > 2400) throw new IllegalStateException("step " + (step + 1) + " did not complete within 120 s");
                return;
            }
            waited = 0;
            if (++step < STEPS.size()) return;
            finish();
            LOG.info("Lads 1.8.9 core probe END: {} passed, 0 failed; menu key through GuiScreen.handleInput and runTick, pause-menu "
                + "button, 1.8.9 bridge in a QA world, launcher catalog", passed);
        } catch (Throwable failure) {
            finish();
            LOG.error("Lads 1.8.9 core probe FAILED after {} checks", passed, failure);
        }
    }

    private static void finish() {
        finished = true;
        if (Minecraft.getMinecraft().gameSettings != null && title != null) Minecraft.getMinecraft().gameSettings.pauseOnLostFocus = pauseOnLostFocus;
    }

    private static boolean titleShown(Minecraft mc) {
        return mc.currentScreen instanceof GuiMainMenu && after(40);
    }

    private static boolean title(Minecraft mc) throws Exception {
        if (!(mc.currentScreen instanceof GuiMainMenu)) {
            // The sandbox window opens on the user's desktop; a stray click can leave the title screen before QA starts.
            LOG.warn("Lads 1.8.9 core probe: {} replaced the title screen before the checks; reopening it", mc.currentScreen);
            mc.displayGuiScreen(new GuiMainMenu());
            delay = 20;
            return false;
        }
        title = mc.currentScreen;
        // An unfocused sandbox window must not open the pause menu by itself during the world steps.
        pauseOnLostFocus = mc.gameSettings.pauseOnLostFocus;
        mc.gameSettings.pauseOnLostFocus = false;
        check(refused(), "shared saves, resource packs and shader packs are refused on 1.8.9 (SharedContentPaths)");
        org.slf4j.Logger slf4j = org.slf4j.LoggerFactory.getLogger("TheLadsCore");
        check(slf4j.getClass().getName().startsWith(Log4jServiceProvider.class.getName()), "common's SLF4J logging goes to Minecraft's log4j");
        slf4j.info("Lads 1.8.9 core probe: this line came through the bundled SLF4J");
        check(LadsGameBridge.get() instanceof VanillaGameBridge189 && !LadsGameBridge.get().isIngame(), "the 1.8.9 game bridge is active, no world yet");
        tap(Keyboard.KEY_RSHIFT, '\0');
        return true;
    }

    private static boolean menuAtTitle(Minecraft mc) {
        check(mc.currentScreen instanceof LadsSettingsScreen189 && ((LadsSettingsScreen189) mc.currentScreen).parent() == title,
            "Right Shift through GuiScreen.handleInput opens the Lads menu over the title screen");
        menu = (LadsSettingsScreen189) mc.currentScreen;
        return after(10);
    }

    private static boolean menuRendered(Minecraft mc) throws Exception {
        LadsSettingsScreen ui = menu.ui();
        LadsSettingsScreen.Rect search = ui.controlBounds("search");
        check(mc.currentScreen == menu && search != null, "the Lads menu rendered its controls through the 1.8.9 graphics adapter");
        boolean onlyBuiltIn = true;
        for (String name : ui.visibleModuleNames()) onlyBuiltIn &= ModuleSupport.isBuiltIn(name);
        check(onlyBuiltIn, "the menu lists only modules built in on 1.8.9 (" + ui.visibleModuleNames().size() + ")");
        screenshot(mc, "c1-lads-menu-title");
        click(search.x() + search.width() / 2, search.y() + search.height() / 2);
        return after(2);
    }

    private static boolean searchClicked(Minecraft mc) throws Exception {
        check(menu.ui().isEditingText(), "a mouse click through GuiScreen.handleMouseInput focuses the search field");
        tap(Keyboard.KEY_Z, 'z');
        tap(Keyboard.KEY_O, 'o');
        tap(Keyboard.KEY_O, 'o');
        tap(Keyboard.KEY_M, 'm');
        return after(2);
    }

    private static boolean typed(Minecraft mc) throws Exception {
        check("zoom".equals(menu.ui().getSearchQuery()), "typed characters reach the search field (" + menu.ui().getSearchQuery() + ")");
        tap(Keyboard.KEY_BACK, '\b');
        return after(2);
    }

    private static boolean erased(Minecraft mc) throws Exception {
        check("zoo".equals(menu.ui().getSearchQuery()), "Backspace (LWJGL 14 -> GLFW 259) edits the search");
        tap(Keyboard.KEY_ESCAPE, (char) 27);
        return after(2);
    }

    private static boolean editingLeft(Minecraft mc) throws Exception {
        check(!menu.ui().isEditingText() && mc.currentScreen == menu, "Escape leaves the search field and keeps the menu open");
        tap(Keyboard.KEY_RSHIFT, '\0');
        return after(2);
    }

    private static boolean closedToTitle(Minecraft mc) throws Exception {
        check(mc.currentScreen == title, "a second Right Shift closes the Lads menu back to its title-screen parent");
        Path game = mc.mcDataDir.toPath().toRealPath(), saves = Files.createDirectories(game.resolve("saves")).toRealPath();
        check(inVerificationSandbox(game) && saves.startsWith(game), "the QA world goes into the sandbox game folder's own saves (" + saves + ")");
        LOG.info("Lads 1.8.9 core probe: opening the QA world '{}' in {}", WORLD, saves);
        mc.launchIntegratedServer(WORLD, WORLD, new WorldSettings(0L, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT));
        return true;
    }

    private static boolean worldReady(Minecraft mc) {
        if (mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) return false;
        return after(40);
    }

    private static boolean bridgeInWorld(Minecraft mc) throws Exception {
        LadsGameBridge bridge = LadsGameBridge.get();
        check(bridge.isIngame() && bridge.hasPlayer(), "the bridge sees the QA world and its player");
        check(bridge.getPlayerX() == MathHelper.floor_double(mc.thePlayer.posX) && bridge.getPlayerY() == MathHelper.floor_double(mc.thePlayer.posY)
            && bridge.getPlayerZ() == MathHelper.floor_double(mc.thePlayer.posZ), "bridge coordinates are the player's block position");
        check("minecraft:overworld".equals(bridge.getDimensionId()) && !bridge.getBiomeName().isEmpty() && bridge.getBiomeId() == null,
            "dimension and biome (" + bridge.getBiomeName() + ", no namespaced id on 1.8.9)");
        check(bridge.getHealth() > 0 && bridge.getMaxHealth() == 20 && bridge.getFoodLevel() >= 0 && bridge.getFoodLevel() <= 20
            && bridge.getSaturation() >= 0 && bridge.getXpLevel() >= 0, "health, food, saturation (integrated server) and XP");
        check(Arrays.asList("north", "south", "east", "west").contains(bridge.getPlayerDirection()) && bridge.getYaw() >= 0 && bridge.getYaw() < 360
            && bridge.getGameTime().matches("\\d\\d:\\d\\d") && bridge.getFps() >= 0 && bridge.getPing() >= 0 && bridge.getSpeed() >= 0,
            "direction, yaw, time, FPS, ping and speed");
        check("Singleplayer".equals(bridge.getServerAddress()) && bridge.getArmor().isEmpty() && !bridge.getActiveResourcePacks().contains(null),
            "server address, armor and resource packs");
        LOG.info("Lads 1.8.9 core probe: bridge in the QA world: xyz {} {} {}, biome {}, {}, facing {} (yaw {}), time {}, day {}, health {}/{}, food {}, "
                + "saturation {}, fps {}, ping {}, speed {}, effects {}, packs {}, scoreboard {}", bridge.getPlayerX(), bridge.getPlayerY(),
            bridge.getPlayerZ(), bridge.getBiomeName(), bridge.getDimensionId(), bridge.getPlayerDirection(), bridge.getYaw(), bridge.getGameTime(),
            bridge.getDayCount(), bridge.getHealth(), bridge.getMaxHealth(), bridge.getFoodLevel(), bridge.getSaturation(), bridge.getFps(),
            bridge.getPing(), bridge.getSpeed(), bridge.getActivePotionEffects(), bridge.getActiveResourcePacks(), bridge.getScoreboard());
        tap(Keyboard.KEY_RSHIFT, '\0');
        return after(2);
    }

    private static boolean menuInWorld(Minecraft mc) throws Exception {
        check(mc.currentScreen instanceof LadsSettingsScreen189 && ((LadsSettingsScreen189) mc.currentScreen).parent() == null,
            "Right Shift in gameplay (runTick's keyboard loop) opens the Lads menu over the world");
        menu = (LadsSettingsScreen189) mc.currentScreen;
        return after(10);
    }

    private static boolean closedToGame(Minecraft mc) throws Exception {
        if (mc.currentScreen == menu) {
            screenshot(mc, "c1-lads-menu-world");
            tap(Keyboard.KEY_RSHIFT, '\0');
            return false;
        }
        check(mc.currentScreen == null, "Right Shift closes it back to gameplay");
        tap(Keyboard.KEY_ESCAPE, (char) 27);
        return after(2);
    }

    private static boolean pauseMenu(Minecraft mc) throws Exception {
        check(mc.currentScreen instanceof GuiIngameMenu, "Escape in the QA world opens the pause menu");
        pause = mc.currentScreen;
        GuiButton button = pause instanceof LadsPauseButton ? ((LadsPauseButton) pause).ladsButton() : null;
        check(button != null && "Lads Client".equals(button.displayString) && button.visible && button.enabled,
            "GuiIngameMenuMixin added the Lads Client button to the pause menu");
        return after(10);
    }

    private static boolean pauseClicked(Minecraft mc) throws Exception {
        check(mc.currentScreen == pause, "the pause menu is still open");
        screenshot(mc, "c1-pause-menu");
        GuiButton button = ((LadsPauseButton) pause).ladsButton();
        click(button.xPosition + button.width / 2, button.yPosition + button.height / 2);
        return after(2);
    }

    private static boolean menuFromPause(Minecraft mc) throws Exception {
        check(mc.currentScreen instanceof LadsSettingsScreen189 && ((LadsSettingsScreen189) mc.currentScreen).parent() == pause,
            "a click on Lads Client (GuiScreen.mouseClicked -> actionPerformed) opens the Lads menu over the pause menu");
        menu = (LadsSettingsScreen189) mc.currentScreen;
        return after(10);
    }

    private static boolean closedToPause(Minecraft mc) throws Exception {
        if (mc.currentScreen == menu) {
            screenshot(mc, "c1-lads-menu-pause");
            tap(Keyboard.KEY_RSHIFT, '\0');
            return false;
        }
        check(mc.currentScreen == pause, "Right Shift closes it back to its pause-menu parent");
        tap(Keyboard.KEY_RSHIFT, '\0');
        return after(2);
    }

    private static boolean menuKeyAtPause(Minecraft mc) throws Exception {
        check(mc.currentScreen instanceof LadsSettingsScreen189 && ((LadsSettingsScreen189) mc.currentScreen).parent() == pause,
            "Right Shift on the pause menu opens the Lads menu over it");
        tap(Keyboard.KEY_RSHIFT, '\0');
        return after(2);
    }

    private static boolean catalog(Minecraft mc) throws Exception {
        check(mc.currentScreen == pause, "and closes it back to the pause menu");
        Path file = ClientPaths.getBaseDir().resolve(CoreCatalogExporter.FILE_NAME);
        check(Files.isRegularFile(file), "the launcher catalog was written to " + file);
        JsonObject root = new JsonParser().parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8)).getAsJsonObject();
        String core = Loader.instance().getIndexedModList().get("theladscore").getVersion();
        check("1.8.9".equals(root.get("minecraftVersion").getAsString()) && core.equals(root.get("coreVersion").getAsString()),
            "the catalog names Minecraft 1.8.9 and Core " + core);
        JsonArray modules = root.getAsJsonArray("modules");
        int builtIn = 0, unavailable = 0, pending = 0;
        List<String> mismatched = new java.util.ArrayList<>();
        for (JsonElement element : modules) {
            JsonObject module = element.getAsJsonObject();
            String name = module.get("name").getAsString(), support = module.get("support").getAsString();
            if (!support.equals(ModuleSupport.support(name)) || module.get("toggleable").getAsBoolean()) mismatched.add(name + "=" + support);
            if (support.equals("builtIn")) builtIn++;
            else if (support.equals("unavailable")) unavailable++;
            else if (support.equals("pending")) pending++;
        }
        check(mismatched.isEmpty(), "every catalog row matches this game's registrations, none toggleable " + mismatched);
        check(modules.size() == ModuleManager.getInstance().getModules().size() && builtIn == 0 && unavailable == 25
            && pending == modules.size() - unavailable, "catalog statuses: " + builtIn + " built in, " + unavailable + " unavailable, " + pending + " pending");
        // Leave through the pause menu's own path so the QA world is saved.
        mc.theWorld.sendQuittingDisconnectingPacket();
        mc.loadWorld(null);
        mc.displayGuiScreen(new GuiMainMenu());
        return after(20);
    }

    private static boolean leftWorld(Minecraft mc) {
        check(mc.theWorld == null && mc.currentScreen instanceof GuiMainMenu, "the QA world closed back to the title screen");
        return true;
    }

    private static boolean refused() {
        try {
            SharedContentPaths.savesDir();
            return false;
        } catch (IllegalStateException expected) {
            return true;
        }
    }

    /** Only below &lt;repository&gt;/artifacts/verification, next to the repository's TheLadsCore/settings.gradle. */
    private static boolean inVerificationSandbox(Path game) {
        for (Path folder = game; folder != null && folder.getParent() != null && folder.getParent().getParent() != null; folder = folder.getParent())
            if (folder.getFileName().toString().equals("verification") && folder.getParent().getFileName().toString().equals("artifacts")
                && Files.isRegularFile(folder.getParent().getParent().resolve("TheLadsCore").resolve("settings.gradle"))) return true;
        return false;
    }

    private static void screenshot(Minecraft mc, String name) {
        File folder = new File(mc.mcDataDir, "lads-qa");
        new File(folder, "screenshots").mkdirs(); // ScreenShotHelper creates only the last folder
        ScreenShotHelper.saveScreenshot(folder, name + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
    }

    /** A key press and release, queued in LWJGL's keyboard buffer as Keyboard.poll() stores them. */
    private static void tap(int key, char character) throws Exception {
        for (int down = 1; down >= 0; down--) {
            ByteBuffer events = queue(Keyboard.class);
            events.compact();
            events.putInt(key).put((byte) down).putInt(character).putLong(System.nanoTime()).put((byte) 0);
            events.flip();
        }
    }

    /** A left click at a GUI position of the current screen, queued in LWJGL's mouse buffer (absolute window pixels, y up). */
    private static void click(int guiX, int guiY) throws Exception {
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen screen = mc.currentScreen;
        int x = (guiX * mc.displayWidth + mc.displayWidth / 2) / screen.width;
        int y = ((screen.height - 1 - guiY) * mc.displayHeight + mc.displayHeight / 2) / screen.height;
        for (int down = 1; down >= 0; down--) {
            ByteBuffer events = queue(Mouse.class);
            events.compact();
            events.put((byte) 0).put((byte) down).putInt(x).putInt(y).putInt(0).putLong(System.nanoTime());
            events.flip();
        }
    }

    private static ByteBuffer queue(Class<?> device) throws Exception {
        Field field = device.getDeclaredField("readBuffer");
        field.setAccessible(true);
        return (ByteBuffer) field.get(null);
    }

    private static boolean after(int ticks) {
        delay = ticks;
        return true;
    }

    private static void check(boolean result, String description) {
        if (!result) throw new IllegalStateException("1.8.9 core QA: " + description);
        passed++;
        LOG.info("Lads 1.8.9 core probe PASS {}: {}", passed, description);
    }
}
