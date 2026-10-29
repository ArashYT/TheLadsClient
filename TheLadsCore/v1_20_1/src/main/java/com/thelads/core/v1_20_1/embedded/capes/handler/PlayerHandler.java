// Ported from Capes 1.5.10+26.3 by Cael (LGPL-2.1-only) from Kotlin to Java; modified by The Lads: ported to Minecraft 1.20.1 from the Lads 26.3 embedded copy.
package com.thelads.core.v1_20_1.embedded.capes.handler;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.v1_20_1.embedded.capes.CapeConfig;
import com.thelads.core.v1_20_1.embedded.capes.CapeType;
import com.thelads.core.v1_20_1.embedded.capes.Capes;
import com.thelads.core.v1_20_1.embedded.capes.handler.data.CosmeticaData;
import com.thelads.core.v1_20_1.embedded.capes.handler.data.MCMData;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.thelads.core.client.CapeImageGuard;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlayerHandler {
    // Read from the render thread (every frame, per player) and written when a profile is first seen.
    private static final Map<UUID, PlayerHandler> instances = new ConcurrentHashMap<>();
    private static final int TIMEOUT_MS = 5000;
    // 1.20.1 runs on Java 17: no virtual threads, so one short-lived daemon thread per download (cached pool).
    private static final ExecutorService capeExecutor = Executors.newCachedThreadPool(task -> {
        Thread thread = new Thread(task, "Capes download");
        thread.setDaemon(true);
        return thread;
    });

    private final GameProfile profile;
    private final UUID uuid;
    private volatile int lastFrame = 0;
    private int maxFrames = 0;
    private long lastFrameTime = 0L;
    private volatile boolean hasCape = false;
    private volatile boolean hasElytraTexture = true;
    private volatile boolean hasAnimatedCape = false;
    private volatile CapeType capeType = null;
    private boolean hasLoadedTextures = false;

    public PlayerHandler(GameProfile profile) {
        this.profile = profile;
        this.uuid = profile.getId();
        instances.put(uuid, this);
    }

    public boolean getHasCape() {
        return hasCape;
    }

    public boolean getHasElytraTexture() {
        return hasElytraTexture;
    }

    public boolean getHasAnimatedCape() {
        return hasAnimatedCape;
    }

    public CapeType getCapeType() {
        return capeType;
    }

    public static PlayerHandler fromProfile(GameProfile profile) {
        PlayerHandler handler = instances.get(profile.getId());
        return handler != null ? handler : new PlayerHandler(profile);
    }

    public static void onLoadTexture(GameProfile profile) {
        PlayerHandler playerHandler = fromProfile(profile);
        if (Objects.equals(profile, Minecraft.getInstance().getUser().getGameProfile())) {
            playerHandler.hasCape = false;
            playerHandler.hasAnimatedCape = false;
            CapeConfig config = Capes.getConfig();
            capeExecutor.submit(() -> playerHandler.setCape(config.getClientCapeType()));
        } else if (!playerHandler.hasLoadedTextures) {
            capeExecutor.submit(() -> {
                for (CapeType capeType : CapeType.values()) {
                    if (playerHandler.setCape(capeType)) break;
                }
                return null;
            });
            playerHandler.hasLoadedTextures = true;
        }
    }

    public static HttpURLConnection connection(String url) throws IOException, URISyntaxException {
        HttpURLConnection connection = (HttpURLConnection) new URI(url).toURL().openConnection(Minecraft.getInstance().getProxy());
        connection.addRequestProperty("User-Agent", "Mozilla/5.0");
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        connection.setDoInput(true);
        connection.setDoOutput(false);
        return connection;
    }

    /** Texture of the cape (the current frame of an animated one); only meaningful while {@link #getHasCape()}. */
    public ResourceLocation getCape() {
        if (!hasAnimatedCape || maxFrames <= 0) return Capes.identifier(uuid.toString());
        long time = System.currentTimeMillis();
        if (time > this.lastFrameTime + 100L) {
            int thisFrame = (this.lastFrame + 1) % this.maxFrames;
            this.lastFrame = thisFrame;
            this.lastFrameTime = time;
            return Capes.identifier(uuid + "/" + thisFrame);
        } else {
            return Capes.identifier(uuid + "/" + this.lastFrame);
        }
    }

    /** Marks the cape lookups of this player as done, so no provider is asked (QA probe and tests that supply the texture themselves). */
    public void skipLookup() {
        this.hasLoadedTextures = true;
    }

    public boolean setCape(CapeType capeType) throws IOException, URISyntaxException {
        String capeURL = capeType.getURL(profile);
        if (capeURL == null) return false;
        HttpURLConnection connection = connection(capeURL);

        boolean result = switch (capeType) {
            case LABYMOD -> setStandardCape(connection, true, false);
            case COSMETICA -> setCosmeticaCape(connection);
            case MINECRAFTCAPES -> setMCMCape(connection);
            default -> setStandardCape(connection, false, false);
        };
        if (result) this.capeType = capeType;
        return result;
    }

    public boolean setStandardCape(HttpURLConnection connection, boolean labymod, boolean animated) throws IOException {
        try {
            connection.connect();
            if (connection.getResponseCode() / 100 == 2) {
                try (InputStream stream = connection.getInputStream()) {
                    byte[] png = CapeImageGuard.readPng(stream, animated);
                    if (png != null) {
                        return setCapeTexture(new ByteArrayInputStream(png), animated, labymod);
                    }
                }
            }
            return false;
        } finally {
            connection.disconnect();
        }
    }

    public boolean setCosmeticaCape(HttpURLConnection connection) throws IOException, URISyntaxException {
        String json;
        try {
            connection.connect();
            if (connection.getResponseCode() / 100 != 2) return false;
            try (InputStream stream = connection.getInputStream()) {
                json = CapeImageGuard.readJson(stream);
            }
        } finally {
            connection.disconnect();
        }
        if (json == null) return false;
        CosmeticaData result = new Gson().fromJson(json, CosmeticaData.class);
        if (result == null || result.cloak() == null || result.cloak().texture() == null) return false;
        String texture = result.cloak().texture();
        if (!CapeImageGuard.isHttps(texture)) return false;
        HttpURLConnection textureConnection = connection(texture);
        return setStandardCape(textureConnection, false, result.cloak().isAnimated());
    }

    public boolean setMCMCape(HttpURLConnection connection) throws IOException, URISyntaxException {
        String json;
        try {
            connection.connect();
            if (connection.getResponseCode() / 100 != 2) return false;
            try (InputStream stream = connection.getInputStream()) {
                json = CapeImageGuard.readJson(stream);
            }
        } finally {
            connection.disconnect();
        }
        if (json == null) return false;
        MCMData profile = new Gson().fromJson(json, MCMData.class);
        if (profile == null || profile.cape_url() == null || !CapeImageGuard.isHttps(profile.cape_url())) return false;

        HttpURLConnection result = connection(profile.cape_url());
        return setStandardCape(result, false, profile.animated_cape_url() != null);
    }

    public boolean setCapeTexture(InputStream image, boolean animated, boolean labymod) {
        try {
            NativeImage cape = NativeImage.read(image);
            if (labymod && UUIDUtil.uuidFromIntArray(cape.getPixelsRGBA()).toString().equals("ff305f81-ff30-5f90-ff30-5f90ff305f90")) {
                cape.close();
                return false;
            }
            Minecraft.getInstance().execute(() -> {
                if (animated) {
                    Int2ObjectOpenHashMap<NativeImage> animatedCapeFrames = parseAnimatedCape(cape);
                    if (!animatedCapeFrames.isEmpty()) {
                        for (Int2ObjectMap.Entry<NativeImage> entry : animatedCapeFrames.int2ObjectEntrySet()) {
                            int frame = entry.getIntKey();
                            Minecraft.getInstance().getTextureManager().register(
                                    Capes.identifier(uuid + "/" + frame), new DynamicTexture(entry.getValue())
                            );
                        }
                        this.maxFrames = animatedCapeFrames.size();
                        this.hasCape = true;
                        this.hasAnimatedCape = true;
                        cape.close();
                        return;
                    }
                }
                this.hasElytraTexture = Math.floorDiv(cape.getWidth(), cape.getHeight()) == 2;
                Minecraft.getInstance().getTextureManager().register(
                        Capes.identifier(uuid.toString()), new DynamicTexture(parseCape(cape))
                );
                this.hasCape = true;
            });
            return true;
        } catch (Exception exception) {
            exception.printStackTrace();
            return false;
        }
    }

    private NativeImage parseCape(NativeImage img) {
        int imageWidth = 64;
        int imageHeight = 32;
        int srcWidth = img.getWidth();
        int srcHeight = img.getHeight();
        while (imageWidth < srcWidth || imageHeight < srcHeight) {
            imageWidth *= 2;
            imageHeight *= 2;
        }
        NativeImage imgNew = new NativeImage(imageWidth, imageHeight, true);
        for (int x = 0; x < srcWidth; x++) {
            for (int y = 0; y < srcHeight; y++) {
                imgNew.setPixelRGBA(x, y, img.getPixelRGBA(x, y));
            }
        }
        img.close();
        return imgNew;
    }

    private Int2ObjectOpenHashMap<NativeImage> parseAnimatedCape(NativeImage img) {
        Int2ObjectOpenHashMap<NativeImage> animatedCape = new Int2ObjectOpenHashMap<>();
        int totalFrames = CapeImageGuard.frameCount(img.getWidth(), img.getHeight());
        for (int currentFrame = 0; currentFrame < totalFrames; currentFrame++) {
            NativeImage frame = new NativeImage(img.getWidth(), img.getWidth() / 2, true);
            for (int x = 0; x < frame.getWidth(); x++) {
                for (int y = 0; y < frame.getHeight(); y++) {
                    frame.setPixelRGBA(x, y, img.getPixelRGBA(x, y + (currentFrame * (img.getWidth() / 2))));
                }
            }
            animatedCape.put(currentFrame, frame);
        }
        return animatedCape;
    }

}
