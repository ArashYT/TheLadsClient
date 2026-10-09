package com.thelads.core.v1_20_1.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.thelads.core.perf.BuiltInRender;
import org.lwjgl.opengl.ARBCopyImage;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GLCapabilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lads Kerria, animation cache (1.8.5): the first time an atlas animation ticks a sprite, its whole frame strip (every mip level) is
 * uploaded once into a texture of its own, through the game's own NativeImage upload so the texels are exactly what the game would
 * write. From then on each frame change is a GPU-to-GPU copy (glCopyImageSubData) into the atlas instead of a glTexSubImage2D from
 * client memory. Interpolated frames change every tick and go through {@link PboUpload1201}. The cache of a sprite dies with it
 * (resource reload, atlas rebuild or resize), so F3+T and mipmap changes rebuild it from the new sprites.
 */
public final class AnimationCache1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    /** Video memory the frame strips may take in all; sprites past it keep the game's own uploads. */
    private static final long BUDGET = 256L << 20;
    /** -1 unknown, 0 unsupported (vanilla uploads), 1 GL 4.3 core, 2 ARB_copy_image. */
    private static int copyPath = -1;
    private static long used;
    /** The atlas whose animations are ticking now (TextureAtlas.cycleAnimationFrames), 0 outside it. */
    public static int cyclingAtlas;

    private AnimationCache1201() {}

    /**
     * SpriteContents.upload during an atlas tick with the sprite's own frame strip: copies the frame at (frameX, frameY) of the strip
     * to (x, y) of the atlas on every mip level. Returns the sprite's cache texture (to remember), -1 when this sprite stays on the
     * game's uploads, and 0 when nothing was done (the caller lets the game upload).
     */
    public static int copy(int cache, int spriteAtlas, NativeImage[] strip, int width, int height, int x, int y, int frameX, int frameY) {
        if (cache < 0 || !BuiltInRender.animations || spriteAtlas == 0 || spriteAtlas != cyclingAtlas || !supported()) return 0;
        boolean fresh = cache == 0;
        if (fresh) {
            for (int i = 0; i < 16 && GL11C.glGetError() != 0; i++) {} // someone else's stale errors must not be read as ours below
            if ((cache = build(strip)) <= 0) return -1;
        }
        for (int level = 0; level < strip.length; level++) {
            int w = width >> level, h = height >> level;
            if (w <= 0 || h <= 0) continue;
            if (copyPath == 1) GL43C.glCopyImageSubData(cache, GL11C.GL_TEXTURE_2D, level, frameX >> level, frameY >> level, 0,
                spriteAtlas, GL11C.GL_TEXTURE_2D, level, x >> level, y >> level, 0, w, h, 1);
            else ARBCopyImage.glCopyImageSubData(cache, GL11C.GL_TEXTURE_2D, level, frameX >> level, frameY >> level, 0,
                spriteAtlas, GL11C.GL_TEXTURE_2D, level, x >> level, y >> level, 0, w, h, 1);
        }
        if (fresh) {
            int error = GL11C.glGetError();
            if (error != 0) { // the copy did not happen: stop for the session, the game's uploads take over from this frame
                LOGGER.warn("Lads Kerria: animation cache off, glCopyImageSubData failed (GL error {}); using the game's texture uploads", error);
                copyPath = 0;
                free(cache, strip);
                return 0;
            }
        }
        BuiltInRender.framesCopied++;
        return cache;
    }

    /** SpriteContents.close: the sprite's frame strip texture goes with it. */
    public static void free(int cache, NativeImage[] strip) {
        if (cache <= 0) return;
        TextureUtil.releaseTextureId(cache);
        used -= bytes(strip);
    }

    /** The whole strip, all mip levels, in a texture laid out like the atlas's (same internal format, so copies are bit exact). */
    private static int build(NativeImage[] strip) {
        long size = bytes(strip);
        if (used + size > BUDGET) return -1;
        int texture = TextureUtil.generateTextureId();
        TextureUtil.prepareImage(texture, strip.length - 1, strip[0].getWidth(), strip[0].getHeight());
        for (int level = 0; level < strip.length; level++)
            strip[level].upload(level, 0, 0, 0, 0, strip[level].getWidth(), strip[level].getHeight(), strip.length > 1, false);
        GlStateManager._bindTexture(cyclingAtlas); // the atlas stays bound for the rest of its tick, as the game left it
        used += size;
        return texture;
    }

    private static long bytes(NativeImage[] strip) {
        long total = 0;
        for (NativeImage image : strip) total += 4L * image.getWidth() * image.getHeight();
        return total;
    }

    private static boolean supported() {
        if (copyPath < 0) {
            GLCapabilities caps = GL.getCapabilities();
            copyPath = caps.OpenGL43 ? 1 : caps.GL_ARB_copy_image ? 2 : 0;
            if (copyPath == 0) LOGGER.info("Lads Kerria: animation cache off, the driver has neither OpenGL 4.3 nor ARB_copy_image");
        }
        return copyPath > 0;
    }

    /** One line for the log and the QA report. */
    public static String describe() {
        return "animation cache " + (copyPath < 0 ? "not used yet" : copyPath == 0 ? "off" : copyPath == 1 ? "GL 4.3 copies" : "ARB_copy_image copies")
            + ", " + (used >> 10) + " KiB of frame strips; " + PboUpload1201.describe();
    }
}
