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
import com.thelads.core.v1_8_9.adapter.VanillaGameBridge189;
import com.thelads.core.v1_8_9.gui.LadsPauseButton;
import com.thelads.core.v1_8_9.gui.LadsSettingsScreen189;
import com.thelads.core.v1_8_9.gui.LadsTitleScreen189;
import com.thelads.core.v1_8_9.gui.TitleExtrasScreen189;
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
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.resources.I18n;
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
 * menu and a click on its "Lads Client" button. Then the launcher catalog is checked and HudProbe runs the C2 HUD checks in
 * the same QA world. Screenshots: game/lads-qa/screenshots.
 */
public final class CoreProbe {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static final String WORLD = "Client QA 1_8_9";
    interface Step { boolean run(Minecraft mc) throws Exception; }
    private static final List<Step> STEPS = new java.util.ArrayList<>(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::ladsTitle,
        CoreProbe::titleMore, CoreProbe::backupPrompt, CoreProbe::backupWarning, CoreProbe::backupDone, CoreProbe::title,
        CoreProbe::menuAtTitle, CoreProbe::menuRendered, CoreProbe::searchClicked, CoreProbe::typed, CoreProbe::erased, CoreProbe::editingLeft,
        CoreProbe::closedToTitle, CoreProbe::worldReady, CoreProbe::bridgeInWorld, CoreProbe::menuInWorld, CoreProbe::closedToGame,
        CoreProbe::pauseMenu, CoreProbe::pauseMultiplayer, CoreProbe::multiplayerConfirm, CoreProbe::pauseClicked, CoreProbe::menuFromPause, CoreProbe::closedToPause, CoreProbe::menuKeyAtPause,
        CoreProbe::catalog));
    static {
        // -Dthelads.verify189Only=170 (the harness's LADS_VERIFY_189_ONLY): straight into the QA world for the 1.7.0 HUD checks only.
        if ("170".equals(System.getProperty("thelads.verify189Only"))) {
            STEPS.clear();
            STEPS.addAll(Arrays.<Step>asList(CoreProbe::titleShown, CoreProbe::quickWorld, CoreProbe::worldReady));
            STEPS.addAll(Probe170.STEPS);
        } else {
            STEPS.addAll(Probe160s.STEPS);
            STEPS.addAll(HudProbe.STEPS);
            STEPS.addAll(Probe145.STEPS);
            STEPS.addAll(Probe150e.STEPS);
            STEPS.addAll(Probe150.STEPS);
            STEPS.addAll(Probe151.STEPS);
            STEPS.addAll(Probe160.STEPS);
            STEPS.addAll(Probe170.STEPS);
            STEPS.addAll(Probe145.PACING);
        }
        STEPS.add(CoreProbe::leaveWorld);
        STEPS.add(CoreProbe::leftWorld);
    }
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
                + "button, 1.8.9 bridge in a QA world, launcher catalog, HUD through RenderGameOverlayEvent and the HUD editor", passed);
        } catch (Throwable failure) {
            finish();
            LOG.error("Lads 1.8.9 core probe FAILED after {} checks", passed, failure);
        }
    }

    private static void finish() {
        finished = true;
        HudProbe.stop();
        Probe151.stop();
        Probe160.stop();
        Probe160s.stop();
        Probe170.stop();
        if (Minecraft.getMinecraft().gameSettings != null && title != null) Minecraft.getMinecraft().gameSettings.pauseOnLostFocus = pauseOnLostFocus;
    }

    private static boolean titleShown(Minecraft mc) {
        return mc.currentScreen instanceof GuiMainMenu && after(40);
    }

    /** The Lads title screen (LadsTitleScreen189): its main buttons laid out by TitleScreenTheme, and a click on More. */
    private static boolean ladsTitle(Minecraft mc) throws Exception {
        LadsTitleScreen189 lads = LadsTitleScreen189.INSTANCE;
        check(lads.screen() != null && lads.screen() == mc.currentScreen, "the TitleScreen module put the Lads layout on the title screen");
        List<String> labels = new java.util.ArrayList<>();
        boolean laidOut = true;
        for (GuiButton button : lads.mainButtons()) {
            labels.add(button.displayString);
            laidOut &= button.visible && button.width > 20 && button.xPosition >= 0 && button.xPosition + button.width <= mc.currentScreen.width
                && button.yPosition + button.height <= mc.currentScreen.height;
        }
        check(laidOut && labels.equals(Arrays.asList(I18n.format("menu.singleplayer"), I18n.format("menu.multiplayer"), "Lads Mods",
            I18n.format("menu.options"), "More...", I18n.format("menu.quit"))), "the main title actions, laid out by TitleScreenTheme " + labels);
        screenshot(mc, "c1-title");
        GuiButton more = lads.moreButton();
        click(more.xPosition + more.width / 2, more.yPosition + more.height / 2);
        return after(10);
    }

    /** More, then its Language: the title screen's own Language button pressed from More (GuiLanguage, a list on the Lads backdrop). */
    private static boolean titleMore(Minecraft mc) throws Exception {
        if (mc.currentScreen instanceof TitleExtrasScreen189) {
            TitleExtrasScreen189 more = (TitleExtrasScreen189) mc.currentScreen;
            check(more.labels().containsAll(Arrays.asList("Mods", "Language", "Realms", "Accounts")),
                "a click on More opens the secondary actions: Forge's Mods, Language, Realms, Accounts " + more.labels());
            screenshot(mc, "c1-title-more");
            GuiButton language = more.button("Language");
            click(language.xPosition + language.width / 2, language.yPosition + language.height / 2);
            return retry(10);
        }
        if (mc.currentScreen instanceof GuiLanguage) {
            check(LadsTitleScreen189.INSTANCE.screen() != null, "Language on More pressed the title screen's own Language button");
            screenshot(mc, "c1-title-language");
            // Not a synthetic click: GuiSlot selects the entry under the real cursor on any click (a language switch).
            mc.displayGuiScreen(LadsTitleScreen189.INSTANCE.screen());
            return retry(10);
        }
        check(mc.currentScreen instanceof GuiMainMenu && "en_US".equals(mc.gameSettings.language), "back on the title screen, language unchanged");
        return after(10);
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
        // Worlds are shared since 1.4.8: the saves link leads to the sandbox's shared folder, never a real one.
        check(inVerificationSandbox(game) && inVerificationSandbox(saves) && com.thelads.core.shared.SharedContentPaths.redirectEnabled()
            && saves.equals(com.thelads.core.shared.SharedContentPaths.savesDir().toRealPath()), "the QA world goes into the sandbox's shared saves (" + saves + ")");
        LOG.info("Lads 1.8.9 core probe: opening the QA world '{}' in {}", WORLD, saves);
        mc.launchIntegratedServer(WORLD, WORLD, new WorldSettings(0L, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT));
        return true;
    }

    /** The --only path: the title step's unfocused-window guard, the sandbox check and the QA world. */
    private static boolean quickWorld(Minecraft mc) throws Exception {
        title = mc.currentScreen;
        pauseOnLostFocus = mc.gameSettings.pauseOnLostFocus;
        mc.gameSettings.pauseOnLostFocus = false;
        Path game = mc.mcDataDir.toPath().toRealPath(), saves = Files.createDirectories(game.resolve("saves")).toRealPath();
        check(inVerificationSandbox(game) && inVerificationSandbox(saves), "the QA world goes into the sandbox (" + saves + ")");
        mc.launchIntegratedServer(WORLD, WORLD, new WorldSettings(0L, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT));
        return true;
    }

    private static boolean worldReady(Minecraft mc) {
        // A run that ended with the QA player dead reopens on the death screen: respawn and go on.
        if (mc.theWorld != null && mc.thePlayer != null && mc.currentScreen instanceof net.minecraft.client.gui.GuiGameOver) {
            mc.thePlayer.respawnPlayer();
            mc.displayGuiScreen(null);
            return false;
        }
        if (mc.theWorld == null || mc.thePlayer == null || mc.currentScreen != null) return false;
        // The QA world starts every run the same: no armour left over from an interrupted run.
        final java.util.UUID id = mc.thePlayer.getUniqueID();
        mc.getIntegratedServer().addScheduledTask(() -> {
            net.minecraft.entity.player.EntityPlayerMP player = mc.getIntegratedServer().getConfigurationManager().getPlayerByUUID(id);
            if (player != null) java.util.Arrays.fill(player.inventory.armorInventory, null);
        });
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

    /** The grouped pause menu (PauseMenuLayout) and its Multiplayer button, which asks before leaving the world (Stay in game here). */
    private static boolean pauseMultiplayer(Minecraft mc) throws Exception {
        GuiButton lads = ((LadsPauseButton) pause).ladsButton(), multiplayer = ((LadsPauseButton) pause).ladsMultiplayerButton();
        check(mc.currentScreen == pause && multiplayer != null && multiplayer.visible && multiplayer.width == lads.width
            && multiplayer.yPosition > lads.yPosition && multiplayer.xPosition < lads.xPosition && lads.yPosition + lads.height <= pause.height,
            "the pause menu is in groups: Options | Lads Client, then Multiplayer on the next row");
        click(multiplayer.xPosition + multiplayer.width / 2, multiplayer.yPosition + multiplayer.height / 2);
        return after(10);
    }

    private static boolean multiplayerConfirm(Minecraft mc) throws Exception {
        if (mc.currentScreen instanceof GuiYesNo) {
            check(mc.theWorld != null, "Multiplayer on the pause menu asks before saving and leaving the QA world");
            screenshot(mc, "c1-pause-multiplayer");
            click(mc.currentScreen.width / 2 + 80, mc.currentScreen.height / 6 + 106); // GuiYesNo's second button: Stay in game
            return retry(10);
        }
        check(mc.currentScreen == pause && mc.theWorld != null, "Stay in game returns to the pause menu, still in the world");
        return after(2);
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
        int builtIn = 0, unavailable = 0, pending = 0, external = 0;
        List<String> mismatched = new java.util.ArrayList<>(), builtInNames = new java.util.ArrayList<>();
        for (JsonElement element : modules) {
            JsonObject module = element.getAsJsonObject();
            String name = module.get("name").getAsString(), support = module.get("support").getAsString();
            if (!support.equals(ModuleSupport.support(name)) || module.get("toggleable").getAsBoolean() != ModuleSupport.isToggleable(name))
                mismatched.add(name + "=" + support);
            if (support.equals("builtIn")) { builtIn++; builtInNames.add(name); }
            else if (support.equals("unavailable")) unavailable++;
            else if (support.equals("pending")) pending++;
            else if (support.equals("external")) external++;
        }
        check(mismatched.isEmpty(), "every catalog row matches this game's registrations and switchability " + mismatched);
        java.util.Set<String> expected = new java.util.HashSet<>(Arrays.asList(NativeHud.MODULES));
        expected.addAll(Arrays.asList(com.thelads.core.v1_8_9.TheLadsCore189.GAMEPLAY_MODULES));
        for (String[] module : com.thelads.core.v1_8_9.TheLadsCore189.LIMITED) expected.add(module[0]);
        check(new java.util.HashSet<>(builtInNames).equals(expected) && builtInNames.size() == expected.size(),
            "exactly the HUD modules NativeHud draws and the native gameplay modules are built in " + builtInNames);
        check(modules.size() == ModuleManager.getInstance().getModules().size() && builtIn == expected.size() && unavailable == com.thelads.core.v1_8_9.TheLadsCore189.MOD_BACKED.length + 4 /* DisableNarrator, ShulkerBoxUtils, Voice Chat, Voice Chat Group */
            && pending == modules.size() - unavailable - builtIn - external, "catalog statuses: " + builtIn + " built in, " + unavailable + " unavailable, "
            + external + " external, " + pending + " pending");
        mc.displayGuiScreen(null); // Back to Game: the HUD checks run in gameplay
        return after(10);
    }

    private static boolean leaveWorld(Minecraft mc) {
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

    private static final String NEWER = "Lads QA newer world", OLDER = "Lads QA 1_8_9 world";

    /** Shared worlds: a world a newer version saved asks for a backup before it opens; a 1.8.9 world opens directly. */
    private static boolean backupPrompt(Minecraft mc) throws Exception {
        File saves = WorldBackup189.savesDir(mc);
        world(saves, NEWER, true);
        world(saves, OLDER, false);
        check("26.3".equals(WorldBackup189.newerVersion(saves, NEWER)), "a world a newer version saved is recognised (DataVersion, version 26.3)");
        check(WorldBackup189.newerVersion(saves, OLDER) == null, "a 1.8.9 world needs no backup");
        check(!WorldBackup189.intercept(mc, OLDER, OLDER, null), "a 1.8.9 world opens without the prompt");
        java.util.concurrent.atomic.AtomicInteger copied = new java.util.concurrent.atomic.AtomicInteger();
        String copy = WorldBackup189.copy(saves, NEWER, copied);
        check(copy.equals(NEWER + WorldBackup189.SUFFIX) && new File(saves, copy + "/level.dat").isFile() && copied.get() >= 1,
            "the backup copies the world to '" + NEWER + " - 1.8.9'");
        mc.getSaveLoader().renameWorld(copy, NEWER + WorldBackup189.SUFFIX);
        check((NEWER + WorldBackup189.SUFFIX).equals(mc.getSaveLoader().getWorldInfo(copy).getWorldName()), "the copy is named '<name> - 1.8.9'");
        check(WorldBackup189.newerVersion(saves, NEWER) != null, "the original stays untouched");
        check(WorldBackup189.intercept(mc, NEWER, NEWER, null) && mc.currentScreen instanceof WorldBackup189.Prompt,
            "opening the newer world shows the backup prompt instead");
        return after(20);
    }

    private static boolean backupWarning(Minecraft mc) {
        check(mc.currentScreen instanceof WorldBackup189.Prompt, "the backup prompt is up");
        screenshot(mc, "w1-backup-prompt");
        mc.displayGuiScreen(new WorldBackup189.Warning(mc.currentScreen, title, NEWER, NEWER));
        return after(20);
    }

    private static boolean backupDone(Minecraft mc) throws Exception {
        check(mc.currentScreen instanceof WorldBackup189.Warning, "No shows the corruption warning");
        screenshot(mc, "w1-corruption-warning");
        File saves = WorldBackup189.savesDir(mc);
        for (String world : new String[] {NEWER, OLDER, NEWER + WorldBackup189.SUFFIX}) delete(new File(saves, world).toPath());
        mc.displayGuiScreen(new GuiMainMenu());
        return after(10);
    }

    private static void world(File saves, String name, boolean newer) throws Exception {
        net.minecraft.nbt.NBTTagCompound data = new net.minecraft.nbt.NBTTagCompound();
        data.setString("LevelName", name);
        data.setInteger("version", 19133);
        if (newer) {
            data.setInteger("DataVersion", 4671);
            net.minecraft.nbt.NBTTagCompound version = new net.minecraft.nbt.NBTTagCompound();
            version.setString("Name", "26.3");
            version.setInteger("Id", 4671);
            data.setTag("Version", version);
        }
        net.minecraft.nbt.NBTTagCompound root = new net.minecraft.nbt.NBTTagCompound();
        root.setTag("Data", data);
        File folder = new File(saves, name);
        check(folder.mkdirs() || folder.isDirectory(), "QA world folder " + folder);
        try (java.io.OutputStream out = new java.io.FileOutputStream(new File(folder, "level.dat"))) {
            net.minecraft.nbt.CompressedStreamTools.writeCompressed(root, out);
        }
    }

    private static void delete(Path folder) throws java.io.IOException {
        if (!Files.exists(folder)) return;
        try (java.util.stream.Stream<Path> walk = Files.walk(folder)) {
            for (Path path : (Iterable<Path>) walk.sorted(java.util.Comparator.reverseOrder())::iterator) Files.delete(path);
        }
    }

    /** Only below &lt;repository&gt;/artifacts/verification, next to the repository's TheLadsCore/settings.gradle. */
    private static boolean inVerificationSandbox(Path game) {
        for (Path folder = game; folder != null && folder.getParent() != null && folder.getParent().getParent() != null; folder = folder.getParent())
            if (folder.getFileName().toString().equals("verification") && folder.getParent().getFileName().toString().equals("artifacts")
                && Files.isRegularFile(folder.getParent().getParent().resolve("TheLadsCore").resolve("settings.gradle"))) return true;
        return false;
    }

    static void screenshot(Minecraft mc, String name) {
        File folder = new File(mc.mcDataDir, "lads-qa");
        new File(folder, "screenshots").mkdirs(); // ScreenShotHelper creates only the last folder
        ScreenShotHelper.saveScreenshot(folder, name + ".png", mc.displayWidth, mc.displayHeight, mc.getFramebuffer());
    }

    /** A key press and release, queued in LWJGL's keyboard buffer as Keyboard.poll() stores them. */
    static void tap(int key, char character) throws Exception {
        key(key, character, true);
        key(key, character, false);
    }

    /** One key event (a held key: press now, release later), queued as Keyboard.poll() stores it. */
    static void key(int key, char character, boolean down) throws Exception {
        ByteBuffer events = queue(Keyboard.class);
        events.compact();
        events.putInt(key).put((byte) (down ? 1 : 0)).putInt(character).putLong(System.nanoTime()).put((byte) 0);
        events.flip();
    }

    /** One mouse-wheel event in gameplay (120 is one notch on Windows), queued as Mouse.poll() stores it. */
    static void wheel(int dwheel) throws Exception {
        ByteBuffer events = queue(Mouse.class);
        events.compact();
        events.put((byte) -1).put((byte) 0).putInt(0).putInt(0).putInt(dwheel).putLong(System.nanoTime());
        events.flip();
    }

    /** A left click at a GUI position of the current screen. */
    static void click(int guiX, int guiY) throws Exception {
        mouse(0, true, guiX, guiY);
        mouse(0, false, guiX, guiY);
    }

    /**
     * One mouse event at a GUI position of the current screen (button -1: a move, which GuiScreen turns into mouseClickMove while
     * a button is held), queued in LWJGL's mouse buffer as Mouse.poll() stores it: absolute window pixels, y up. Without a screen
     * (gameplay) only the button matters.
     */
    static void mouse(int button, boolean down, int guiX, int guiY) throws Exception {
        event(button, down, guiX, guiY, 0);
    }

    /** Wheel notches (negative: towards the user, scrolling down), 120 each as Mouse.poll() stores them. */
    static void wheel(int guiX, int guiY, int notches) throws Exception {
        for (int i = 0; i < Math.abs(notches); i++) event(-1, false, guiX, guiY, notches > 0 ? 120 : -120);
    }

    private static void event(int button, boolean down, int guiX, int guiY, int wheel) throws Exception {
        Minecraft mc = Minecraft.getMinecraft();
        GuiScreen screen = mc.currentScreen;
        int x = screen == null ? 0 : (guiX * mc.displayWidth + mc.displayWidth / 2) / screen.width;
        int y = screen == null ? 0 : ((screen.height - 1 - guiY) * mc.displayHeight + mc.displayHeight / 2) / screen.height;
        ByteBuffer events = queue(Mouse.class);
        events.compact();
        events.put((byte) button).put((byte) (down ? 1 : 0)).putInt(x).putInt(y).putInt(wheel).putLong(System.nanoTime());
        events.flip();
    }

    private static ByteBuffer queue(Class<?> device) throws Exception {
        Field field = device.getDeclaredField("readBuffer");
        field.setAccessible(true);
        return (ByteBuffer) field.get(null);
    }

    /** Step done; the next one runs after this many ticks (each tick renders frames first). */
    static boolean after(int ticks) {
        delay = ticks;
        return true;
    }

    /** Step not done yet: run it again after this many ticks. */
    static boolean retry(int ticks) {
        delay = ticks;
        return false;
    }

    static void check(boolean result, String description) {
        if (!result) throw new IllegalStateException("1.8.9 core QA: " + description);
        passed++;
        LOG.info("Lads 1.8.9 core probe PASS {}: {}", passed, description);
    }
}
