package com.thelads.core.v1_21_11.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;

public final class Screenshots12111 {
    private Screenshots12111() {}

    public record ScreenshotChatActionClickEvent(File screenshotFile, ActionType actionType) implements ClickEvent {
        public enum ActionType {
            COPY, OPEN_FILE, OPEN_FOLDER, UPLOAD_IMGUR
        }

        @Override
        public Action action() {
            return Action.OPEN_URL;
        }
    }

    public static void open(Screen parent) {
        Minecraft mc = Minecraft.getInstance();
        try {
            Screen gallery = (Screen) Class.forName("io.github.lgatodu47.screenshot_viewer.screen.manage_screenshots.ManageScreenshotsScreen")
                .getConstructor(Screen.class)
                .newInstance(parent);
            mc.setScreen(gallery);
            return;
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        File dir = new File(mc.gameDirectory, "screenshots");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        Util.getPlatform().openFile(dir);
    }

    public static MutableComponent appendScreenshotButtons(MutableComponent base, File file) {
        String existing = base.getString();
        if (existing.contains("[Copy]") && existing.contains("[Upload to Imgur]")) {
            return base;
        }
        var copyBtn = Component.literal(" [Copy]")
            .withStyle(s -> s.withColor(ChatFormatting.GOLD)
                .withClickEvent(new ScreenshotChatActionClickEvent(file, ScreenshotChatActionClickEvent.ActionType.COPY))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Copy image to clipboard"))));

        var openFileBtn = Component.literal(" [Open File]")
            .withStyle(s -> s.withColor(ChatFormatting.GREEN)
                .withClickEvent(new ScreenshotChatActionClickEvent(file, ScreenshotChatActionClickEvent.ActionType.OPEN_FILE))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Open screenshot file"))));

        var openFolderBtn = Component.literal(" [Open Folder]")
            .withStyle(s -> s.withColor(ChatFormatting.AQUA)
                .withClickEvent(new ScreenshotChatActionClickEvent(file, ScreenshotChatActionClickEvent.ActionType.OPEN_FOLDER))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Open screenshots folder"))));

        var imgurBtn = Component.literal(" [Upload to Imgur]")
            .withStyle(s -> s.withColor(ChatFormatting.LIGHT_PURPLE)
                .withClickEvent(new ScreenshotChatActionClickEvent(file, ScreenshotChatActionClickEvent.ActionType.UPLOAD_IMGUR))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Upload to Imgur and copy link"))));

        return base.append(copyBtn).append(openFileBtn).append(openFolderBtn).append(imgurBtn);
    }

    public static void handleClick(ScreenshotChatActionClickEvent ae) {
        File file = ae.screenshotFile();
        switch (ae.actionType()) {
            case COPY -> copyImageToClipboard(file);
            case OPEN_FILE -> Util.getPlatform().openFile(file);
            case OPEN_FOLDER -> Util.getPlatform().openFile(file.getParentFile());
            case UPLOAD_IMGUR -> uploadToImgur(file);
        }
    }

    public static void copyImageToClipboard(File screenshotFile) {
        Minecraft client = Minecraft.getInstance();
        CompletableFuture.runAsync(() -> {
            try {
                BufferedImage image = ImageIO.read(screenshotFile);
                if (image == null) {
                    throw new IllegalStateException("Could not decode image");
                }
                BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgb.createGraphics();
                try {
                    g.drawImage(image, 0, 0, null);
                } finally {
                    g.dispose();
                    image.flush();
                }
                Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                clipboard.setContents(new ImageTransferable(rgb), null);
                client.execute(() -> {
                    if (client.gui != null) {
                        client.gui.setOverlayMessage(Component.literal("Screenshot copied to clipboard"), false);
                    }
                });
            } catch (Throwable t) {
                client.execute(() -> {
                    if (client.gui != null) {
                        client.gui.setOverlayMessage(Component.literal("Failed to copy screenshot: " + t.getMessage()), false);
                    }
                });
            }
        });
    }

    public static void uploadToImgur(File screenshotFile) {
        Minecraft client = Minecraft.getInstance();
        if (client.gui != null) {
            client.gui.setOverlayMessage(Component.literal("Uploading screenshot to Imgur..."), false);
        }
        CompletableFuture.runAsync(() -> {
            try {
                byte[] bytes = Files.readAllBytes(screenshotFile.toPath());
                HttpURLConnection conn = (HttpURLConnection) new URI("https://api.imgur.com/3/image").toURL().openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Client-ID 546c25a59c58ad7");
                conn.setDoOutput(true);
                conn.getOutputStream().write(bytes);
                conn.getOutputStream().flush();
                int code = conn.getResponseCode();
                if (code == 200) {
                    try (var reader = new InputStreamReader(conn.getInputStream())) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        String link = json.getAsJsonObject("data").get("link").getAsString();
                        client.execute(() -> {
                            client.keyboardHandler.setClipboard(link);
                            if (client.gui != null) {
                                client.gui.setOverlayMessage(Component.literal("Imgur link copied: " + link), false);
                            }
                        });
                    }
                } else {
                    client.execute(() -> {
                        if (client.gui != null) {
                            client.gui.setOverlayMessage(Component.literal("Imgur upload failed: HTTP " + code), false);
                        }
                    });
                }
            } catch (Throwable t) {
                client.execute(() -> {
                    if (client.gui != null) {
                        client.gui.setOverlayMessage(Component.literal("Imgur upload failed: " + t.getMessage()), false);
                    }
                });
            }
        });
    }

    private record ImageTransferable(Image image) implements Transferable {
        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{DataFlavor.imageFlavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return DataFlavor.imageFlavor.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return image;
        }
    }
}
