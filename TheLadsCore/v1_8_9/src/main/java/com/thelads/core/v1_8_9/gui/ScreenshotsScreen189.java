package com.thelads.core.v1_8_9.gui;

import com.thelads.core.v1_8_9.feature.Screenshots189;
import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.Sys;
import org.lwjgl.input.Keyboard;

/**
 * The BetterScreenshots gallery on 1.8.9 (26.x: ManageScreenshotsScreen): the screenshots folder newest first, the selected one
 * previewed, and Open (the system viewer; double-click too), Copy (image to the clipboard), Delete (to the Recycle Bin or trash
 * where the system has one, asked first) and Folder. Decoding and file actions run off the render thread.
 */
public final class ScreenshotsScreen189 extends GuiScreen implements GuiYesNoCallback {
    private static final int OPEN = 0, COPY = 1, DELETE = 2, FOLDER = 3, DONE = 4;
    private static final SimpleDateFormat DATE = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    private final GuiScreen parent;
    private List<File> files = Collections.emptyList();
    private int selected = -1, listWidth, request;
    private FileList list;
    private ResourceLocation preview;
    private File previewFile;
    private int previewWidth, previewHeight;
    private String status = "";
    private boolean confirming;
    private final List<GuiButton> fileButtons = new ArrayList<>();

    public ScreenshotsScreen189(GuiScreen parent) {
        this.parent = parent;
        reload(null);
    }

    /** QA: the listed files, newest first, and the selected one. */
    public List<File> files() { return files; }
    public File selectedFile() { return selected >= 0 && selected < files.size() ? files.get(selected) : null; }
    public boolean previewShown() { return preview != null && previewFile != null && previewFile.equals(selectedFile()); }

    private void reload(File select) {
        File[] found = Screenshots189.folder().listFiles(Screenshots189::image);
        List<File> sorted = new ArrayList<>(found == null ? Collections.<File>emptyList() : Arrays.asList(found));
        sorted.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        files = sorted;
        select(select != null && files.contains(select) ? files.indexOf(select) : files.isEmpty() ? -1 : Math.max(0, Math.min(selected, files.size() - 1)));
    }

    private void select(int index) {
        selected = index;
        File file = selectedFile();
        if (file == null || file.equals(previewFile)) return;
        int ticket = ++request;
        Screenshots189.IO.execute(() -> {
            try {
                BufferedImage image = Screenshots189.read(file, 1280);
                mc().addScheduledTask(() -> {
                    if (ticket != request) return;
                    releasePreview();
                    preview = Screenshots189.texture("lads_screenshot_gallery", image);
                    previewFile = file;
                    previewWidth = image.getWidth();
                    previewHeight = image.getHeight();
                });
            } catch (IOException | RuntimeException unreadable) {
                mc().addScheduledTask(() -> { if (ticket == request) status = file.getName() + " could not be read: " + unreadable.getMessage(); });
            }
        });
    }

