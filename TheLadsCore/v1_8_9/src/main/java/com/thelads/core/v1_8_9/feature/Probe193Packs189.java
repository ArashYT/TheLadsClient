package com.thelads.core.v1_8_9.feature;

import com.thelads.core.v1_8_9.gui.FolderNav189;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.ResourcePackRepository;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class Probe193Packs189 {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static int step = 0;
    private static int wait = 0;

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
                mc.displayGuiScreen(new net.minecraft.client.gui.GuiMainMenu());
                step = 0;
                return true;
        }
        return true;
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

    private static void setSearchText(GuiScreen screen, String text) {
        try {
            for (Field f : screen.getClass().getDeclaredFields()) {
                if (GuiTextField.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    GuiTextField tf = (GuiTextField) f.get(screen);
                    if (tf != null) {
                        tf.setText(text);
                        return;
                    }
                }
            }
        } catch (Exception ignored) {}
    }
}
