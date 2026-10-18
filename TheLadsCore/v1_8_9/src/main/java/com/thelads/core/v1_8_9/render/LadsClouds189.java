package com.thelads.core.v1_8_9.render;

import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;

/**
 * Fast fancy clouds (1.9.13, with Ionium): the same clouds as vanilla's fancy ones, drawn from far fewer fragments.
 * <p>
 * Vanilla (and Ionium's display-list version of it) draws the 64x64-texel window of the cloud texture around the camera as whole
 * 8x8-texel quads, top and bottom, plus a side quad along every texel row in both directions, and lets the alpha test throw away
 * the empty texels; only a fifth of vanilla's clouds.png is cloud, so most of what the GPU rasterises, twice (depth pass and
 * colour pass), is discarded (~50 us of a 480 us frame on a GTX 1660 Ti). Here the lists hold only the cloud texels' top and bottom
 * faces (merged along rows) and the side faces where a cloud texel borders an empty one, on the sides vanilla draws for the
 * camera's position. Texture coordinates, colours, the two passes, blending, fog and anaglyph masks are vanilla's, so the picture is
 * the same; the internal faces it leaves out are always behind a boundary face of the same cloud. The window's geometry depends
 * on which texels it covers: it is rebuilt when the camera or the drift moves it by a texel (12 blocks, or ~20 s of drift) and when
 * the cloud texture changes (resource packs; an HD texture counts a texel as cloud when any of its pixels is).
 * Kill switch -Dlads.fastClouds=false (IoniumMixinPlugin189).
 */
public final class LadsClouds189 {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-1.8.9");
    private static final ResourceLocation CLOUDS = new ResourceLocation("textures/environment/clouds.png");
    private static final float TEXEL = 1.0F / 256.0F, EPSILON = 9.765625E-4F;
    private static final int BOTTOM = 0, TOP = 1, SIDES_X = 2, SIDES_Z = 3;
    /** The window: cells -3..4 of 8 texels around the camera's cell, as vanilla. */
    private static final int LOW = -24, SIZE = 64;
    /** Texture alpha that survives the 0.1 alpha test under the clouds' 0.8 vertex alpha. */
    private static final int MIN_ALPHA = 32;
    private static int lists = -1, texture = -1, builtX = Integer.MIN_VALUE, builtZ;
    private static final boolean[] solid = new boolean[256 * 256];
    private static boolean failed;
    /** QA (CoreProbe): frames drawn here, and the share of cloud texels in the texture (percent). */
    public static long frames;
    public static int cloudPercent = -1;

    private LadsClouds189() {}

    /** QA: an error turned the fast clouds off (Ionium's draw instead). */
    public static boolean failed() {
        return failed;
    }

    /** Ionium's FancyCloudRenderer.render, replaced; false leaves it to Ionium (an error turned this off). */
    public static boolean render(Minecraft mc, WorldClient world, int ticks, float partialTicks, int anaglyphPass) {
        if (failed) return false;
        try {
            draw(mc, world, ticks, partialTicks, anaglyphPass);
            frames++;
            return true;
        } catch (Throwable t) {
            failed = true;
            LOGGER.warn("Lads fast clouds off, Ionium's clouds draw instead: " + t);
            return false;
        }
    }