    @Override
    public void initGui() {
        listWidth = Math.max(120, Math.min(220, width * 2 / 5));
        list = new FileList();
        buttonList.clear();
        fileButtons.clear();
        String[] labels = {"Open", "Copy", "Delete", "Folder", "Done"};
        int buttonWidth = Math.min(72, (width - 16 - 4 * 4) / labels.length), left = (width - (buttonWidth * labels.length + 4 * 4)) / 2;
        for (int i = 0; i < labels.length; i++) {
            GuiButton button = new GuiButton(i, left + i * (buttonWidth + 4), height - 28, buttonWidth, 20, labels[i]);
            buttonList.add(button);
            if (i <= DELETE) fileButtons.add(button);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        for (GuiButton button : fileButtons) button.enabled = selectedFile() != null;
        list.drawScreen(mouseX, mouseY, partialTicks);
        drawCenteredString(fontRendererObj, "Screenshots (" + files.size() + ")", width / 2, 12, 0xFFFFFF);
        int left = 8 + listWidth + 8, right = width - 8, top = 32, bottom = height - 48;
        File file = selectedFile();
        if (file == null) drawCenteredString(fontRendererObj, "No screenshots yet: press " + GameSettings.getKeyDisplayString(mc.gameSettings.keyBindScreenshot.getKeyCode())
            + " in game.", (left + right) / 2, (top + bottom) / 2, 0xAAAAAA);
        else if (previewShown() && right - left > 10 && bottom - top > 30) {
            // Fit the image into the preview area above its name line, keeping its aspect ratio.
            float scale = Math.min((right - left) / (float) previewWidth, (bottom - top - 24) / (float) previewHeight);
            int w = Math.max(1, (int) (previewWidth * scale)), h = Math.max(1, (int) (previewHeight * scale));
            int x = (left + right - w) / 2;
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
            mc.getTextureManager().bindTexture(preview);
            Gui.drawModalRectWithCustomSizedTexture(x, top, 0, 0, w, h, w, h);
            drawCenteredString(fontRendererObj, fontRendererObj.trimStringToWidth(file.getName(), right - left), (left + right) / 2, top + h + 4, 0xFFFFFF);
            drawCenteredString(fontRendererObj, DATE.format(new Date(file.lastModified())) + "  " + (file.length() + 1023) / 1024 + " KB",
                (left + right) / 2, top + h + 14, 0xAAAAAA);
        }
        if (!status.isEmpty()) drawCenteredString(fontRendererObj, fontRendererObj.trimStringToWidth(status, width - 16), width / 2, height - 42, 0xFFFF55);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        File file = selectedFile();
        if (button.id == DONE) mc.displayGuiScreen(parent);
        else if (button.id == FOLDER) open(Screenshots189.folder());
        else if (file == null) return;
        else if (button.id == OPEN) open(file);
        else if (button.id == COPY) copy(file);
        else if (button.id == DELETE) {
            confirming = true;
            mc.displayGuiScreen(new GuiYesNo(this, trash() ? "Move this screenshot to the Recycle Bin?"
                : "Delete this screenshot? It cannot be restored.", file.getName(), 0));
        }
    }

    @Override
    public void confirmClicked(boolean result, int id) {
        File file = selectedFile();
        mc.displayGuiScreen(this);
        if (!result || file == null) return;
        Screenshots189.IO.execute(() -> {
            String done;
            try {
                if (trash()) com.sun.jna.platform.FileUtils.getInstance().moveToTrash(new File[] {file});
                else Files.deleteIfExists(file.toPath());
                done = file.exists() ? file.getName() + " could not be deleted." : (trash() ? "Moved " : "Deleted ") + file.getName();
            } catch (IOException | RuntimeException | LinkageError failed) {
                done = file.getName() + " could not be deleted: " + failed.getMessage();
            }
            String message = done;
            mc().addScheduledTask(() -> {
                status = message;
                if (file.equals(previewFile)) releasePreview();
                reload(null);
            });
        });
    }

    /** JNA's FileUtils: the Recycle Bin on Windows, the Trash on macOS and where Linux desktops keep ~/.Trash. */
    private static boolean trash() {
        try { return com.sun.jna.platform.FileUtils.getInstance().hasTrash(); } catch (Throwable unavailable) { return false; }
    }

    private void open(File file) {
        Screenshots189.IO.execute(() -> {
            try {
                Desktop.getDesktop().open(file);
            } catch (Throwable noDesktop) {
                Sys.openURL(file.toURI().toString());
            }
        });
    }

    private void copy(File file) {
        status = "Copying " + file.getName() + "...";
        Screenshots189.IO.execute(() -> {
            String done;
            try {
                BufferedImage image = Screenshots189.read(file, 16384);
                BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D graphics = rgb.createGraphics();
                try { graphics.drawImage(image, 0, 0, null); } finally { graphics.dispose(); }
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new Transferable() {
                    @Override public DataFlavor[] getTransferDataFlavors() { return new DataFlavor[] {DataFlavor.imageFlavor}; }
                    @Override public boolean isDataFlavorSupported(DataFlavor flavor) { return DataFlavor.imageFlavor.equals(flavor); }
                    @Override public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                        if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);
                        return rgb;
                    }
                }, null);
                done = "Copied " + file.getName() + " to the clipboard";
            } catch (Throwable failed) {
                done = file.getName() + " could not be copied: " + failed.getMessage();
            }
            String message = done;
            mc().addScheduledTask(() -> status = message);
        });
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) mc.displayGuiScreen(parent);
        else if (keyCode == Keyboard.KEY_DELETE && selectedFile() != null) actionPerformed(buttonList.get(DELETE));
        else if ((keyCode == Keyboard.KEY_UP || keyCode == Keyboard.KEY_DOWN) && !files.isEmpty())
            select(Math.max(0, Math.min(files.size() - 1, selected + (keyCode == Keyboard.KEY_UP ? -1 : 1))));
        else if (keyCode == Keyboard.KEY_RETURN && selectedFile() != null) open(selectedFile());
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        list.handleMouseInput();
    }

    @Override
    public void onGuiClosed() {
        // The delete prompt comes back to this screen; leaving it otherwise frees the preview.
        if (!confirming) releasePreview();
        confirming = false;
    }

    private void releasePreview() {
        if (preview != null) mc().getTextureManager().deleteTexture(preview);
        preview = null;
        previewFile = null;
    }

    private static Minecraft mc() { return Minecraft.getMinecraft(); }

    private final class FileList extends GuiSlot {
        FileList() {
            super(ScreenshotsScreen189.this.mc, listWidth, ScreenshotsScreen189.this.height, 32, ScreenshotsScreen189.this.height - 48, 22);
            setSlotXBoundsFromLeft(8);
        }

        @Override protected int getSize() { return files.size(); }
        @Override protected boolean isSelected(int index) { return index == selected; }
        @Override protected void drawBackground() {}
        @Override public int getListWidth() { return width - 12; }
        @Override protected int getScrollBarX() { return left + width - 6; }

        @Override
        protected void elementClicked(int index, boolean doubleClick, int mouseX, int mouseY) {
            select(index);
            if (doubleClick && selectedFile() != null) open(selectedFile());
        }

        @Override
        protected void drawSlot(int index, int x, int y, int slotHeight, int mouseX, int mouseY) {
            File file = files.get(index);
            fontRendererObj.drawString(fontRendererObj.trimStringToWidth(file.getName(), getListWidth() - 8), x + 2, y + 1, 0xFFFFFF);
            fontRendererObj.drawString(DATE.format(new Date(file.lastModified())), x + 2, y + 11, 0x808080);
        }
    }
}
