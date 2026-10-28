package com.thelads.core.v1_8_9.render;

import com.thelads.core.perf.BuiltInRender;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.util.EnumFacing;
import org.lwjgl.util.vector.Vector3f;

/**
 * 1.8.9 2D GUI item quad culling: generated 2D item models (ItemModelGenerator / Forge ItemLayerModel) contain
 * 60-120 thin edge quads plus north/south faces. When rendered flat in the orthographic GUI (!model.isGui3d()),
 * only EnumFacing.SOUTH quads face the screen. Caching a GUI-filtered IBakedModel per source model cuts GUI item
 * and enchantment-glint vertex counts by ~95% without reading GL matrices.
 */
public final class Flerovium189 {
    private static final Map<IBakedModel, GuiBakedModel> GUI_CACHE =
        Collections.synchronizedMap(new WeakHashMap<IBakedModel, GuiBakedModel>());

    private static boolean inGui;

    static {
        BuiltInRender.standDownReason(BuiltInRender.FLEROVIUM);
    }

    private Flerovium189() {}

    public static void enterGui() {
        inGui = true;
    }

    public static void exitGui() {
        inGui = false;
    }

    public static IBakedModel wrapGuiModel(IBakedModel model) {
        if (!inGui || !BuiltInRender.items || model == null || model instanceof GuiBakedModel) {
            return model;
        }
        if (model.isGui3d() || model.isBuiltInRenderer()) {
            return model;
        }
        ItemCameraTransforms transforms = model.getItemCameraTransforms();
        if (transforms != null && transforms.gui != null) {
            Vector3f rot = transforms.gui.rotation;
            if (rot != null && (rot.x != 0.0f || rot.y != 0.0f)) {
                return model;
            }
        }
        GuiBakedModel cached = GUI_CACHE.get(model);
        if (cached == null) {
            cached = buildGuiModel(model);
            GUI_CACHE.put(model, cached);
        }
        BuiltInRender.quadsKept += cached.keptCount;
        BuiltInRender.quadsCulled += cached.culledCount;
        return cached.passthrough ? model : cached;
    }

    private static GuiBakedModel buildGuiModel(IBakedModel model) {
        int originalTotal = 0;
        for (EnumFacing facing : EnumFacing.values()) {
            List<BakedQuad> fq = model.getFaceQuads(facing);
            if (fq != null) originalTotal += fq.size();
        }
        List<BakedQuad> southFace = model.getFaceQuads(EnumFacing.SOUTH);
        List<BakedQuad> keptFace = (southFace == null || southFace.isEmpty())
            ? Collections.<BakedQuad>emptyList()
            : southFace;

        List<BakedQuad> general = model.getGeneralQuads();
        List<BakedQuad> keptGeneral;
        if (general == null || general.isEmpty()) {
            keptGeneral = Collections.emptyList();
        } else {
            originalTotal += general.size();
            List<BakedQuad> filtered = new ArrayList<BakedQuad>(4);
            for (int i = 0, n = general.size(); i < n; i++) {
                BakedQuad quad = general.get(i);
                if (quad.getFace() == EnumFacing.SOUTH) {
                    filtered.add(quad);
                }
            }
            keptGeneral = filtered.isEmpty() ? Collections.<BakedQuad>emptyList() : filtered;
        }

        int keptTotal = keptFace.size() + keptGeneral.size();
        if (keptTotal == 0 && originalTotal > 0) {
            return new GuiBakedModel(model, Collections.<BakedQuad>emptyList(), Collections.<BakedQuad>emptyList(), originalTotal, 0, true);
        }
        return new GuiBakedModel(model, keptFace, keptGeneral, keptTotal, Math.max(0, originalTotal - keptTotal), false);
    }

    /**
     * Keeps copies of the source model's properties, never the model itself: GUI_CACHE holds its values strongly, so a value
     * that referenced its weak key would keep every model ever drawn (and its quads) alive across resource reloads.
     */
    private static final class GuiBakedModel implements IBakedModel {
        private final boolean ambientOcclusion, gui3d, builtInRenderer;
        private final TextureAtlasSprite particleTexture;
        private final ItemCameraTransforms cameraTransforms;
        private final List<BakedQuad> southFaceQuads;
        private final List<BakedQuad> southGeneralQuads;
        final int keptCount;
        final int culledCount;
        final boolean passthrough;

        GuiBakedModel(IBakedModel delegate, List<BakedQuad> southFaceQuads, List<BakedQuad> southGeneralQuads,
                      int keptCount, int culledCount, boolean passthrough) {
            this.ambientOcclusion = delegate.isAmbientOcclusion();
            this.gui3d = delegate.isGui3d();
            this.builtInRenderer = delegate.isBuiltInRenderer();
            this.particleTexture = delegate.getParticleTexture();
            this.cameraTransforms = delegate.getItemCameraTransforms();
            this.southFaceQuads = southFaceQuads;
            this.southGeneralQuads = southGeneralQuads;
            this.keptCount = keptCount;
            this.culledCount = culledCount;
            this.passthrough = passthrough;
        }

        @Override
        public List<BakedQuad> getFaceQuads(EnumFacing side) {
            return side == EnumFacing.SOUTH ? southFaceQuads : Collections.<BakedQuad>emptyList();
        }

        @Override
        public List<BakedQuad> getGeneralQuads() {
            return southGeneralQuads;
        }

        @Override
        public boolean isAmbientOcclusion() {
            return ambientOcclusion;
        }

        @Override
        public boolean isGui3d() {
            return gui3d;
        }

        @Override
        public boolean isBuiltInRenderer() {
            return builtInRenderer;
        }

        @Override
        public TextureAtlasSprite getParticleTexture() {
            return particleTexture;
        }

        @Override
        public ItemCameraTransforms getItemCameraTransforms() {
            return cameraTransforms;
        }
    }
}
