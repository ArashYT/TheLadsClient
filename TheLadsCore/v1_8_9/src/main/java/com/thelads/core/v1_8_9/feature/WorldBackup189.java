package com.thelads.core.v1_8_9.feature;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldSettings;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Worlds are shared with the newer versions, and 1.8.9 corrupts a world a newer version saved. Before such a world opens
 * (its level.dat has the DataVersion every version since 1.9 writes), the player is asked for a backup: Yes copies it to
 * "&lt;name&gt; - 1.8.9" and opens the copy; No warns, in big red letters, that the world might be corrupted.
 */
public final class WorldBackup189 {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    public static final String SUFFIX = " - 1.8.9";
    /** The folder the player chose to open as it is, once. */
    private static String allowed;

    private WorldBackup189() {}

    /** Minecraft.launchIntegratedServer HEAD (MinecraftMixin): true when the prompt opened instead, so the launch is cancelled. */
    public static boolean intercept(Minecraft mc, String folder, String name, WorldSettings settings) {
        if (settings != null) return false; // a new world
        if (folder.equals(allowed)) { allowed = null; return false; }
        String version = newerVersion(savesDir(mc), folder);
        if (version == null) return false;
        mc.displayGuiScreen(new Prompt(mc.currentScreen, folder, name == null || name.isEmpty() ? folder : name, version));
        return true;
    }

    static File savesDir(Minecraft mc) { return new File(mc.mcDataDir, "saves"); }

    /** The newer version that last saved the world ("a newer version" when unnamed), or null for a 1.8.9 (or older) world. */
    public static String newerVersion(File saves, String folder) {
        File level = new File(new File(saves, folder), "level.dat");
        if (!level.isFile()) return null;
        try (InputStream in = new FileInputStream(level)) {
            NBTTagCompound data = CompressedStreamTools.readCompressed(in).getCompoundTag("Data");
            if (!data.hasKey("DataVersion") && !data.hasKey("Version", 10)) return null;
            String version = data.getCompoundTag("Version").getString("Name");
            return version.isEmpty() ? "a newer version" : version;
        } catch (Exception unreadable) {
            LOG.warn("Lads world check: could not read {}; letting Minecraft open it", level, unreadable);
            return null;
        }
    }

