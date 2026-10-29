package com.thelads.core.v1_8_9.render;

import com.thelads.core.perf.BuiltInRender;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.util.EnumFacing;
import org.junit.Assert;
import org.junit.Test;

public class Flerovium189Test {

    /** The GUI cache is weak on its source models: the models a resource reload replaces must stay collectable. */
    @Test
    public void cachedGuiModelLetsItsSourceModelBeCollected() throws InterruptedException {
        Assert.assertNull("Flerovium must be on in tests", BuiltInRender.standDownReason(BuiltInRender.FLEROVIUM));
        WeakReference<IBakedModel> source = cacheOne();
        for (int i = 0; i < 50 && source.get() != null; i++) {
            System.gc();
            Thread.sleep(10);
        }
        Assert.assertNull("the GUI model cache kept its source model alive", source.get());
    }

    private static WeakReference<IBakedModel> cacheOne() {
        IBakedModel model = new FlatModel();
        Flerovium189.enterGui();
        try {
            Assert.assertNotSame("a flat model is cached and wrapped", model, Flerovium189.wrapGuiModel(model));
        } finally {
            Flerovium189.exitGui();
        }
        return new WeakReference<IBakedModel>(model);
    }

    private static final class FlatModel implements IBakedModel {
        @Override public List<BakedQuad> getFaceQuads(EnumFacing side) { return Collections.emptyList(); }
        @Override public List<BakedQuad> getGeneralQuads() { return Collections.emptyList(); }
        @Override public boolean isAmbientOcclusion() { return false; }
        @Override public boolean isGui3d() { return false; }
        @Override public boolean isBuiltInRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleTexture() { return null; }
        @Override public ItemCameraTransforms getItemCameraTransforms() { return null; }
    }
}
