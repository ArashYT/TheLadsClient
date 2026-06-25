package com.thelads.core.client.capes;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.thelads.core.client.capes.handler.CosmeticaData;
import com.thelads.core.client.capes.handler.MCMData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.ClientAsset;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import org.apache.commons.codec.binary.Base64;

public final class PlayerHandler {
    private static final Map<UUID, PlayerHandler> instances = new HashMap<>();
    private static final ExecutorService capeExecutor = Executors.newCachedThreadPool();

    private GameProfile profile;
    private final UUID uuid;
    private int lastFrame;
    private int maxFrames;
    private long lastFrameTime;
    private boolean hasCape;
    private boolean hasElytraTexture;
    private boolean hasAnimatedCape;
    private CapeType capeType;
    private boolean hasLoadedTextures;

    public PlayerHandler(GameProfile profile) {
        this.profile = profile;
        this.uuid = this.profile.id();
        this.hasElytraTexture = true;
        instances.put(this.uuid, this);
    }

    public GameProfile getProfile() {
        return this.profile;
    }

    public void setProfile(GameProfile gameProfile) {
        if (gameProfile != null) {
            this.profile = gameProfile;
        }
    }

    public UUID getUuid() {
        return this.uuid;
    }

    public int getLastFrame() {
        return this.lastFrame;
    }

    public void setLastFrame(int n) {
        this.lastFrame = n;
    }

    public int getMaxFrames() {
        return this.maxFrames;
    }

    public void setMaxFrames(int n) {
        this.maxFrames = n;
    }

    public long getLastFrameTime() {
        return this.lastFrameTime;
    }

    public void setLastFrameTime(long l) {
        this.lastFrameTime = l;
    }

    public boolean getHasCape() {
        return this.hasCape;
    }

    public void setHasCape(boolean bl) {
        this.hasCape = bl;
    }

    public boolean getHasElytraTexture() {
        return this.hasElytraTexture;
    }

    public void setHasElytraTexture(boolean bl) {
        this.hasElytraTexture = bl;
    }

    public boolean getHasAnimatedCape() {
        return this.hasAnimatedCape;
    }

    public void setHasAnimatedCape(boolean bl) {
        this.hasAnimatedCape = bl;
    }

    public CapeType getCapeType() {
        return this.capeType;
    }

    public void setCapeType(CapeType capeType) {
        this.capeType = capeType;
    }

    public boolean getHasLoadedTextures() {
        return this.hasLoadedTextures;
    }

    public void setHasLoadedTextures(boolean bl) {
        this.hasLoadedTextures = bl;
    }

    public ClientAsset.Texture getCape() {
        if (!this.hasAnimatedCape) {
            String uuidStr = this.uuid.toString();
            return new ClientAsset.ResourceTexture(
                Capes.identifier(uuidStr),
                Capes.identifier(uuidStr)
            );
        }
        long time = System.currentTimeMillis();
        int frameToUse = this.lastFrame;
        if (time > this.lastFrameTime + 100L) {
            this.lastFrame = (this.lastFrame + 1) % this.maxFrames;
            this.lastFrameTime = time;
            frameToUse = this.lastFrame;
        }
        String framePath = this.uuid + "/" + frameToUse;
        return new ClientAsset.ResourceTexture(
            Capes.identifier(framePath),
            Capes.identifier(framePath)
        );
    }

