/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.google.gson.Gson
 *  com.google.gson.JsonElement
 *  com.google.gson.JsonObject
 *  com.mojang.blaze3d.platform.NativeImage
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.renderer.texture.AbstractTexture
 *  net.minecraft.client.renderer.texture.DynamicTexture
 */
package com.thelads.core.client.cosmetics.backend;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.client.cosmetics.backend.TextureRegistrar;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.CompletionHandler;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public class CosmeticsBackend {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().build();
    private static final Gson GSON = new Gson();
    private static final Map<UUID, Identifier> activeSkins = new ConcurrentHashMap<UUID, Identifier>();
    private static final Map<UUID, Identifier> activeCapes = new ConcurrentHashMap<UUID, Identifier>();
    private static final Map<Identifier, Integer> refCounts = new ConcurrentHashMap<Identifier, Integer>();
    private static final Map<Identifier, Long> unassigned = new ConcurrentHashMap<Identifier, Long>();
    private static TextureRegistrar registrar = CosmeticsBackend::defaultRegisterTexture;

    public static void setRegistrar(TextureRegistrar newRegistrar) {
        registrar = newRegistrar;
    }

    private static void cleanupUnassigned() {
        long now = System.currentTimeMillis();
        for (Map.Entry<Identifier, Long> entry : unassigned.entrySet()) {
            if (now - entry.getValue() <= 60000L) continue;
            Identifier id = entry.getKey();
            unassigned.remove(id);
            if (refCounts.getOrDefault(id, 0) > 0) continue;
            refCounts.remove(id);
            Minecraft.getInstance().execute(() -> {
                AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(id);
                if (tex != null) {
                    tex.close();
                }
                Minecraft.getInstance().getTextureManager().release(id);
            });
        }
    }

    public static Identifier getActiveSkin(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return activeSkins.get(uuid);
    }

    public static void setActiveSkin(UUID uuid, Identifier skin) {
        int newCount;
        Identifier oldIdentifier;
        if (uuid == null) {
            return;
        }
        CosmeticsBackend.cleanupUnassigned();
        if (skin != null) {
            unassigned.remove(skin);
            refCounts.merge(skin, 1, Integer::sum);
            oldIdentifier = activeSkins.put(uuid, skin);
        } else {
            oldIdentifier = activeSkins.remove(uuid);
        }
        if (oldIdentifier != null && !oldIdentifier.equals(skin) && (newCount = refCounts.merge(oldIdentifier, -1, Integer::sum).intValue()) <= 0) {
            refCounts.remove(oldIdentifier);
            Minecraft.getInstance().execute(() -> {
                AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(oldIdentifier);
                if (tex != null) {
                    tex.close();
                }
                Minecraft.getInstance().getTextureManager().release(oldIdentifier);
            });
        }
    }

    public static void clearActiveSkin(UUID uuid) {
        int newCount;
        if (uuid == null) {
            return;
        }
        CosmeticsBackend.cleanupUnassigned();
        Identifier oldIdentifier = activeSkins.remove(uuid);
        if (oldIdentifier != null && (newCount = refCounts.merge(oldIdentifier, -1, Integer::sum).intValue()) <= 0) {
            refCounts.remove(oldIdentifier);
            Minecraft.getInstance().execute(() -> {
                AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(oldIdentifier);
                if (tex != null) {
                    tex.close();
                }
                Minecraft.getInstance().getTextureManager().release(oldIdentifier);
            });
        }
    }

    public static Identifier getActiveCape(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return activeCapes.get(uuid);
    }

    public static void setActiveCape(UUID uuid, Identifier cape) {
        int newCount;
        Identifier oldIdentifier;
        if (uuid == null) {
            return;
        }
        CosmeticsBackend.cleanupUnassigned();
        if (cape != null) {
            unassigned.remove(cape);
            refCounts.merge(cape, 1, Integer::sum);
            oldIdentifier = activeCapes.put(uuid, cape);
        } else {
            oldIdentifier = activeCapes.remove(uuid);
        }
        if (oldIdentifier != null && !oldIdentifier.equals(cape) && (newCount = refCounts.merge(oldIdentifier, -1, Integer::sum).intValue()) <= 0) {
            refCounts.remove(oldIdentifier);
            Minecraft.getInstance().execute(() -> {
                AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(oldIdentifier);
                if (tex != null) {
                    tex.close();
                }
                Minecraft.getInstance().getTextureManager().release(oldIdentifier);
            });
        }
    }

    public static void clearActiveCape(UUID uuid) {
        int newCount;
        if (uuid == null) {
            return;
        }
        CosmeticsBackend.cleanupUnassigned();
        Identifier oldIdentifier = activeCapes.remove(uuid);
        if (oldIdentifier != null && (newCount = refCounts.merge(oldIdentifier, -1, Integer::sum).intValue()) <= 0) {
            refCounts.remove(oldIdentifier);
            Minecraft.getInstance().execute(() -> {
                AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(oldIdentifier);
                if (tex != null) {
                    tex.close();
                }
                Minecraft.getInstance().getTextureManager().release(oldIdentifier);
            });
        }
    }

    public static CompletableFuture<Identifier> fetchByUsername(String username, boolean isCape) {
        CosmeticsBackend.cleanupUnassigned();
        HttpRequest uuidReq = HttpRequest.newBuilder().uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username)).timeout(Duration.ofSeconds(10L)).GET().build();
        return ((CompletableFuture)((CompletableFuture)((CompletableFuture)HTTP_CLIENT.sendAsync(uuidReq, HttpResponse.BodyHandlers.ofString()).thenCompose(uuidResp -> {
            if (uuidResp.statusCode() != 200) {
                throw new CompletionException(new RuntimeException("Failed to get UUID for " + username));
            }
            JsonObject uuidJson = (JsonObject)GSON.fromJson((String)uuidResp.body(), JsonObject.class);
            String uuid = uuidJson.get("id").getAsString();
            HttpRequest profileReq = HttpRequest.newBuilder().uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid)).timeout(Duration.ofSeconds(10L)).GET().build();
            return HTTP_CLIENT.sendAsync(profileReq, HttpResponse.BodyHandlers.ofString());
        })).thenCompose(profileResp -> {
            String targetType;
            if (profileResp.statusCode() != 200) {
                throw new CompletionException(new RuntimeException("Failed to get profile for " + username));
            }
            JsonObject profileJson = (JsonObject)GSON.fromJson((String)profileResp.body(), JsonObject.class);
            String texturesBase64 = null;
            for (JsonElement propElement : profileJson.getAsJsonArray("properties")) {
                JsonObject prop = propElement.getAsJsonObject();
                if (!"textures".equals(prop.get("name").getAsString())) continue;
                texturesBase64 = prop.get("value").getAsString();
                break;
            }
            if (texturesBase64 == null) {
                throw new CompletionException(new RuntimeException("No textures property found for " + username));
            }
            String decodedTextures = new String(Base64.getDecoder().decode(texturesBase64));
            JsonObject texturesJson = (JsonObject)GSON.fromJson(decodedTextures, JsonObject.class);
            JsonObject texturesObj = texturesJson.getAsJsonObject("textures");
            String string = targetType = isCape ? "CAPE" : "SKIN";
            if (texturesObj == null || !texturesObj.has(targetType)) {
                throw new CompletionException(new RuntimeException("No " + targetType + " found for " + username));
            }
            String textureUrl = texturesObj.getAsJsonObject(targetType).get("url").getAsString();
            HttpRequest textureReq = HttpRequest.newBuilder().uri(URI.create(textureUrl)).timeout(Duration.ofSeconds(10L)).GET().build();
            return HTTP_CLIENT.sendAsync(textureReq, HttpResponse.BodyHandlers.ofByteArray());
        })).thenCompose(textureResp -> {
            if (textureResp.statusCode() != 200) {
                throw new CompletionException(new RuntimeException("Failed to download texture"));
            }
            return CosmeticsBackend.registerTexture((byte[])textureResp.body());
        })).orTimeout(15L, TimeUnit.SECONDS);
    }

    public static CompletableFuture<Identifier> fetchByUrl(String url) {
        CosmeticsBackend.cleanupUnassigned();
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10L)).GET().build();
            return ((CompletableFuture)HTTP_CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofByteArray()).thenCompose(resp -> {
                if (resp.statusCode() != 200) {
                    return CompletableFuture.failedFuture(new RuntimeException("Failed to download texture from " + url));
                }
                return CosmeticsBackend.registerTexture((byte[])resp.body());
            })).orTimeout(15L, TimeUnit.SECONDS);
        }
        catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public static CompletableFuture<Identifier> fetchByFile(File file) {
        CosmeticsBackend.cleanupUnassigned();
        final CompletableFuture futureBytes = new CompletableFuture();
        try {
            final AsynchronousFileChannel channel = AsynchronousFileChannel.open(file.toPath(), StandardOpenOption.READ);
            long size = channel.size();
            if (size > 0x100000L) {
                futureBytes.completeExceptionally(new RuntimeException("Image file size exceeds maximum allowed size (1MB)"));
                channel.close();
            } else {
                ByteBuffer buffer = ByteBuffer.allocate((int)size);
                channel.read(buffer, 0L, buffer, new CompletionHandler<Integer, ByteBuffer>(){

                    @Override
                    public void completed(Integer result, ByteBuffer attachment) {
                        try {
                            channel.close();
                            futureBytes.complete(attachment.array());
                        }
                        catch (Exception e) {
                            futureBytes.completeExceptionally(e);
                        }
                    }

                    @Override
                    public void failed(Throwable exc, ByteBuffer attachment) {
                        try {
                            channel.close();
                        }
                        catch (Exception exception) {
                            // empty catch block
                        }
                        futureBytes.completeExceptionally(exc);
                    }
                });
            }
        }
        catch (Exception e) {
            futureBytes.completeExceptionally(e);
        }
        return ((CompletableFuture)futureBytes.thenCompose(data -> CosmeticsBackend.registerTexture(data))).orTimeout(15L, TimeUnit.SECONDS);
    }

    private static CompletableFuture<Identifier> registerTexture(byte[] data) {
        return registrar.register(data).thenApply(id -> {
            unassigned.put((Identifier)id, System.currentTimeMillis());
            return id;
        });
    }

    private static CompletableFuture<Identifier> defaultRegisterTexture(byte[] data) {
        NativeImage img;
        if (data.length > 0x100000) {
            return CompletableFuture.failedFuture(new RuntimeException("Image file size exceeds maximum allowed size (1MB)"));
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);){
            img = NativeImage.read((InputStream)bais);
        }
        catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
        CompletableFuture<Identifier> future = new CompletableFuture<Identifier>();
        Minecraft.getInstance().execute(() -> {
            try {
                DynamicTexture tex = new DynamicTexture(() -> "cosmetics_preview", img);
                Identifier id = Identifier.fromNamespaceAndPath("thelads", "cosmetics_preview_" + UUID.randomUUID().toString());
                Minecraft.getInstance().getTextureManager().register(id, (AbstractTexture)tex);
                future.complete(id);
            }
            catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }
}

