package com.thelads.core.v1_8_9.feature;

import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.thelads.core.client.util.LocalSkinSource;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ImageBufferDownload;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;

/**
 * The Skin changer's skin on 1.8.9 (LocalSkins1201 and LocalSkins on the other versions): a username, UUID, skin URL or PNG file
 * becomes this client's own skin, kept in config/lads-skin (skin.png and model.txt) and worn through LocalSkinMixin189. Network and
 * disk work never runs on the game thread.
 */
public final class LocalSkins189 {
    /** The selected local skin: its texture and whether it uses the slim (3 pixel arm) model. */
    public static final class Skin {
        public final ResourceLocation texture;
        public final boolean slim;

        Skin(ResourceLocation texture, boolean slim) {
            this.texture = texture;
            this.slim = slim;
        }
    }

    private static volatile Skin selected;
    private static volatile int revision;
    private static final Object STATE = new Object();
    private static boolean initialized, accountAsked;
    private static volatile ResourceLocation accountSkin;
    private static volatile boolean accountSlim;

    private LocalSkins189() {}

    public static Skin current() {
        return selected;
    }

    /** The saved skin goes on once, when the game starts. */
    public static void initialize() {
        if (initialized) return;
        initialized = true;
        File file = new File(folder(), "skin.png");
        if (file.isFile()) load(file.getPath(), readSlim(), false).whenComplete((message, failure) -> {
            if (failure != null) LogManager.getLogger("TheLadsCore").warn("Could not load the saved local skin", failure);
        });
    }

    private static File folder() {
        return new File(Minecraft.getMinecraft().mcDataDir, "config/lads-skin");
    }

    private static boolean readSlim() {
        try {
            return new String(Files.readAllBytes(new File(folder(), "model.txt").toPath()), StandardCharsets.UTF_8).trim().equals("slim");
        } catch (IOException none) {
            return false;
        }
    }

    /** Makes the skin of {@code input} this client's own; the future completes with a message for the player, or the reason it failed. */
    public static CompletableFuture<String> load(String input, boolean slim, boolean persist) {
        CompletableFuture<String> result = new CompletableFuture<>();
        final int request;
        synchronized (STATE) {
            request = ++revision;
        }
        CompletableFuture.runAsync(() -> {
            try {
                LocalSkinSource.Skin source = LocalSkinSource.resolve(input, slim);
                LocalSkinSource.validate(source.png());
                BufferedImage read = ImageIO.read(new ByteArrayInputStream(source.png()));
                if (read == null) throw new IOException("Select a PNG skin");
                BufferedImage image = new ImageBufferDownload().parseUserSkin(read); // vanilla's: the old 64 x 32 layout becomes 64 x 64
                boolean model = source.slim();
                synchronized (STATE) {
                    if (request != revision) {
                        result.complete("A newer skin was selected");
                        return;
                    }
                    if (persist) save(image, model);
                }
                Minecraft.getMinecraft().addScheduledTask(() -> {
                    if (request != revision) {
                        result.complete("A newer skin was selected");
                        return;
                    }
                    try {
                        apply(image, model, request);
                        result.complete("Skin applied locally");
                    } catch (RuntimeException failure) {
                        result.completeExceptionally(failure);
                    }
                });
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        return result;
    }

    private static void save(BufferedImage image, boolean slim) throws IOException {
        Path folder = folder().toPath();
        Files.createDirectories(folder);
        Path temporary = Files.createTempFile(folder, "skin-", ".png");
        try {
            ImageIO.write(image, "png", temporary.toFile());
            Files.move(temporary, folder.resolve("skin.png"), StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        Files.write(folder.resolve("model.txt"), (slim ? "slim" : "wide").getBytes(StandardCharsets.UTF_8));
    }

    /** On the game thread: the skin becomes a texture and the one before it goes. */
    private static void apply(BufferedImage image, boolean slim, int request) {
        Minecraft mc = Minecraft.getMinecraft();
        ResourceLocation id = new ResourceLocation("theladscore", "local_skin/" + request);
        mc.getTextureManager().loadTexture(id, new DynamicTexture(image));
        Skin old = selected;
        selected = new Skin(id, slim);
        if (old != null) mc.getTextureManager().deleteTexture(old.texture);
        refreshLayers();
    }

    /** Back to the account's own skin. */
    public static void reset() {
        Skin old;
        synchronized (STATE) {
            old = selected;
            selected = null;
            revision++;
            try {
                Files.deleteIfExists(new File(folder(), "skin.png").toPath());
            } catch (IOException failure) {
                LogManager.getLogger("TheLadsCore").warn("Could not reset the saved local skin", failure);
            }
        }
        if (old != null) Minecraft.getMinecraft().getTextureManager().deleteTexture(old.texture);
        refreshLayers();
    }

    /** The selected skin takes the other arm model. */
    public static void setModel(boolean slim) {
        final int request;
        synchronized (STATE) {
            if (selected == null) return;
            request = revision;
            selected = new Skin(selected.texture, slim);
        }
        refreshLayers();
        CompletableFuture.runAsync(() -> {
            synchronized (STATE) {
                if (request != revision || selected == null || selected.slim != slim) return;
                try {
                    Files.createDirectories(folder().toPath());
                    Files.write(new File(folder(), "model.txt").toPath(), (slim ? "slim" : "wide").getBytes(StandardCharsets.UTF_8));
                } catch (IOException failure) {
                    LogManager.getLogger("TheLadsCore").warn("Could not save the skin model", failure);
                }
            }
        });
    }

    /** 3D Skin Layers builds its layers from the skin the player wears: it makes them again for the new one. */
    private static void refreshLayers() {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player != null) SkinLayers189.refresh(player);
    }

    /** The signed-in account's own skin: in a world the player's, else Mojang's (the default skin until it loads, and for accounts without one). */
    public static ResourceLocation accountSkin() {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player != null && selected == null) return ((net.minecraft.client.entity.AbstractClientPlayer) player).getLocationSkin();
        askAccount();
        ResourceLocation skin = accountSkin;
        return skin != null ? skin : DefaultPlayerSkin.getDefaultSkin(accountId());
    }

    public static boolean accountSlim() {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player != null && selected == null) return "slim".equals(((net.minecraft.client.entity.AbstractClientPlayer) player).getSkinType());
        askAccount();
        return accountSkin != null ? accountSlim : "slim".equals(DefaultPlayerSkin.getSkinType(accountId()));
    }

    private static java.util.UUID accountId() {
        return EntityPlayer.getUUID(Minecraft.getMinecraft().getSession().getProfile());
    }

    /** Asks Mojang for the account's skin once; vanilla loads it into the texture manager and calls back on the game thread. */
    private static void askAccount() {
        if (accountAsked) return;
        accountAsked = true;
        Minecraft mc = Minecraft.getMinecraft();
        mc.getSkinManager().loadProfileTextures(mc.getSession().getProfile(), (type, location, texture) -> {
            if (type != MinecraftProfileTexture.Type.SKIN) return;
            accountSlim = "slim".equals(texture.getMetadata("model"));
            accountSkin = location;
        }, true);
    }
}