    public boolean setCape(CapeType capeType) {
        if (capeType == null) {
            return false;
        }
        String url = capeType.getURL(this.profile);
        if (url == null) {
            return false;
        }
        try {
            HttpURLConnection connection = connection(url);
            boolean it = switch (capeType) {
                case LABYMOD -> this.setStandardCape(connection, true);
                case COSMETICA -> this.setCosmeticaCape(connection);
                case MINECRAFTCAPES -> this.setMCMCape(connection);
                default -> this.setStandardCape(connection, false);
            };
            if (it) {
                this.capeType = capeType;
            }
            return it;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean setStandardCape(HttpURLConnection connection) {
        return setStandardCape(connection, false);
    }

    public boolean setStandardCape(HttpURLConnection connection, boolean labymod) {
        try {
            connection.connect();
            if (connection.getResponseCode() / 100 == 2) {
                try (InputStream inputStream = connection.getInputStream()) {
                    return this.setCapeTexture(inputStream, false, labymod);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    public boolean setCosmeticaCape(HttpURLConnection connection) {
        try {
            connection.connect();
            if (connection.getResponseCode() / 100 != 2) {
                return false;
            }
            try (Reader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                CosmeticaData result = new Gson().fromJson(reader, CosmeticaData.class);
                if (result == null || result.getCape() == null) {
                    return false;
                }
                CosmeticaData.CapeData capeData = result.getCape();
                if (!"Cosmetica".equals(capeData.getOrigin())) {
                    return false;
                }
                String imageStr = capeData.getImage();
                if (imageStr.length() > 22) {
                    String sub = imageStr.substring(22);
                    return this.setCapeTextureFromBase64(sub, capeData.isAnimated());
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    public boolean setMCMCape(HttpURLConnection connection) {
        try {
            connection.connect();
            if (connection.getResponseCode() / 100 == 2) {
                MCMData profileData;
                try (Reader reader = new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)) {
                    profileData = new Gson().fromJson(reader, MCMData.class);
                }
                if (profileData != null && profileData.getCape_url() != null) {
                    HttpURLConnection resultConn = connection(profileData.getCape_url());
                    resultConn.connect();
                    if (resultConn.getResponseCode() / 100 == 2) {
                        try (InputStream inputStream = resultConn.getInputStream()) {
                            return this.setCapeTexture(inputStream, profileData.getAnimated_cape_url() != null, false);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    public boolean setCapeTextureFromBase64(String base64Texture) {
        return setCapeTextureFromBase64(base64Texture, false);
    }

    public boolean setCapeTextureFromBase64(String base64Texture, boolean animated) {
        if (base64Texture == null) {
            return false;
        }
        byte[] bytes = Base64.decodeBase64(base64Texture);
        return this.setCapeTexture(new ByteArrayInputStream(bytes), animated, false);
    }

    public boolean setCapeTexture(InputStream image) {
        return setCapeTexture(image, false, false);
    }

    public boolean setCapeTexture(InputStream image, boolean animated) {
        return setCapeTexture(image, animated, false);
    }

    public boolean setCapeTexture(InputStream image, boolean animated, boolean labymod) {
        if (image == null) {
            return false;
        }
        try {
            NativeImage nativeImage = NativeImage.read(image);
            if (nativeImage == null) {
                return false;
            }
            if (labymod && UUIDUtil.uuidFromIntArray(nativeImage.getPixels()).toString().equals("ff305f81-ff30-5f90-ff30-5f90ff305f90")) {
                nativeImage.close();
                return false;
            }
            Minecraft.getInstance().submit(() -> {
                if (animated) {
                    Int2ObjectOpenHashMap<NativeImage> animatedCapeFrames = this.parseAnimatedCape(nativeImage);
                    for (Map.Entry<Integer, NativeImage> entry : animatedCapeFrames.entrySet()) {
                        Integer frame = entry.getKey();
                        NativeImage texture = entry.getValue();
                        Minecraft.getInstance().getTextureManager().register(
                            Capes.identifier(this.uuid + "/" + frame),
                            new DynamicTexture(() -> this.uuid + "/" + frame, texture)
                        );
                    }
                    this.maxFrames = animatedCapeFrames.size();
                    this.hasCape = true;
                    this.hasAnimatedCape = true;
                } else {
                    int w = nativeImage.getWidth();
                    int h = nativeImage.getHeight();
                    int ratio = w / h;
                    if ((w ^ h) < 0 && ratio * h != w) {
                        ratio--;
                    }
                    this.hasElytraTexture = (ratio == 2);
                    TextureManager textureManager = Minecraft.getInstance().getTextureManager();
                    String uuidStr = this.uuid.toString();
                    textureManager.register(
                        Capes.identifier(uuidStr),
                        new DynamicTexture(() -> uuidStr, this.parseCape(nativeImage))
                    );
                    this.hasCape = true;
                }
            });
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private NativeImage parseCape(NativeImage img) {
        int imageHeight;
        int imageWidth = 64;
        int srcWidth = img.getWidth();
        int srcHeight = img.getHeight();
        for (imageHeight = 32; imageWidth < srcWidth || imageHeight < srcHeight; imageWidth *= 2, imageHeight *= 2) {
        }
        NativeImage imgNew = new NativeImage(imageWidth, imageHeight, true);
        for (int x = 0; x < srcWidth; ++x) {
            for (int y = 0; y < srcHeight; ++y) {
                imgNew.setPixel(x, y, img.getPixel(x, y));
            }
        }
        img.close();
        return imgNew;
    }

    private Int2ObjectOpenHashMap<NativeImage> parseAnimatedCape(NativeImage img) {
        Int2ObjectOpenHashMap<NativeImage> animatedCape = new Int2ObjectOpenHashMap<>();
        int totalFrames = img.getHeight() / (img.getWidth() / 2);
        for (int currentFrame = 0; currentFrame < totalFrames; ++currentFrame) {
            NativeImage frame = new NativeImage(img.getWidth(), img.getWidth() / 2, true);
            int n = frame.getWidth();
            for (int x = 0; x < n; ++x) {
                int n2 = frame.getHeight();
                for (int y = 0; y < n2; ++y) {
                    frame.setPixel(x, y, img.getPixel(x, y + currentFrame * (img.getWidth() / 2)));
                }
            }
            animatedCape.put(currentFrame, frame);
        }
        return animatedCape;
    }

    public static Map<UUID, PlayerHandler> getInstances() {
        return instances;
    }

    public static ExecutorService getCapeExecutor() {
        return capeExecutor;
    }

    public static PlayerHandler fromProfile(GameProfile profile) {
        PlayerHandler playerHandler = instances.get(profile.id());
        if (playerHandler == null) {
            playerHandler = new PlayerHandler(profile);
        }
        return playerHandler;
    }

    public static void onLoadTexture(GameProfile profile) {
        PlayerHandler playerHandler = fromProfile(profile);
        if (profile.equals(Minecraft.getInstance().getGameProfile())) {
            playerHandler.setHasCape(false);
            playerHandler.setHasAnimatedCape(false);
            com.thelads.core.config.Module capesMod = com.thelads.core.config.ModuleManager.getInstance().getModule("Capes");
            if (capesMod != null && !capesMod.isEnabled()) {
                return;
            }
            CapeType capeType = CapeType.MINECRAFT;
            if (capesMod != null) {
                var opt = capesMod.getOption("Preferred Cape");
                if (opt instanceof com.thelads.core.config.DropdownOption) {
                    capeType = CapeType.values()[((com.thelads.core.config.DropdownOption) opt).getIndex()];
                }
            }
            final CapeType targetCape = capeType;
            capeExecutor.submit(() -> {
                if (playerHandler.setCape(targetCape)) {
                    return;
                }
                for (CapeType type : CapeType.values()) {
                    if (type != targetCape) {
                        if (playerHandler.setCape(type)) {
                            break;
                        }
                    }
                }
            });
        } else if (!playerHandler.getHasLoadedTextures()) {
            com.thelads.core.config.Module capesMod = com.thelads.core.config.ModuleManager.getInstance().getModule("Capes");
            if (capesMod != null && !capesMod.isEnabled()) {
                return;
            }
            capeExecutor.submit(() -> {
                for (CapeType capeType : CapeType.values()) {
                    if (playerHandler.setCape(capeType)) {
                        break;
                    }
                }
            });
            playerHandler.setHasLoadedTextures(true);
        }
    }

    public static HttpURLConnection connection(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URI(url).toURL().openConnection(Minecraft.getInstance().getProxy());
            connection.addRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setDoInput(true);
            connection.setDoOutput(false);
            return connection;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
