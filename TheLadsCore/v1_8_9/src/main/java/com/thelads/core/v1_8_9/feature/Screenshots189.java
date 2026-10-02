package com.thelads.core.v1_8_9.feature;

import com.thelads.core.config.ActionOption;
import com.thelads.core.v1_8_9.gui.ScreenshotsScreen189;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * BetterScreenshots on 1.8.9 (26.x: ScreenshotViewer): the gallery (ScreenshotsScreen189) opens with its key (F10) in gameplay,
 * on the title screen and the pause menu, or from the module's "Browse Screenshots"; a screenshot taken with the screenshot
 * key shows a preview for 3 seconds (1.8.9 has no toasts; not under F1, so F1 shots stay clean).
 */
public final class Screenshots189 {
    public static final KeyBinding OPEN = new KeyBinding("key.theladscore.screenshots", Keyboard.KEY_F10, "key.category.theladscore.controls");
    /** Image decoding, clipboard and file actions, off the render thread (26.x: ScreenshotFileIO.IMAGE_EXECUTOR). */
    public static final ExecutorService IO = Executors.newSingleThreadExecutor(task -> {
        Thread worker = new Thread(task, "Lads screenshot IO");
        worker.setDaemon(true);
        return worker;
    });
    private static final long PREVIEW_NANOS = 3_000_000_000L;
    private static ResourceLocation preview;
    private static String previewName;
    private static int previewWidth, previewHeight;
    private static long previewStart;
    /** QA only: the last screenshot the screenshot key saved. */
    static File lastSaved;

    public static void register() {
        ClientRegistry.registerKeyBinding(OPEN);
        ActionOption browse = new ActionOption("Browse Screenshots", "Open gallery");
        browse.setAction(() -> open(Minecraft.getMinecraft().currentScreen));
        Options189.module("BetterScreenshots").addOption(browse);
        MinecraftForge.EVENT_BUS.register(new Screenshots189());
    }

    public static boolean active() { return Options189.enabled("BetterScreenshots"); }

    public static boolean open(GuiScreen parent) {
        if (!active()) return false;
        Minecraft.getMinecraft().displayGuiScreen(new ScreenshotsScreen189(parent));
        return true;
    }

    public static File folder() { return new File(Minecraft.getMinecraft().mcDataDir, "screenshots"); }

    /** ScreenShotHelperMixin, on the client thread: the screenshot key's result, "screenshot.success" with the file's link. */
    public static void saved(IChatComponent result) {
        if (!(result instanceof ChatComponentTranslation) || !"screenshot.success".equals(((ChatComponentTranslation) result).getKey())) return;
        Object[] args = ((ChatComponentTranslation) result).getFormatArgs();
        ClickEvent link = args.length > 0 && args[0] instanceof IChatComponent ? ((IChatComponent) args[0]).getChatStyle().getChatClickEvent() : null;
        if (link == null || link.getAction() != ClickEvent.Action.OPEN_FILE) return;
        File file = new File(link.getValue());
        lastSaved = file;
        if (!active()) return;
        IO.execute(() -> {
            try {
                BufferedImage image = read(file, 256);
                Minecraft.getMinecraft().addScheduledTask(() -> showPreview(file, image));
            } catch (IOException | RuntimeException unreadable) {
                // The chat line still links the file; only the preview is skipped.
            }
        });
    }

    private static void showPreview(File file, BufferedImage image) {
        clearPreview();
        preview = texture("lads_screenshot_preview", image);
        previewName = file.getName();
        previewWidth = image.getWidth();
        previewHeight = image.getHeight();
        previewStart = System.nanoTime();
    }

    private static void clearPreview() {
        if (preview != null) Minecraft.getMinecraft().getTextureManager().deleteTexture(preview);
        preview = null;
    }

    /** Over everything drawn this frame, top right. */
    @SubscribeEvent
    public void frame(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || preview == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (System.nanoTime() - previewStart > PREVIEW_NANOS || !active()) { clearPreview(); return; }
        if (mc.gameSettings.hideGUI && mc.currentScreen == null) return;
        mc.entityRenderer.setupOverlayRendering();
        ScaledResolution resolution = new ScaledResolution(mc);
        FontRenderer font = mc.fontRendererObj;
        int width = 112, height = Math.max(1, Math.min(84, previewHeight * width / Math.max(1, previewWidth)));
        int x = resolution.getScaledWidth() - width - 8, y = 8;
        Gui.drawRect(x - 4, y - 4, x + width + 4, y + height + 26, 0xC0101010);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
        mc.getTextureManager().bindTexture(preview);
        Gui.drawModalRectWithCustomSizedTexture(x, y, 0, 0, width, height, width, height);
        font.drawStringWithShadow("Screenshot saved", x, y + height + 4, 0xFFFFFF);
        font.drawStringWithShadow(font.trimStringToWidth(previewName, width), x, y + height + 14, 0xAAAAAA);
    }

    @SubscribeEvent
    public void gameplayKey(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        while (OPEN.isPressed()) if (Minecraft.getMinecraft().currentScreen == null) open(null);
    }

    /** KeyBindings count presses only in gameplay: on the title screen and the pause menu the key is read from the screen's input. */
    @SubscribeEvent
    public void screenKey(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!Keyboard.getEventKeyState() || Keyboard.isRepeatEvent() || Keyboard.getEventKey() != OPEN.getKeyCode() || OPEN.getKeyCode() == 0) return;
        if ((event.gui instanceof GuiMainMenu || event.gui instanceof GuiIngameMenu) && open(event.gui)) event.setCanceled(true);
    }

    /** A dynamic texture of image with linear filtering (previews are drawn smaller than the image). */
    public static ResourceLocation texture(String name, BufferedImage image) {
        ResourceLocation location = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation(name, new DynamicTexture(image));
        Minecraft.getMinecraft().getTextureManager().bindTexture(location);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        return location;
    }

    public static boolean image(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return file.isFile() && (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg"));
    }

    /** A screenshot decoded at most maxEdge pixels wide or high, bounded like 26.x ScreenshotFileIO.read. */
    public static BufferedImage read(File file, int maxEdge) throws IOException {
        if (!image(file) || file.length() > 256L * 1024 * 1024) throw new IOException("Unsupported screenshot file or file exceeds 256 MiB");
        try (ImageInputStream input = ImageIO.createImageInputStream(file)) {
            if (input == null) throw new IOException("Unreadable image");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Unsupported image format");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > 16384 || height > 16384 || (long) width * height > 64_000_000L)
                    throw new IOException("Image dimensions exceed the gallery limit");
                ImageReadParam params = reader.getDefaultReadParam();
                int sample = Math.max(1, (Math.max(width, height) + maxEdge - 1) / maxEdge);
                params.setSourceSubsampling(sample, sample, 0, 0);
                return reader.read(0, params);
            } finally {
                reader.dispose();
            }
        }
    }
}