    private static void draw(Minecraft mc, WorldClient world, int ticks, float partialTicks, int anaglyphPass) {
        GlStateManager.disableCull();
        Entity camera = mc.getRenderViewEntity();
        float cameraY = (float) (camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partialTicks);
        double time = ticks + partialTicks;
        double cloudX = (camera.prevPosX + (camera.posX - camera.prevPosX) * partialTicks + time * 0.03F) / 12.0;
        double cloudZ = (camera.prevPosZ + (camera.posZ - camera.prevPosZ) * partialTicks) / 12.0 + 0.33F;
        float height = world.provider.getCloudHeight() - cameraY + 0.33F;
        cloudX -= MathHelper.floor_double(cloudX / 2048.0) * 2048;
        cloudZ -= MathHelper.floor_double(cloudZ / 2048.0) * 2048;
        int cellX = MathHelper.floor_double(cloudX), cellZ = MathHelper.floor_double(cloudZ);

        mc.getTextureManager().bindTexture(CLOUDS);
        ITextureObject cloudTexture = mc.getTextureManager().getTexture(CLOUDS);
        int id = cloudTexture == null ? -1 : cloudTexture.getGlTextureId();
        if (id != texture) {
            readTexture();
            texture = id;
            builtX = Integer.MIN_VALUE;
        }
        if (lists < 0) lists = GLAllocation.generateDisplayLists(4);
        if (builtX != (cellX & 255) || builtZ != (cellZ & 255)) build(cellX & 255, cellZ & 255);

        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        Vec3 color = world.getCloudColour(partialTicks);
        float red = (float) color.xCoord, green = (float) color.yCoord, blue = (float) color.zCoord;
        if (anaglyphPass != 2) {
            float gray = (red * 30.0F + green * 59.0F + blue * 11.0F) / 100.0F;
            float anaglyphGreen = (red * 30.0F + green * 70.0F) / 100.0F;
            float anaglyphBlue = (red * 30.0F + blue * 70.0F) / 100.0F;
            red = gray;
            green = anaglyphGreen;
            blue = anaglyphBlue;
        }
        GlStateManager.scale(12.0F, 1.0F, 12.0F);
        GlStateManager.translate(-(float) (cloudX - cellX), height, -(float) (cloudZ - cellZ));
        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        GlStateManager.pushMatrix();
        GlStateManager.translate(cellX * TEXEL, cellZ * TEXEL, 0.0F);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 0) GlStateManager.colorMask(false, false, false, false);
            else if (anaglyphPass == 0) GlStateManager.colorMask(false, true, true, true);
            else if (anaglyphPass == 1) GlStateManager.colorMask(true, false, false, true);
            else GlStateManager.colorMask(true, true, true, true);
            if (height > -5.0F) {
                GlStateManager.color(red * 0.7F, green * 0.7F, blue * 0.7F, 0.8F);
                GlStateManager.callList(lists + BOTTOM);
            }
            if (height <= 5.0F) {
                GlStateManager.color(red, green, blue, 0.8F);
                GlStateManager.callList(lists + TOP);
            }
            GlStateManager.color(red * 0.9F, green * 0.9F, blue * 0.9F, 0.8F);
            GlStateManager.callList(lists + SIDES_X);
            GlStateManager.color(red * 0.8F, green * 0.8F, blue * 0.8F, 0.8F);
            GlStateManager.callList(lists + SIDES_Z);
        }
        GlStateManager.matrixMode(GL11.GL_TEXTURE);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
    }

    /** Which of the 256x256 cloud texels have a pixel the alpha test keeps (the bound texture, any size). */
    private static void readTexture() {
        int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
        int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
        java.util.Arrays.fill(solid, false);
        if (w <= 0 || h <= 0) {
            java.util.Arrays.fill(solid, true); // unknown: every texel, as vanilla
            return;
        }
        ByteBuffer pixels = GLAllocation.createDirectByteBuffer(w * h * 4);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        int count = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((pixels.get((y * w + x) * 4 + 3) & 255) < MIN_ALPHA) continue;
                int i = (int) ((long) y * 256 / h) * 256 + (int) ((long) x * 256 / w);
                if (!solid[i]) count++;
                solid[i] = true;
            }
        }
        cloudPercent = count * 100 / 65536;
        LOGGER.info("Lads fast clouds: " + w + "x" + h + " cloud texture, " + cloudPercent + "% cloud");
    }

    private static boolean cloud(int baseX, int baseZ, int x, int z) {
        return solid[((baseZ + z) & 255) * 256 + ((baseX + x) & 255)];
    }

    /** The four lists for the window whose cell origin is texel baseX, baseZ. */
    private static void build(int baseX, int baseZ) {
        builtX = baseX;
        builtZ = baseZ;
        for (int face = BOTTOM; face <= TOP; face++) {
            GL11.glNewList(lists + face, GL11.GL_COMPILE);
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glNormal3f(0.0F, face == BOTTOM ? -1.0F : 1.0F, 0.0F);
            float y = face == BOTTOM ? 0.0F : 4.0F - EPSILON;
            for (int z = LOW; z < LOW + SIZE; z++) {
                for (int x = LOW; x < LOW + SIZE; x++) {
                    if (!cloud(baseX, baseZ, x, z)) continue;
                    int end = x + 1;
                    while (end < LOW + SIZE && cloud(baseX, baseZ, end, z)) end++;
                    vertex(x, y, z + 1, x * TEXEL, (z + 1) * TEXEL);
                    vertex(end, y, z + 1, end * TEXEL, (z + 1) * TEXEL);
                    vertex(end, y, z, end * TEXEL, z * TEXEL);
                    vertex(x, y, z, x * TEXEL, z * TEXEL);
                    x = end;
                }
            }
            GL11.glEnd();
            GL11.glEndList();
        }
        // Sides: vanilla draws the -X faces of the cells from the camera's on (x >= 0) and the +X faces up to the next but one
        // (x < 16), the faces that can face the camera; here only where a cloud texel meets an empty one. Same for Z.
        GL11.glNewList(lists + SIDES_X, GL11.GL_COMPILE);
        GL11.glBegin(GL11.GL_QUADS);
        for (int x = LOW; x < LOW + SIZE; x++) {
            float u = (x + 0.5F) * TEXEL;
            for (int side = -1; side <= 1; side += 2) {
                if (side < 0 ? x < 0 : x >= 16) continue;
                GL11.glNormal3f(side, 0.0F, 0.0F);
                float plane = side < 0 ? x : x + 1.0F - EPSILON;
                for (int z = LOW; z < LOW + SIZE; z++) {
                    if (!edge(baseX, baseZ, x, z, side, 0)) continue;
                    int end = z + 1;
                    while (end < LOW + SIZE && edge(baseX, baseZ, x, end, side, 0)) end++;
                    vertex(plane, 0.0F, end, u, end * TEXEL);
                    vertex(plane, 4.0F, end, u, end * TEXEL);
                    vertex(plane, 4.0F, z, u, z * TEXEL);
                    vertex(plane, 0.0F, z, u, z * TEXEL);
                    z = end;
                }
            }
        }
        GL11.glEnd();
        GL11.glEndList();
        GL11.glNewList(lists + SIDES_Z, GL11.GL_COMPILE);
        GL11.glBegin(GL11.GL_QUADS);
        for (int z = LOW; z < LOW + SIZE; z++) {
            float v = (z + 0.5F) * TEXEL;
            for (int side = -1; side <= 1; side += 2) {
                if (side < 0 ? z < 0 : z >= 16) continue;
                GL11.glNormal3f(0.0F, 0.0F, side);
                float plane = side < 0 ? z : z + 1.0F - EPSILON;
                for (int x = LOW; x < LOW + SIZE; x++) {
                    if (!edge(baseX, baseZ, x, z, 0, side)) continue;
                    int end = x + 1;
                    while (end < LOW + SIZE && edge(baseX, baseZ, end, z, 0, side)) end++;
                    vertex(x, 4.0F, plane, x * TEXEL, v);
                    vertex(end, 4.0F, plane, end * TEXEL, v);
                    vertex(end, 0.0F, plane, end * TEXEL, v);
                    vertex(x, 0.0F, plane, x * TEXEL, v);
                    x = end;
                }
            }
        }
        GL11.glEnd();
        GL11.glEndList();
    }

    /** A cloud texel whose neighbour in direction (dx, dz) is empty. */
    private static boolean edge(int baseX, int baseZ, int x, int z, int dx, int dz) {
        return cloud(baseX, baseZ, x, z) && !cloud(baseX, baseZ, x + dx, z + dz);
    }

    private static void vertex(float x, float y, float z, float u, float v) {
        GL11.glTexCoord2f(u, v);
        GL11.glVertex3f(x, y, z);
    }
}
