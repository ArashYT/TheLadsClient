// Ported from Capes 1.5.4+1.21 by Cael (LGPL-2.1-only) from Kotlin to Java; modified by The Lads: remapped to Mojang mappings and repackaged into Lads Core.
package com.thelads.core.v1_21_1.embedded.capes.handler;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.v1_21_1.embedded.capes.CapeConfig;
import com.thelads.core.v1_21_1.embedded.capes.CapeType;
import com.thelads.core.v1_21_1.embedded.capes.Capes;
import com.thelads.core.v1_21_1.embedded.capes.handler.data.MCMData;
import com.thelads.core.v1_21_1.embedded.capes.handler.data.WynntilsData;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.codec.binary.Base64;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlayerHandler {
    private static final HashMap<UUID, PlayerHandler> instances = new HashMap<>();
    private static final ExecutorService capeExecutor = Executors.newFixedThreadPool(2);

    private final GameProfile profile;
    private final UUID uuid;
    private int lastFrame = 0;
    private int maxFrames = 0;
    private long lastFrameTime = 0L;
    private boolean hasCape = false;
    private boolean hasElytraTexture = true;
    private boolean hasAnimatedCape = false;
    private CapeType capeType = null;

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
        if (Objects.equals(profile, Minecraft.getInstance().getGameProfile())) {
            playerHandler.hasCape = false;
            playerHandler.hasAnimatedCape = false;
            CapeConfig config = Capes.getConfig();
            capeExecutor.submit(() -> playerHandler.setCape(config.getClientCapeType()));
        } else {
            capeExecutor.submit(() -> {
                if (profile.getId().toString().equals("5f91fdfd-ea97-473c-bb77-c8a2a0ed3af9")) {
                    playerHandler.setStandardCape(connection("https://athena.wynntils.com/capes/user/" + profile.getId()));
                    return null;
                }
                for (CapeType capeType : CapeType.values()) {
                    if (playerHandler.setCape(capeType)) break;
                }
                return null;
            });
        }
    }

    @SuppressWarnings("deprecation") // new URL(String), as upstream
    public static HttpURLConnection connection(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection(Minecraft.getInstance().getProxy());
        connection.addRequestProperty("User-Agent", "Mozilla/4.0");
        connection.setDoInput(true);
        connection.setDoOutput(false);
        return connection;
    }

    public ResourceLocation getCape() {
        if (!hasAnimatedCape) return Capes.identifier(uuid.toString());
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

    public boolean setCape(CapeType capeType) throws IOException {
        String capeURL = capeType.getURL(profile);
        if (capeURL == null) return false;
        HttpURLConnection connection = connection(capeURL);

        boolean result = switch (capeType) {
            case WYNNTILS -> setWynntilsCape(connection);
            case MINECRAFTCAPES -> setMCMCape(connection);
            default -> setStandardCape(connection);
        };
        if (result) this.capeType = capeType;
        return result;
    }

    public boolean setStandardCape(HttpURLConnection connection) throws IOException {
        connection.connect();
        if (connection.getResponseCode() / 100 == 2) {
            return setCapeTexture(connection.getInputStream(), false);
        }
        return false;
    }

    public boolean setWynntilsCape(HttpURLConnection connection) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("uuid", uuid.toString());
        byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");
        connection.addRequestProperty("Content-Type", "application/json");
        connection.addRequestProperty("Content-Length", String.valueOf(data.length));
        OutputStream o = connection.getOutputStream();
        o.write(data);
        o.flush();
        connection.connect();
        if (connection.getResponseCode() / 100 == 2) {
            Reader reader = new InputStreamReader(connection.getInputStream(), "UTF-8");
            JsonElement cosmetics = JsonParser.parseReader(reader).getAsJsonObject().get("user").getAsJsonObject().get("cosmetics");
            WynntilsData result = new Gson().fromJson(cosmetics, WynntilsData.class);
            return this.setCapeTextureFromBase64(result.texture, false);
        }
        return false;
    }

    public boolean setMCMCape(HttpURLConnection connection) throws IOException {
        connection.connect();
        if (connection.getResponseCode() / 100 == 2) {
            Reader reader = new InputStreamReader(connection.getInputStream(), "UTF-8");
            MCMData result = new Gson().fromJson(reader, MCMData.class);
            return setCapeTextureFromBase64(result.textures.get("cape"), result.animatedCape);
        }
        return false;
    }

    public boolean setCapeTextureFromBase64(String base64Texture, boolean animated) {
        if (base64Texture == null) return false;
        byte[] bytes = Base64.decodeBase64(base64Texture);
        return setCapeTexture(new ByteArrayInputStream(bytes), animated);
    }

    public boolean setCapeTexture(InputStream image, boolean animated) {
        try {
            NativeImage cape = NativeImage.read(image);
            Minecraft.getInstance().submit(() -> {
                if (animated) {
                    Int2ObjectOpenHashMap<NativeImage> animatedCapeFrames = parseAnimatedCape(cape);
                    for (Int2ObjectMap.Entry<NativeImage> entry : animatedCapeFrames.int2ObjectEntrySet()) {
                        int frame = entry.getIntKey();
                        Minecraft.getInstance().getTextureManager().register(
                                Capes.identifier(uuid + "/" + frame), new DynamicTexture(entry.getValue())
                        );
                    }
                    this.maxFrames = animatedCapeFrames.size();
                    this.hasCape = true;
                    this.hasAnimatedCape = true;
                } else {
                    this.hasElytraTexture = Math.floorDiv(cape.getWidth(), cape.getHeight()) == 2;
                    Minecraft.getInstance().getTextureManager().register(
                            Capes.identifier(uuid.toString()), new DynamicTexture(parseCape(cape))
                    );
                    this.hasCape = true;
                }
            });
            return true;
        } catch (IOException ioException) {
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
        int totalFrames = img.getHeight() / (img.getWidth() / 2);
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
