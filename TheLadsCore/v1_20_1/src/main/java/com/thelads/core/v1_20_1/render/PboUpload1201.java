package com.thelads.core.v1_20_1.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.perf.BuiltInRender;
import com.thelads.core.v1_20_1.render.mixin.NativeImageAnimationAccess;
import org.lwjgl.opengl.ARBBufferStorage;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL21C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL32C;
import org.lwjgl.opengl.GL44C;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lads Kerria, fast uploads (1.8.5): animation frames the cache does not hold (interpolated frames, which change every tick, and
 * sprites past its budget) are written into a persistently mapped pixel buffer and uploaded from there, so the driver reads them
 * asynchronously instead of copying client memory on the spot. A ring of three fenced segments, one per atlas tick, keeps the CPU
 * from overwriting bytes the GPU has not read yet. Needs OpenGL 4.4 or ARB_buffer_storage; without it the game uploads as before.
 */
public final class PboUpload1201 {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");
    private static final int SEGMENTS = 3, SEGMENT = 1 << 20;
    private static final int FLAGS = GL30C.GL_MAP_WRITE_BIT | GL44C.GL_MAP_PERSISTENT_BIT | GL44C.GL_MAP_COHERENT_BIT;
    private static final long[] FENCES = new long[SEGMENTS];
    /** -1 unknown, 0 off, 1 on. */
    private static int state = -1;
    private static int buffer, segment, offset;
    private static long address;
    private static boolean wrote;

    private PboUpload1201() {}

    /**
     * During an atlas tick, with the atlas bound: uploads the (width x height) >> level region at (frameX, frameY) >> level of each
     * image to (x, y) >> level of the atlas. False when the game should upload it itself.
     */
    public static boolean upload(NativeImage[] images, int width, int height, int x, int y, int frameX, int frameY) {
        if (!BuiltInRender.animations || AnimationCache1201.cyclingAtlas == 0 || !ready()) return false;
        long total = 0;
        for (int level = 0; level < images.length; level++) {
            if (images[level].format() != NativeImage.Format.RGBA) return false;
            total += 4L * Math.max(0, width >> level) * Math.max(0, height >> level);
        }
        if (offset + total > SEGMENT) return false; // this tick's segment is full: the rest go the game's way
        if (!wrote) awaitSegment();
        GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, buffer);
        try {
            GL11C.glPixelStorei(GL11C.GL_UNPACK_ROW_LENGTH, 0);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_PIXELS, 0);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_SKIP_ROWS, 0);
            GL11C.glPixelStorei(GL11C.GL_UNPACK_ALIGNMENT, 4);
            for (int level = 0; level < images.length; level++) {
                int w = width >> level, h = height >> level;
                if (w <= 0 || h <= 0) continue;
                NativeImage image = images[level];
                long source = ((NativeImageAnimationAccess) (Object) image).lads$pixels();
                if (source == 0) throw new IllegalStateException("image is closed");
                long at = (long) segment * SEGMENT + offset;
                int stride = image.getWidth() * 4, row = w * 4;
                long first = source + ((long) (frameY >> level) * image.getWidth() + (frameX >> level)) * 4;
                if (stride == row) MemoryUtil.memCopy(first, address + at, (long) row * h);
                else for (int r = 0; r < h; r++) MemoryUtil.memCopy(first + (long) r * stride, address + at + (long) r * row, row);
                GL11C.glTexSubImage2D(GL11C.GL_TEXTURE_2D, level, x >> level, y >> level, w, h, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, at);
                offset += row * h;
            }
        } finally {
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, 0); // client-memory uploads elsewhere must never read our buffer
        }
        wrote = true;
        BuiltInRender.framesUploaded++;
        return true;
    }

    /** End of an atlas tick: fences what this tick wrote and moves on to the next segment. */
    public static void endTick() {
        if (!wrote) return;
        FENCES[segment] = GL32C.glFenceSync(GL32C.GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        segment = (segment + 1) % SEGMENTS;
        offset = 0;
        wrote = false;
    }

    private static void awaitSegment() {
        long fence = FENCES[segment];
        if (fence == 0) return;
        GL32C.glClientWaitSync(fence, GL32C.GL_SYNC_FLUSH_COMMANDS_BIT, 1_000_000_000L);
        GL32C.glDeleteSync(fence);
        FENCES[segment] = 0;
    }

    private static boolean ready() {
        if (state >= 0) return state == 1;
        state = 0;
        GLCapabilities caps = GL.getCapabilities();
        if (!caps.OpenGL44 && !caps.GL_ARB_buffer_storage) {
            LOGGER.info("Lads Kerria: mapped-buffer uploads off, the driver has neither OpenGL 4.4 nor ARB_buffer_storage");
            return false;
        }
        buffer = GL15C.glGenBuffers();
        GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, buffer);
        if (caps.OpenGL44) GL44C.glBufferStorage(GL21C.GL_PIXEL_UNPACK_BUFFER, (long) SEGMENTS * SEGMENT, FLAGS);
        else ARBBufferStorage.glBufferStorage(GL21C.GL_PIXEL_UNPACK_BUFFER, (long) SEGMENTS * SEGMENT, FLAGS);
        address = GL30C.nglMapBufferRange(GL21C.GL_PIXEL_UNPACK_BUFFER, 0, (long) SEGMENTS * SEGMENT, FLAGS);
        GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER, 0);
        if (address == 0) {
            LOGGER.warn("Lads Kerria: mapped-buffer uploads off, the pixel buffer could not be mapped (GL error {})", GL11C.glGetError());
            GL15C.glDeleteBuffers(buffer);
            return false;
        }
        state = 1;
        return true;
    }

    static String describe() {
        return "mapped-buffer uploads " + (state < 0 ? "not used yet" : state == 0 ? "off" : "on (" + SEGMENTS + " x " + (SEGMENT >> 10) + " KiB)");
    }
}