    /** Copies the world folder to a free "&lt;folder&gt; - 1.8.9" folder; returns its name. {@code copied} counts files. */
    public static String copy(File saves, String folder, AtomicInteger copied) throws IOException {
        String target = folder + SUFFIX;
        for (int n = 2; new File(saves, target).exists(); n++) target = folder + SUFFIX + " (" + n + ")";
        Path from = new File(saves, folder).toPath(), to = new File(saves, target).toPath();
        Files.walkFileTree(from, new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (!file.getFileName().toString().equals("session.lock")) Files.copy(file, to.resolve(from.relativize(file).toString()));
                copied.incrementAndGet();
                return FileVisitResult.CONTINUE;
            }
        });
        return target;
    }

    /** Forge's own checks (missing mods) ran before the prompt; this opens the world past it. */
    private static void open(Minecraft mc, String folder, String name) {
        allowed = folder;
        mc.launchIntegratedServer(folder, name, null);
    }

    /** "Back up this world first?" */
    static final class Prompt extends GuiScreen {
        private final GuiScreen parent;
        private final String folder, name, version;
        private final AtomicInteger copied = new AtomicInteger();
        private volatile String status;
        private volatile boolean busy;

        Prompt(GuiScreen parent, String folder, String name, String version) {
            this.parent = parent;
            this.folder = folder;
            this.name = name;
            this.version = version;
        }

        @Override public void initGui() {
            buttonList.clear();
            int y = height / 2 + 30;
            buttonList.add(new GuiButton(0, width / 2 - 155, y, 310, 20, "Yes, back it up and play the copy (recommended)"));
            buttonList.add(new GuiButton(1, width / 2 - 155, y + 24, 152, 20, "No, play the original"));
            buttonList.add(new GuiButton(2, width / 2 + 3, y + 24, 152, 20, "Cancel"));
            for (GuiButton button : buttonList) button.enabled = !busy;
        }

        @Override protected void actionPerformed(GuiButton button) {
            if (busy) return;
            if (button.id == 2) mc.displayGuiScreen(parent);
            else if (button.id == 1) mc.displayGuiScreen(new Warning(this, parent, folder, name));
            else backUp();
        }

        private void backUp() {
            busy = true;
            for (GuiButton button : buttonList) button.enabled = false;
            File saves = savesDir(mc);
            Thread worker = new Thread(() -> {
                try {
                    String copy = copy(saves, folder, copied);
                    mc.addScheduledTask(() -> {
                        mc.getSaveLoader().renameWorld(copy, name + SUFFIX);
                        LOG.info("Lads world backup: '{}' copied to '{}' ({} files); opening the copy", folder, copy, copied.get());
                        open(mc, copy, name + SUFFIX);
                    });
                } catch (Exception failure) {
                    LOG.error("Lads world backup FAILED for '{}'", folder, failure);
                    status = "The backup failed: " + failure.getMessage() + ". Nothing was opened.";
                    busy = false;
                    mc.addScheduledTask(this::initGui);
                }
            }, "Lads world backup");
            worker.setDaemon(true);
            worker.start();
        }

        @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawDefaultBackground();
            drawCenteredString(fontRendererObj, "Back up this world first?", width / 2, height / 2 - 70, 0xFFFFFF);
            List<String> lines = fontRendererObj.listFormattedStringToWidth("\"" + name + "\" was last played in Minecraft " + version
                + ". Opening it in 1.8.9 can corrupt it: terrain and items from newer versions are lost. A backup keeps the original"
                + " safe and opens a copy named \"" + name + SUFFIX + "\".", 300);
            for (int i = 0; i < lines.size(); i++) drawCenteredString(fontRendererObj, lines.get(i), width / 2, height / 2 - 48 + i * 11, 0xD0D0D0);
            if (busy) drawCenteredString(fontRendererObj, "Backing up... " + copied.get() + " files", width / 2, height / 2 + 14, 0xFFFF55);
            else if (status != null) drawCenteredString(fontRendererObj, status, width / 2, height / 2 + 14, 0xFF5555);
            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override protected void keyTyped(char typedChar, int keyCode) {
            if (keyCode == 1 && !busy) mc.displayGuiScreen(parent);
        }
    }

    /** No backup: the world might be corrupted, said big and red. */
    static final class Warning extends GuiScreen {
        private final GuiScreen prompt, list;
        private final String folder, name;

        Warning(GuiScreen prompt, GuiScreen list, String folder, String name) {
            this.prompt = prompt;
            this.list = list;
            this.folder = folder;
            this.name = name;
        }

        @Override public void initGui() {
            buttonList.clear();
            int y = height / 2 + 34;
            buttonList.add(new GuiButton(0, width / 2 - 155, y, 310, 20, "Go back and make a backup"));
            buttonList.add(new GuiButton(1, width / 2 - 155, y + 24, 310, 20, "§cI understand, play the original anyway"));
        }

        @Override protected void actionPerformed(GuiButton button) {
            if (button.id == 0) mc.displayGuiScreen(prompt);
            else {
                LOG.warn("Lads world backup: the player opened '{}' (saved by a newer version) without a backup", folder);
                open(mc, folder, name);
            }
        }

        @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
            drawDefaultBackground();
            drawRect(0, height / 2 - 78, width, height / 2 + 26, 0x90400000);
            float scale = Math.min(3f, (width - 20) / (float) fontRendererObj.getStringWidth("THIS WORLD MIGHT BE CORRUPTED"));
            GlStateManager.pushMatrix();
            GlStateManager.translate(width / 2f, height / 2f - 70, 0);
            GlStateManager.scale(scale, scale, 1);
            drawCenteredString(fontRendererObj, "THIS WORLD MIGHT BE CORRUPTED", 0, 0, 0xFF3333);
            GlStateManager.popMatrix();
            List<String> lines = fontRendererObj.listFormattedStringToWidth("\"" + name + "\" was saved by a newer Minecraft. Without a backup,"
                + " opening it in 1.8.9 can permanently damage it, also for the newer versions: chunks get regenerated and newer blocks,"
                + " items and entities are lost. This cannot be undone.", Math.min(360, width - 20));
            for (int i = 0; i < lines.size(); i++) drawCenteredString(fontRendererObj, lines.get(i), width / 2, height / 2 - 30 + i * 11, 0xFF7777);
            super.drawScreen(mouseX, mouseY, partialTicks);
        }

        @Override protected void keyTyped(char typedChar, int keyCode) {
            if (keyCode == 1) mc.displayGuiScreen(prompt);
        }
    }
}
