package com.thelads.core.v1_8_9.feature;

import com.thelads.core.modules.OldAnimationsModule;
import com.thelads.core.modules.OldAnimationsModule.Feature;
import com.thelads.core.v1_8_9.gui.FolderNav189;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.ResourcePackListEntry;
import net.minecraft.client.resources.ResourcePackListEntryFound;
import net.minecraft.client.resources.ResourcePackRepository;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Probe193Packs189 {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static int step = 0;
    private static int wait = 0;
    private static final OldAnimationsModule ANIMATIONS = OldAnimations189.MODULE;
    private static boolean animationsWere, lowFireWas;
    private static String deselected;
    private static boolean pointerOnArrow;

    public static void ensureSandboxPacks(Minecraft mc) {
        try {
            File target = mc.getResourcePackRepository().getDirResourcepacks();
            if (!target.exists()) target.mkdirs();
            File source = new File(mc.mcDataDir, "tools/sandbox-packs");
            if (!source.isDirectory()) {
                File cur = mc.mcDataDir;
                for (int i = 0; i < 4 && cur != null; i++) {
                    File candidate = new File(cur, "tools/sandbox-packs");
                    if (candidate.isDirectory()) {
                        source = candidate;
                        break;
                    }
                    cur = cur.getParentFile();
                }
            }
            if (source.isDirectory()) {
                copyRecursive(source, target);
            }
            mc.getResourcePackRepository().updateRepositoryEntriesAll();
        } catch (Exception e) {
            LOG.error("Failed to copy sandbox packs: " + e.getMessage());
        }
    }

    private static void copyRecursive(File src, File dst) {
        if (src.isDirectory()) {
            if (!dst.exists()) dst.mkdirs();
            File[] files = src.listFiles();
            if (files != null) {
                for (File f : files) {
                    copyRecursive(f, new File(dst, f.getName()));
                }
            }
        } else {
            try {
                Files.copy(src.toPath(), dst.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {}
        }
    }

    public static boolean runTitlePacks(Minecraft mc) throws Exception {
        if (wait > 0) { wait--; return false; }
        switch (step) {
            case 0:
                ensureSandboxPacks(mc);
                mc.displayGuiScreen(new GuiScreenResourcePacks(mc.currentScreen));
                wait = 20;
                step++;
                return false;
            case 1:
                CoreProbe.screenshot(mc, "189-packs-title");
                wait = 10;
                step++;
                return false;
            case 2:
                if (mc.currentScreen instanceof GuiScreenResourcePacks) {
                    setSearchText(mc.currentScreen, "PvP");
                }
                wait = 20;
                step++;
                return false;
            case 3:
                CoreProbe.screenshot(mc, "189-packs-search");
                wait = 10;
                step++;
                return false;
            case 4:
                if (mc.currentScreen instanceof GuiScreenResourcePacks && mc.currentScreen instanceof FolderNav189) {
                    setSearchText(mc.currentScreen, "");
                    ((FolderNav189) mc.currentScreen).ladsCd("PvP");
                }
                wait = 20;
                step++;
                return false;
            case 5:
                CoreProbe.screenshot(mc, "189-packs-in-pvp");
                wait = 10;
                step++;
                return false;
            case 6:
                ResourcePackRepository repo = mc.getResourcePackRepository();
                List<ResourcePackRepository.Entry> available = repo.getRepositoryEntriesAll();
                for (ResourcePackRepository.Entry entry : available) {
                    if (entry.getResourcePackName().contains("PvPPackOne")) {
                        repo.setRepositories(java.util.Collections.singletonList(entry));
                        break;
                    }
                }
                mc.gameSettings.resourcePacks.clear();
                for (ResourcePackRepository.Entry entry : repo.getRepositoryEntries()) {
                    mc.gameSettings.resourcePacks.add(entry.getResourcePackName());
                }
                mc.gameSettings.saveOptions();
                mc.displayGuiScreen(null);
                wait = 20;
                step++;
                return false;
            case 7:
                mc.displayGuiScreen(new GuiScreenResourcePacks(mc.currentScreen));
                wait = 20;
                step++;
                return false;
            case 8:
                CoreProbe.screenshot(mc, "189-packs-pvp-selected");
                animationsWere = ANIMATIONS.isEnabledSetting();
                lowFireWas = ANIMATIONS.option(Feature.LOW_FIRE).get();
                ANIMATIONS.setEnabled(true);
                ANIMATIONS.option(Feature.LOW_FIRE).set(false);
                mc.displayGuiScreen(new GuiScreenResourcePacks(mc.currentScreen)); // reopened: the Low Fire button reads the options in initGui
                wait = 20;
                step++;
                return false;
            case 9: { // the Low Fire button (1.9.15): on screen at the real size and at 427x240 (854x480 at GUI scale 2), clear of the others
                GuiScreen screen = mc.currentScreen;
                CoreProbe.check(screen instanceof GuiScreenResourcePacks && fireButton(screen) != null, "the pack screen has a Low Fire button");
                CoreProbe.check(fireProblems(screen).isEmpty(), "Low Fire fits the pack screen at " + screen.width + "x" + screen.height + " " + fireProblems(screen));
                int width = screen.width, height = screen.height;
                screen.setWorldAndResolution(mc, 427, 240);
                CoreProbe.check(fireProblems(screen).isEmpty(), "Low Fire fits the pack screen at 427x240 " + fireProblems(screen));
                screen.setWorldAndResolution(mc, width, height);
                GuiButton fire = fireButton(screen);
                CoreProbe.check(fire.enabled && fire.displayString.equals("Low Fire: OFF"), "Low Fire starts active and reads '" + fire.displayString + "' with the option off");
                CoreProbe.screenshot(mc, "189-packs-lowfire-off");
                press(fire);
                wait = 10;
                step++;
                return false;
            }
            case 10: {
                GuiButton fire = fireButton(mc.currentScreen);
                CoreProbe.check(ANIMATIONS.option(Feature.LOW_FIRE).get() && fire.displayString.equals("Low Fire: ON"),
                    "pressing Low Fire turned the option on; the button reads '" + fire.displayString + "'");
                CoreProbe.screenshot(mc, "189-packs-lowfire-on");
                press(fire);
                wait = 10;
                step++;
                return false;
            }
            case 11: {
                GuiButton fire = fireButton(mc.currentScreen);
                CoreProbe.check(!ANIMATIONS.option(Feature.LOW_FIRE).get() && fire.displayString.equals("Low Fire: OFF"),
                    "pressing Low Fire again turned the option off; the button reads '" + fire.displayString + "'");
                ANIMATIONS.setEnabled(false);
                mc.displayGuiScreen(new GuiScreenResourcePacks(mc.currentScreen));
                wait = 20;
                step++;
                return false;
            }
            case 12: {
                GuiButton fire = fireButton(mc.currentScreen);
                CoreProbe.check(!fire.enabled, "Low Fire is inactive while 1.7 Animations is off");
                CoreProbe.screenshot(mc, "189-packs-lowfire-inactive");
                press(fire);
                wait = 10;
                step++;
                return false;
            }
            case 13:
                CoreProbe.check(!ANIMATIONS.option(Feature.LOW_FIRE).get(), "a click on the inactive Low Fire button changed nothing");
                ANIMATIONS.option(Feature.LOW_FIRE).set(lowFireWas);
                ANIMATIONS.setEnabled(animationsWere);
                mc.displayGuiScreen(new GuiScreenResourcePacks(new net.minecraft.client.gui.GuiMainMenu()));
                wait = 20;
                step++;
                return false;
            case 14: { // PRE-1: a pack selected when the screen opens, deselected with its own arrow, is listed in Available again
                GuiScreenResourcePacks screen = (GuiScreenResourcePacks) mc.currentScreen;
                // The Selected list's first row: the list starts at width / 2 + 4, its rows at 32 + 4 + 13 (header); x 5 is the remove arrow.
                int arrowX = screen.width / 2 + 4 + 2 + 5, arrowY = 32 + 4 + 13 + 5;
                if (!pointerOnArrow) {
                    List<ResourcePackListEntry> selected = screen.getSelectedResourcePacks();
                    deselected = selected.isEmpty() || !(selected.get(0) instanceof ResourcePackListEntryFound) ? null
                        : ((ResourcePackListEntryFound) selected.get(0)).func_148318_i().getResourcePackName();
                    CoreProbe.check(deselected != null, "a pack is selected when the pack screen opens (PvPPackOne from step 6)");
                    // QA: the pointer goes there first. A list takes clicks only where it last drew the pointer (GuiSlot.mouseX, from
                    // Mouse.getX: the real pointer, not a queued click), so a WM_MOUSEMOVE to the window puts it on the arrow, as
                    // ItemsProbe189 hovers slots; the OS cursor stays where it is.
                    int px = (arrowX * mc.displayWidth + mc.displayWidth / 2) / screen.width, py = (arrowY * mc.displayHeight + mc.displayHeight / 2) / screen.height;
                    com.sun.jna.platform.win32.User32.INSTANCE.PostMessage(Borderless189.hwnd(), 0x0200 /* WM_MOUSEMOVE */,
                        new com.sun.jna.platform.win32.WinDef.WPARAM(0), new com.sun.jna.platform.win32.WinDef.LPARAM((long) py << 16 | px & 0xFFFF));
                    pointerOnArrow = true;
                    wait = 3;
                    return false;
                }
                pointerOnArrow = false;
                CoreProbe.screenshot(mc, "189-packs-pointer-on-arrow");
                if (CoreProbe.PYLON) {
                    // Pylon (Ionium) reads the pointer from SDL, which neither the window message nor a queued event moves: the lists
                    // get it from a draw at the arrow, then the screen's own mouseClicked runs as a click event would call it.
                    screen.drawScreen(arrowX, arrowY, 0F);
                    net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(net.minecraft.client.gui.GuiScreen.class, screen,
                        new String[] {"mouseClicked", "func_73864_a"}, int.class, int.class, int.class).invoke(screen, arrowX, arrowY, 0);
                } else {
                    CoreProbe.click(arrowX, arrowY);
                }
                wait = 10;
                step++;
                return false;
            }
            case 15: {
                GuiScreenResourcePacks screen = (GuiScreenResourcePacks) mc.currentScreen;
                CoreProbe.screenshot(mc, "189-packs-after-arrow");
                LOG.info("Lads packs QA: after the arrow click: selected {}, available {}, startup packs {}", packNames(screen.getSelectedResourcePacks()),
                    packNames(screen.getAvailableResourcePacks()), mc.gameSettings.resourcePacks);
                CoreProbe.check(packNames(screen.getSelectedResourcePacks()).indexOf(deselected) < 0, deselected + " left Selected after its remove arrow");
                setSearchText(screen, deselected); // wherever its folder is
                CoreProbe.check(packNames(screen.getAvailableResourcePacks()).contains(deselected), deselected + " is listed in Available after deselecting it");
                CoreProbe.screenshot(mc, "189-packs-deselected");
                setSearchText(screen, "");
                int before = screen.getAvailableResourcePacks().size();
                screen.setWorldAndResolution(mc, screen.width, screen.height); // a resize re-inits with the move pending
                setSearchText(screen, deselected);
                CoreProbe.check(packNames(screen.getAvailableResourcePacks()).contains(deselected), deselected + " is still in Available after a resize");
                setSearchText(screen, "");
                CoreProbe.check(screen.getAvailableResourcePacks().size() == before,
                    "a resize keeps the Available list (" + before + " rows before, " + screen.getAvailableResourcePacks().size() + " after)");
                mc.displayGuiScreen(new net.minecraft.client.gui.GuiMainMenu()); // never Done: the selection stays as it was
                step = 0;
                return true;
            }
        }
        return true;
    }

    /** The pack names of the Found rows in a pack list, in order. */
    private static List<String> packNames(List<ResourcePackListEntry> rows) {
        List<String> names = new ArrayList<>();
        for (ResourcePackListEntry row : rows)
            if (row instanceof ResourcePackListEntryFound) names.add(((ResourcePackListEntryFound) row).func_148318_i().getResourcePackName());
        return names;
    }

    public static boolean runInGamePacks(Minecraft mc) throws Exception {
        if (wait > 0) { wait--; return false; }
        switch (step) {
            case 0:
                mc.displayGuiScreen(new GuiScreenResourcePacks(mc.currentScreen));
                wait = 20;
                step++;
                return false;
            case 1:
                CoreProbe.screenshot(mc, "189-packs-ingame");
                mc.displayGuiScreen(null);
                step = 0;
                return true;
        }
        return true;
    }

    public static boolean runRelaunchPacks(Minecraft mc) throws Exception {
        if (wait > 0) { wait--; return false; }
        switch (step) {
            case 0:
                boolean hasPvPPack = false;
                for (String p : mc.gameSettings.resourcePacks) {
                    if (p.contains("PvPPackOne")) {
                        hasPvPPack = true;
                        break;
                    }
                }
                LOG.info("Lads packs relaunch check: gameSettings.resourcePacks=" + mc.gameSettings.resourcePacks + ", hasPvPPack=" + hasPvPPack);
                if (!hasPvPPack) {
                    LOG.error("Lads packs probe FAILED: PvPPackOne not found in gameSettings.resourcePacks!");
                }
                mc.displayGuiScreen(new GuiScreenResourcePacks(mc.currentScreen));
                wait = 20;
                step++;
                return false;
            case 1:
                CoreProbe.screenshot(mc, "189-packs-relaunch");
                mc.displayGuiScreen(new net.minecraft.client.gui.GuiMainMenu());
                LOG.info("Lads packs relaunch PASS: 189-packs-relaunch captured successfully");
                step = 0;
                return true;
        }
        return true;
    }

    private static List<GuiButton> buttons(GuiScreen screen) {
        return ReflectionHelper.getPrivateValue(GuiScreen.class, screen, "buttonList", "field_146292_n");
    }

    private static GuiButton fireButton(GuiScreen screen) {
        for (GuiButton button : buttons(screen)) if (button.displayString.startsWith("Low Fire")) return button;
        return null;
    }

    /** What is wrong with Low Fire's place on the screen: off it, over another button, or up in the pack lists (they end at height - 51). */
    private static List<String> fireProblems(GuiScreen screen) {
        List<String> problems = new ArrayList<>();
        GuiButton fire = fireButton(screen);
        if (fire == null) {
            problems.add("missing");
            return problems;
        }
        if (fire.xPosition != screen.width / 2 - 75 || fire.yPosition != screen.height - 25 || fire.width != 150 || fire.height != 20)
            problems.add("at " + fire.xPosition + "," + fire.yPosition + " " + fire.width + "x" + fire.height + ", not the centred 150x20 row at the bottom");
        if (fire.xPosition < 0 || fire.yPosition < screen.height - 51 || fire.xPosition + fire.width > screen.width || fire.yPosition + fire.height > screen.height)
            problems.add("off the screen or over the pack lists");
        for (GuiButton other : buttons(screen))
            if (other != fire && fire.xPosition < other.xPosition + other.width && other.xPosition < fire.xPosition + fire.width
                && fire.yPosition < other.yPosition + other.height && other.yPosition < fire.yPosition + fire.height)
                problems.add("overlaps '" + other.displayString + "'");
        return problems;
    }

    /** A real click on the button's centre: GuiScreen.mouseClicked, Forge's action events, then actionPerformed. */
    private static void press(GuiButton button) throws Exception {
        CoreProbe.click(button.xPosition + button.width / 2, button.yPosition + button.height / 2);
    }

    private static void setSearchText(GuiScreen screen, String text) {
        try {
            for (Field f : screen.getClass().getDeclaredFields()) {
                if (GuiTextField.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    GuiTextField tf = (GuiTextField) f.get(screen);
                    if (tf != null) {
                        tf.setText(text);
                        try {
                            java.lang.reflect.Method m = screen.getClass().getDeclaredMethod("ladsApplyFilter");
                            m.setAccessible(true);
                            m.invoke(screen);
                        } catch (Exception ignored) {}
                        return;
                    }
                }
            }
        } catch (Exception ignored) {}
    }
}
