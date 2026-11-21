package com.thelads.core.v1_20_1.feature.packs;

import com.google.common.hash.Hashing;
import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.perf.FastPackScreen;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.Util;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;
import org.slf4j.LoggerFactory;

/**
 * Pack icons of one resource pack screen on 1.20.1: pack.png is read and decoded on a background thread, and the texture is registered
 * (GPU upload) on the render thread. Until then the screen shows the default icon. Every method except decode runs on the render thread.
 */
public final class PackIcons1201 {
    private record Ready(int gen, String id, ResourceLocation location, NativeImage image) {}

    private final ConcurrentLinkedQueue<Ready> ready = new ConcurrentLinkedQueue<>();
    private final Set<String> pending = new HashSet<>();
    private volatile int gen;

    /** The icon for a pack: cached one, or the default icon while the decode runs. */
    public ResourceLocation get(Pack pack, Map<String, ResourceLocation> cache, ResourceLocation fallback, TextureManager textures) {
        String id = pack.getId();
        drain(cache, fallback, textures);
        ResourceLocation have = cache.get(id);
        if (have != null) return have;
        if (pending.add(id)) {
            int mine = gen;
            FastPackScreen.decoder().execute(() -> decode(pack, mine));
        }
        return fallback;
    }

    /** Pack list reloaded or screen closed: drop anything still in flight. */
    public void reset() {
        gen++;
        pending.clear();
        Ready r;
        while ((r = ready.poll()) != null) if (r.image != null) r.image.close();
    }

    private void drain(Map<String, ResourceLocation> cache, ResourceLocation fallback, TextureManager textures) {
        Ready r;
        while ((r = ready.poll()) != null) {
            if (r.gen != gen) {
                if (r.image != null) r.image.close();
                continue;
            }
            pending.remove(r.id);
            if (r.image != null) textures.register(r.location, new DynamicTexture(r.image));
            cache.put(r.id, r.image != null ? r.location : fallback);
        }
    }

    private void decode(Pack pack, int mine) {
        String id = pack.getId();
        ResourceLocation location = new ResourceLocation("minecraft", "pack/" + Util.sanitizeName(id, ResourceLocation::validPathChar) + "/"
            + Hashing.sha1().hashUnencodedChars(id) + "/icon");
        NativeImage image = null;
        try (PackResources resources = pack.open()) {
            IoSupplier<InputStream> png = resources.getRootResource("pack.png");
            if (png != null) {
                try (InputStream in = png.get()) {
                    image = NativeImage.read(in);
                }
            }
        } catch (Exception e) {
            LoggerFactory.getLogger("TheLadsCore").warn("Failed to load icon from pack {}", id, e);
            image = null;
        }
        ready.add(new Ready(mine, id, location, image));
    }
}
