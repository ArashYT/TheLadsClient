package com.thelads.core.v1_8_9.gui;

import com.thelads.core.client.LayerVoxels;
import com.thelads.core.v1_8_9.feature.ebe.BakedBlockEntities189;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.model.PositionTextureVertex;
import net.minecraft.client.model.TexturedQuad;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/**
 * The outer layers of one player model (hat, jacket, sleeves, trousers) as 3D boxes, one per opaque pixel of the skin, for the Skin
 * Customization preview (26.3's PreviewSkinLayers; LayerVoxels does the geometry). The skin's pixels are read back from its texture, so
 * any skin works, and one display list per layer is made when the texture changes.
 */
final class SkinVoxels189 {
    private static final float UNIT = 0.0625f;
    private final ModelRenderer[] layers;
    private final int[] lists = new int[6];
    private ResourceLocation builtFor;
    private int boxes;

    SkinVoxels189(ModelPlayer model) {
        layers = new ModelRenderer[] {model.bipedHeadwear, model.bipedBodyWear, model.bipedLeftArmwear, model.bipedRightArmwear,
            model.bipedLeftLegwear, model.bipedRightLegwear};
    }

    ModelRenderer layer(int index) {
        return layers[index];
    }

    int list(int index) {
        return lists[index];
    }

    /** How many boxes the six layers have. */
    int boxes() {
        return boxes;
    }

    /** Makes the layers of this skin texture (once per texture); false while the texture is not a loaded 64 x 64 skin. */
    boolean prepare(Minecraft mc, ResourceLocation texture) {
        if (texture.equals(builtFor)) return true;
        mc.getTextureManager().bindTexture(texture);
        if (GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH) != 64
            || GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT) != 64) return false;
        IntBuffer pixels = BufferUtils.createIntBuffer(64 * 64);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, 32993, 33639, pixels); // BGRA, 8888 reversed: one int per pixel, as ARGB
        int[] argb = new int[64 * 64];
        pixels.get(argb);
        release();
        boxes = 0;
        List<LayerVoxels.Box> layerBoxes = new ArrayList<>();
        for (int i = 0; i < layers.length; i++) {
            layerBoxes.clear();
            for (ModelBox box : layers[i].cubeList) {
                float[] center = {(box.posX1 + box.posX2) / 2, (box.posY1 + box.posY2) / 2, (box.posZ1 + box.posZ2) / 2};
                for (TexturedQuad quad : ((BakedBlockEntities189.ModelBoxQuads) box).lads$quads()) {
                    float[][] face = new float[4][];
                    for (int corner = 0; corner < 4; corner++) {
                        PositionTextureVertex vertex = quad.vertexPositions[corner];
                        // the quad's texture coordinates carry a tenth of a pixel of bleed guard: whole pixels again
                        face[corner] = new float[] {(float) vertex.vector3D.xCoord, (float) vertex.vector3D.yCoord, (float) vertex.vector3D.zCoord,
                            Math.round(vertex.texturePositionX * 64), Math.round(vertex.texturePositionY * 64)};
                    }
                    LayerVoxels.extrude(face, center, argb, 64, 64, layerBoxes);
                }
            }
            boxes += layerBoxes.size();
            lists[i] = compile(layerBoxes);
        }
        builtFor = texture;
        return true;
    }

    private static int compile(List<LayerVoxels.Box> boxes) {
        int list = GLAllocation.generateDisplayLists(1);
        GL11.glNewList(list, GL11.GL_COMPILE);
        WorldRenderer renderer = Tessellator.getInstance().getWorldRenderer();
        renderer.begin(7, DefaultVertexFormats.OLDMODEL_POSITION_TEX_NORMAL);
        for (LayerVoxels.Box box : boxes) {
            double u = (box.px() + 0.5) / 64, v = (box.py() + 0.5) / 64; // every face shows the middle of its pixel
            float x0 = box.x() * UNIT, y0 = box.y() * UNIT, z0 = box.z() * UNIT;
            float x1 = (box.x() + box.dx()) * UNIT, y1 = (box.y() + box.dy()) * UNIT, z1 = (box.z() + box.dz()) * UNIT;
            quad(renderer, u, v, 0, 0, -1, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
            quad(renderer, u, v, 0, 0, 1, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1);
            quad(renderer, u, v, -1, 0, 0, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1);
            quad(renderer, u, v, 1, 0, 0, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
            quad(renderer, u, v, 0, -1, 0, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0);
            quad(renderer, u, v, 0, 1, 0, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        }
        Tessellator.getInstance().draw();
        GL11.glEndList();
        return list;
    }

    private static void quad(WorldRenderer renderer, double u, double v, float nx, float ny, float nz, float... corners) {
        for (int i = 0; i < 12; i += 3) renderer.pos(corners[i], corners[i + 1], corners[i + 2]).tex(u, v).normal(nx, ny, nz).endVertex();
    }

    /** Frees the display lists (a new texture or the screen closing). */
    void release() {
        for (int i = 0; i < lists.length; i++)
            if (lists[i] != 0) {
                GLAllocation.deleteDisplayLists(lists[i]);
                lists[i] = 0;
            }
        builtFor = null;
        boxes = 0;
    }
}
