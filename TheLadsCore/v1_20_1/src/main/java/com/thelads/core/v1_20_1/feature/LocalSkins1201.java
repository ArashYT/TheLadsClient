package com.thelads.core.v1_20_1.feature;

import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.client.util.LocalSkinSource;
import com.thelads.core.v1_20_1.mixin.menus.SkinTextureAccessor;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** Local skin library. Network and disk work never runs on the render thread. */
public final class LocalSkins1201 {
    /** The selected local skin: its texture and whether it uses the slim (3 pixel arm) model. */
    public record Skin(ResourceLocation texture, boolean slim) {}

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static volatile Skin selected;
    private static boolean initialized;
    private static volatile int revision;
    private static final Object STATE = new Object();

    private LocalSkins1201() {}

    public static Skin current() {
        return selected;
    }

    public static void initialize() {
        if (initialized) return;
        initialized = true;
        Path file = folder().resolve("skin.png");
        if (Files.isRegularFile(file)) load(file.toString(), readSlim(), false);
    }

    private static boolean readSlim() {
        try {
            return Files.readString(folder().resolve("model.txt")).equals("slim");
        } catch (Exception e) {
            return false;
        }
    }

    private static Path folder() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/lads-skin");
    }

    public static CompletableFuture<String> load(String input, boolean slim, boolean persist) {
        var result = new CompletableFuture<String>();
        final int request;
        synchronized (STATE) {
            request = ++revision;
        }
        CompletableFuture.runAsync(() -> {
            try {
                LocalSkinSource.Skin source = LocalSkinSource.resolve(input, slim);
                byte[] data = source.png();
                boolean model = source.slim();
                validate(data);
                NativeImage image = normalize(NativeImage.read(new java.io.ByteArrayInputStream(data)));
                if (image == null) throw new java.io.IOException("Skin must be 64 x 64 or 64 x 32 pixels");
                boolean finalModel = model;
                try {
                    synchronized (STATE) {
                        if (request != revision) {
                            image.close();
                            result.complete("A newer skin was selected");
                            return;
                        }
                        if (persist) {
                            Files.createDirectories(folder());
                            Path temporary = Files.createTempFile(folder(), "skin-", ".png");
                            try {
                                image.writeToFile(temporary);
                                Files.move(temporary, folder().resolve("skin.png"), StandardCopyOption.REPLACE_EXISTING);
                            } finally {
                                Files.deleteIfExists(temporary);
                            }
                            Files.writeString(folder().resolve("model.txt"), finalModel ? "slim" : "wide");
                        }
                    }
                } catch (Throwable failure) {
                    image.close();
                    throw failure;
                }
                Minecraft.getInstance().execute(() -> {
                    if (request != revision) {
                        image.close();
                        result.complete("A newer skin was selected");
                        return;
                    }
                    try {
                        var mc = Minecraft.getInstance();
                        ResourceLocation id = new ResourceLocation("theladscore", "local_skin/" + request);
                        mc.getTextureManager().register(id, new DynamicTexture(image));
                        Skin old = selected;
                        selected = new Skin(id, finalModel);
                        if (old != null) mc.getTextureManager().release(old.texture());
                        result.complete("Skin applied locally");
                    } catch (Throwable e) {
                        image.close();
                        result.completeExceptionally(e);
                    }
                });
            } catch (Throwable e) {
                result.completeExceptionally(e);
            }
        });
        return result;
    }

    /** The skin as a 64x64 image (vanilla's own conversion of the old 64x32 layout), or null when the size is wrong. */
    private static NativeImage normalize(NativeImage image) {
        var texture = new net.minecraft.client.renderer.texture.HttpTexture(null, "", new ResourceLocation("minecraft", "textures/entity/steve.png"), true, null);
        return ((SkinTextureAccessor) texture).ladsProcessLegacySkin(image);
    }

    public static byte[] fetch(String url) throws Exception {
        URI uri = URI.create(url);
        if (!List.of("https", "http").contains(uri.getScheme())) throw new java.io.IOException("Use an HTTP(S) image URL");
        var response = HTTP.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).header("User-Agent", "TheLadsClient/" + com.thelads.core.LadsVersion.VERSION).GET().build(),
            HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            if (response.statusCode() != 200) throw new java.io.IOException("Server returned " + response.statusCode());
            byte[] bytes = body.readNBytes(1024 * 1024 + 1);
            if (bytes.length > 1024 * 1024) throw new java.io.IOException("Response exceeds 1 MB");
            return bytes;
        }
    }

    public static void validate(byte[] bytes) throws java.io.IOException {
        if (bytes.length < 24 || java.nio.ByteBuffer.wrap(bytes).getLong() != 0x89504E470D0A1A0AL) throw new java.io.IOException("Select a PNG skin");
        var header = java.nio.ByteBuffer.wrap(bytes);
        int w = header.getInt(16), h = header.getInt(20);
        if (w != 64 || (h != 64 && h != 32)) throw new java.io.IOException("Skin must be 64 x 64 or 64 x 32 pixels");
    }

    public static void setModel(boolean slim) {
        final int request;
        synchronized (STATE) {
            if (selected == null) return;
            request = revision;
            selected = new Skin(selected.texture(), slim);
        }
        CompletableFuture.runAsync(() -> {
            synchronized (STATE) {
                if (request != revision || selected == null || selected.slim() != slim) return;
                try {
                    Files.createDirectories(folder());
                    Files.writeString(folder().resolve("model.txt"), slim ? "slim" : "wide");
                } catch (java.io.IOException failure) {
                    org.slf4j.LoggerFactory.getLogger("TheLadsCore").warn("Could not save skin model", failure);
                }
            }
        });
    }

    public static void reset() {
        Skin old = selected;
        synchronized (STATE) {
            selected = null;
            revision++;
            try {
                Files.deleteIfExists(folder().resolve("skin.png"));
            } catch (java.io.IOException e) {
                org.slf4j.LoggerFactory.getLogger("TheLadsCore").warn("Could not reset saved local skin", e);
            }
        }
        if (old != null) Minecraft.getInstance().getTextureManager().release(old.texture());
    }
}
