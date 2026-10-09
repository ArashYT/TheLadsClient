package com.thelads.core.v1_8_9.render;

import com.thelads.core.perf.BuiltInRender;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.ARBCopyImage;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.GLContext;

/**
 * Lads Kerria on 1.8.9 (1.8.5): the first time the block atlas animates a sprite, all its frames (every mip level) are uploaded once
 * into a texture of its own, through the game's own TextureUtil upload so the texels are exactly what the game would write; from
 * then on each frame change is a GPU-to-GPU copy (glCopyImageSubData) into the atlas instead of a glTexSubImage2D from client
 * memory. With OptiFine too: its Smart Animations skip invisible sprites before this runs, and its shader normal/specular and
 * multi-texture sprites keep their own uploads. Caches die with the atlas (F3+T, resource packs, mipmap changes). Off while
 * anaglyph 3D is on (the game converts frames as it uploads them).
 */
public final class AnimationCache189 {
    private static final Logger LOG = LogManager.getLogger("TheLadsCore");
    private static final long BUDGET = 256L << 20;
    private static final Map<TextureAtlasSprite, Integer> CACHES = new IdentityHashMap<TextureAtlasSprite, Integer>();
    private static final Map<TextureAtlasSprite, Boolean> MAIN = new IdentityHashMap<TextureAtlasSprite, Boolean>();
    /** -1 unknown, 0 unsupported, 1 GL 4.3, 2 ARB_copy_image. */
    private static int copyPath = -1;
    private static long used;
    /** The block atlas while its animations tick (TextureMap.updateAnimations), else 0. */
    public static int cyclingAtlas;

    private AnimationCache189() {}

    /** TextureMap.loadTextureAtlas: every frame cache goes with the old sprites. */
    public static void clear() {
        for (int texture : CACHES.values()) if (texture > 0) TextureUtil.deleteTexture(texture);
        CACHES.clear();
        MAIN.clear();
        used = 0;
    }

    /** After the atlas was stitched: its animated sprites (OptiFine's normal, specular and single sprites are not among them). */
    public static void stitched(List<TextureAtlasSprite> animated) {
        for (TextureAtlasSprite sprite : animated) MAIN.put(sprite, Boolean.TRUE);
    }

    /**
     * TextureAtlasSprite.updateAnimation's upload of frame {@code frame} (of {@code frames}): copied from the sprite's cache into
     * the atlas when possible. False when the game should upload it.
     */
    public static boolean copy(TextureAtlasSprite sprite, List<int[][]> frames, int frame, int width, int height, int x, int y) {
        if (!BuiltInRender.animations || cyclingAtlas == 0 || !MAIN.containsKey(sprite) || Minecraft.getMinecraft().gameSettings.anaglyph || !supported())
            return false;
        Integer known = CACHES.get(sprite);
        int cache = known == null ? 0 : known;
        if (cache < 0) return false;
        boolean fresh = cache == 0;
        if (fresh) {
            for (int i = 0; i < 16 && GL11.glGetError() != 0; i++) {} // stale errors are someone else's
            cache = build(frames, width, height);
            CACHES.put(sprite, cache);
            if (cache < 0) return false;
        }
        int[][] levels = frames.get(frame);
        for (int level = 0; level < levels.length; level++) {
            int w = width >> level, h = height >> level;
            if (w <= 0 || h <= 0) continue;
            if (copyPath == 1) GL43.glCopyImageSubData(cache, GL11.GL_TEXTURE_2D, level, 0, frame * h, 0,
                cyclingAtlas, GL11.GL_TEXTURE_2D, level, x >> level, y >> level, 0, w, h, 1);
            else ARBCopyImage.glCopyImageSubData(cache, GL11.GL_TEXTURE_2D, level, 0, frame * h, 0,
                cyclingAtlas, GL11.GL_TEXTURE_2D, level, x >> level, y >> level, 0, w, h, 1);
        }
        if (fresh) {
            int error = GL11.glGetError();
            if (error != 0) {
                LOG.warn("Lads Kerria: animation cache off, glCopyImageSubData failed (GL error {}); using the game's texture uploads", error);
                copyPath = 0;
                clear();
                return false;
            }
        }
        BuiltInRender.framesCopied++;
        return true;
    }

    /** Every frame stacked vertically (frame k at y = k * (height >> level) on each level), uploaded the game's way. */
    private static int build(List<int[][]> frames, int width, int height) {
        int levels = frames.get(0).length;
        long size = 0;
        for (int level = 0; level < levels; level++) size += 4L * (width >> level) * (height >> level) * frames.size();
        if (used + size > BUDGET) return -1;
        int texture = TextureUtil.glGenTextures();
        TextureUtil.allocateTextureImpl(texture, levels - 1, width, height * frames.size());
        for (int k = 0; k < frames.size(); k++)
            TextureUtil.uploadTextureMipmap(frames.get(k), width, height, 0, k * height, false, false);
        GlStateManager.bindTexture(cyclingAtlas); // the atlas stays bound for the rest of its tick, as the game left it
        used += size;
        return texture;
    }

    private static boolean supported() {
        if (copyPath < 0) {
            ContextCapabilities caps = GLContext.getCapabilities();
            copyPath = caps.OpenGL43 ? 1 : caps.GL_ARB_copy_image ? 2 : 0;
            if (copyPath == 0) LOG.info("Lads Kerria: animation cache off, the driver has neither OpenGL 4.3 nor ARB_copy_image");
        }
        return copyPath > 0;
    }

    public static String describe() {
        return "animation cache " + (copyPath < 0 ? "not used yet" : copyPath == 0 ? "off" : copyPath == 1 ? "GL 4.3 copies" : "ARB_copy_image copies")
            + ", " + CACHES.size() + " sprites, " + (used >> 10) + " KiB";
    }
}
