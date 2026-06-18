package com.thelads.core.client.cosmetics.backend;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class CosmeticsBackend {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().build();
    private static final Gson GSON = new Gson();

    private static final java.util.Map<UUID, Identifier> activeSkins = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<UUID, Identifier> activeCapes = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.Map<Identifier, Integer> refCounts = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<Identifier, Long> unassigned = new java.util.concurrent.ConcurrentHashMap<>();

    // Raw PNG bytes for each registered texture, so equipped cosmetics can be
    // re-created (persisted to disk + restored on the next launch). Cleaned in
    // lockstep with refCounts whenever a texture is fully released.
    private static final java.util.Map<Identifier, byte[]> textureData = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile boolean loaded = false;

    private static TextureRegistrar registrar = CosmeticsBackend::defaultRegisterTexture;

    public static void setRegistrar(TextureRegistrar newRegistrar) {
        registrar = newRegistrar;
    }

    private static void cleanupUnassigned() {
        long now = System.currentTimeMillis();
        for (java.util.Map.Entry<Identifier, Long> entry : unassigned.entrySet()) {
            if (now - entry.getValue() > 60000) { // 60 seconds
                Identifier id = entry.getKey();
                unassigned.remove(id);
                if (refCounts.getOrDefault(id, 0) <= 0) {
                    refCounts.remove(id);
                    textureData.remove(id);
                    Minecraft.getInstance().execute(() -> {
                        net.minecraft.client.renderer.texture.AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(id);
                        if (tex != null) {
                            tex.close();
                        }
                        Minecraft.getInstance().getTextureManager().release(id);
                    });
                }
            }
        }
    }

    public static Identifier getActiveSkin(UUID uuid) {
        if (uuid == null) return null;
        return activeSkins.get(uuid);
    }

    public static void setActiveSkin(UUID uuid, Identifier skin) {
        if (uuid == null) return;
        cleanupUnassigned();
        Identifier oldIdentifier;
        if (skin != null) {
            unassigned.remove(skin);
            refCounts.merge(skin, 1, Integer::sum);
            oldIdentifier = activeSkins.put(uuid, skin);
        } else {
            oldIdentifier = activeSkins.remove(uuid);
        }
        if (oldIdentifier != null && !oldIdentifier.equals(skin)) {
            int newCount = refCounts.merge(oldIdentifier, -1, Integer::sum);
            if (newCount <= 0) {
                refCounts.remove(oldIdentifier);
                textureData.remove(oldIdentifier);
                Minecraft.getInstance().execute(() -> {
                    net.minecraft.client.renderer.texture.AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(oldIdentifier);
                    if (tex != null) {
                        tex.close();
                    }
                    Minecraft.getInstance().getTextureManager().release(oldIdentifier);
                });
            }
        }
    }

    public static void clearActiveSkin(UUID uuid) {
        if (uuid == null) return;
        cleanupUnassigned();
        Identifier oldIdentifier = activeSkins.remove(uuid);
        if (oldIdentifier != null) {
            int newCount = refCounts.merge(oldIdentifier, -1, Integer::sum);
            if (newCount <= 0) {
                refCounts.remove(oldIdentifier);
                textureData.remove(oldIdentifier);
                Minecraft.getInstance().execute(() -> {
                    net.minecraft.client.renderer.texture.AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(oldIdentifier);
                    if (tex != null) {
                        tex.close();
                    }
                    Minecraft.getInstance().getTextureManager().release(oldIdentifier);
                });
            }
        }
    }

    public static Identifier getActiveCape(UUID uuid) {
        if (uuid == null) return null;
        return activeCapes.get(uuid);
    }

    public static void setActiveCape(UUID uuid, Identifier cape) {
        if (uuid == null) return;
        cleanupUnassigned();
        Identifier oldIdentifier;
        if (cape != null) {
            unassigned.remove(cape);
            refCounts.merge(cape, 1, Integer::sum);
            oldIdentifier = activeCapes.put(uuid, cape);
        } else {
            oldIdentifier = activeCapes.remove(uuid);
        }
        if (oldIdentifier != null && !oldIdentifier.equals(cape)) {
            int newCount = refCounts.merge(oldIdentifier, -1, Integer::sum);
            if (newCount <= 0) {
                refCounts.remove(oldIdentifier);
                textureData.remove(oldIdentifier);
                Minecraft.getInstance().execute(() -> {
                    net.minecraft.client.renderer.texture.AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(oldIdentifier);
                    if (tex != null) {
                        tex.close();
                    }
                    Minecraft.getInstance().getTextureManager().release(oldIdentifier);
                });
            }
        }
    }

    public static void clearActiveCape(UUID uuid) {
        if (uuid == null) return;
        cleanupUnassigned();
        Identifier oldIdentifier = activeCapes.remove(uuid);
        if (oldIdentifier != null) {
            int newCount = refCounts.merge(oldIdentifier, -1, Integer::sum);
            if (newCount <= 0) {
                refCounts.remove(oldIdentifier);
                textureData.remove(oldIdentifier);
                Minecraft.getInstance().execute(() -> {
                    net.minecraft.client.renderer.texture.AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(oldIdentifier);
                    if (tex != null) {
                        tex.close();
                    }
                    Minecraft.getInstance().getTextureManager().release(oldIdentifier);
                });
            }
        }
    }

    public static CompletableFuture<Identifier> fetchByUsername(String username, boolean isCape) {
        cleanupUnassigned();
        HttpRequest uuidReq = HttpRequest.newBuilder()
                .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + username))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        return HTTP_CLIENT.sendAsync(uuidReq, HttpResponse.BodyHandlers.ofString())
            .thenCompose(uuidResp -> {
                if (uuidResp.statusCode() != 200) throw new java.util.concurrent.CompletionException(new RuntimeException("Failed to get UUID for " + username));
                JsonObject uuidJson = GSON.fromJson(uuidResp.body(), JsonObject.class);
                String uuid = uuidJson.get("id").getAsString();

                HttpRequest profileReq = HttpRequest.newBuilder()
                        .uri(URI.create("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                return HTTP_CLIENT.sendAsync(profileReq, HttpResponse.BodyHandlers.ofString());
            })
            .thenCompose(profileResp -> {
                if (profileResp.statusCode() != 200) throw new java.util.concurrent.CompletionException(new RuntimeException("Failed to get profile for " + username));
                JsonObject profileJson = GSON.fromJson(profileResp.body(), JsonObject.class);
                String texturesBase64 = null;
                for (var propElement : profileJson.getAsJsonArray("properties")) {
                    JsonObject prop = propElement.getAsJsonObject();
                    if ("textures".equals(prop.get("name").getAsString())) {
                        texturesBase64 = prop.get("value").getAsString();
                        break;
                    }
                }
                if (texturesBase64 == null) throw new java.util.concurrent.CompletionException(new RuntimeException("No textures property found for " + username));
                String decodedTextures = new String(Base64.getDecoder().decode(texturesBase64));
                JsonObject texturesJson = GSON.fromJson(decodedTextures, JsonObject.class);
                JsonObject texturesObj = texturesJson.getAsJsonObject("textures");
                String targetType = isCape ? "CAPE" : "SKIN";
                if (texturesObj == null || !texturesObj.has(targetType)) {
                    throw new java.util.concurrent.CompletionException(new RuntimeException("No " + targetType + " found for " + username));
                }
                String textureUrl = texturesObj.getAsJsonObject(targetType).get("url").getAsString();

                HttpRequest textureReq = HttpRequest.newBuilder()
                        .uri(URI.create(textureUrl))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                return HTTP_CLIENT.sendAsync(textureReq, HttpResponse.BodyHandlers.ofByteArray());
            })
            .thenCompose(textureResp -> {
                if (textureResp.statusCode() != 200) throw new java.util.concurrent.CompletionException(new RuntimeException("Failed to download texture"));
                return registerTexture(textureResp.body());
            })
            .orTimeout(15, java.util.concurrent.TimeUnit.SECONDS);
    }

    public static CompletableFuture<Identifier> fetchByUrl(String url) {
        cleanupUnassigned();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            return HTTP_CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofByteArray())
                    .thenCompose(resp -> {
                        if (resp.statusCode() != 200) return CompletableFuture.<Identifier>failedFuture(new RuntimeException("Failed to download texture from " + url));
                        return registerTexture(resp.body());
                    }).orTimeout(15, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public static CompletableFuture<Identifier> fetchByFile(File file) {
        cleanupUnassigned();
        CompletableFuture<byte[]> futureBytes = new CompletableFuture<>();
        try {
            java.nio.channels.AsynchronousFileChannel channel = java.nio.channels.AsynchronousFileChannel.open(file.toPath(), java.nio.file.StandardOpenOption.READ);
            long size = channel.size();
            if (size > 1048576) {
                futureBytes.completeExceptionally(new RuntimeException("Image file size exceeds maximum allowed size (1MB)"));
                channel.close();
            } else {
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate((int) size);
                channel.read(buffer, 0, buffer, new java.nio.channels.CompletionHandler<Integer, java.nio.ByteBuffer>() {
                    @Override
                    public void completed(Integer result, java.nio.ByteBuffer attachment) {
                        try {
                            channel.close();
                            futureBytes.complete(attachment.array());
                        } catch (Exception e) {
                            futureBytes.completeExceptionally(e);
                        }
                    }

                    @Override
                    public void failed(Throwable exc, java.nio.ByteBuffer attachment) {
                        try {
                            channel.close();
                        } catch (Exception e) {
                            // ignore
                        }
                        futureBytes.completeExceptionally(exc);
                    }
                });
            }
        } catch (Exception e) {
            futureBytes.completeExceptionally(e);
        }

        return futureBytes.thenCompose(data -> registerTexture(data))
          .orTimeout(15, java.util.concurrent.TimeUnit.SECONDS);
    }

    private static CompletableFuture<Identifier> registerTexture(byte[] data) {
        return registrar.register(data).thenApply(id -> {
            unassigned.put(id, System.currentTimeMillis());
            textureData.put(id, data);
            return id;
        });
    }

    private static CompletableFuture<Identifier> defaultRegisterTexture(byte[] data) {
        if (data.length > 1048576) {
            return CompletableFuture.failedFuture(new RuntimeException("Image file size exceeds maximum allowed size (1MB)"));
        }
        NativeImage img;
        try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(data)) {
            img = NativeImage.read(bais);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
        CompletableFuture<Identifier> future = new CompletableFuture<>();
        Minecraft.getInstance().execute(() -> {
            try {
                DynamicTexture tex = new DynamicTexture(() -> "cosmetics_preview", img);
                Identifier id = Identifier.fromNamespaceAndPath("thelads", "cosmetics_preview_" + UUID.randomUUID().toString());
                Minecraft.getInstance().getTextureManager().register(id, tex);
                future.complete(id);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private static File persistFile() {
        return new File(Minecraft.getInstance().gameDirectory, "thelads_cosmetics.json");
    }

    private static void writeSection(JsonObject section, java.util.Map<UUID, Identifier> active) {
        for (java.util.Map.Entry<UUID, Identifier> entry : active.entrySet()) {
            byte[] data = textureData.get(entry.getValue());
            if (data != null) {
                section.addProperty(entry.getKey().toString(), Base64.getEncoder().encodeToString(data));
            }
        }
    }

    /** Persist the currently active skins/capes (as base64 PNG data) so they survive a restart. */
    public static void persist() {
        JsonObject root = new JsonObject();
        JsonObject skins = new JsonObject();
        JsonObject capes = new JsonObject();
        writeSection(skins, activeSkins);
        writeSection(capes, activeCapes);
        root.add("skins", skins);
        root.add("capes", capes);

        try (FileWriter writer = new FileWriter(persistFile())) {
            GSON.toJson(root, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadSection(JsonObject section, boolean isCape) {
        if (section == null) return;
        for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry : section.entrySet()) {
            try {
                UUID uuid = UUID.fromString(entry.getKey());
                byte[] data = Base64.getDecoder().decode(entry.getValue().getAsString());
                registerTexture(data).thenAccept(id -> {
                    if (isCape) {
                        setActiveCape(uuid, id);
                    } else {
                        setActiveSkin(uuid, id);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /** Restore persisted skins/capes from disk. Safe to call once at client startup. */
    public static void load() {
        if (loaded) return;
        loaded = true;
        File file = persistFile();
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) return;
            loadSection(root.getAsJsonObject("skins"), false);
            loadSection(root.getAsJsonObject("capes"), true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
