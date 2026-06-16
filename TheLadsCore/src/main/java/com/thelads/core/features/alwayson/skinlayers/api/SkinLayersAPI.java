package com.thelads.core.features.alwayson.skinlayers.api;

import com.mojang.blaze3d.platform.NativeImage;
import com.thelads.core.features.alwayson.skinlayers.render.CustomizableCubeListBuilder;
import com.thelads.core.features.alwayson.skinlayers.render.CustomizableModelPart;
import com.thelads.core.features.alwayson.skinlayers.util.NMSWrapper;
import com.thelads.core.features.alwayson.skinlayers.versionless.util.wrapper.SolidPixelWrapper;
import java.util.Collections;
import net.minecraft.client.player.AbstractClientPlayer;

public final class SkinLayersAPI {
    private static final MeshHelper meshHelper = new MeshHelperImplementation();
    private static final MeshProvider meshProvider = new MeshProviderImplementation();
    private static MeshTransformerProvider meshTransformerProvider = MeshTransformerProvider.EMPTY_PROVIDER;
    private static BoxBuilder boxBuilder = BoxBuilder.DEFAULT;

    public static void setupBoxBuilder(BoxBuilder builder) {
        boxBuilder = builder;
    }

    public static void setupMeshTransformerProvider(MeshTransformerProvider provider) {
        meshTransformerProvider = provider;
    }

    public static MeshTransformerProvider getMeshTransformerProvider() {
        return meshTransformerProvider;
    }

    private SkinLayersAPI() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static MeshHelper getMeshHelper() {
        return meshHelper;
    }

    public static MeshProvider getMeshProvider() {
        return meshProvider;
    }

    public static BoxBuilder getBoxBuilder() {
        return boxBuilder;
    }

    private static class MeshHelperImplementation implements MeshHelper {
        private MeshHelperImplementation() {
        }

        @Override
        public Mesh create3DMesh(NativeImage natImage, int width, int height, int depth, int textureU, int textureV, boolean topPivot, float rotationOffset, boolean mirror) {
            CustomizableCubeListBuilder builder = new CustomizableCubeListBuilder();
            builder.mirror(mirror);
            if (SolidPixelWrapper.wrapBox(builder, new NMSWrapper.WrappedNativeImage(natImage), width, height, depth, textureU, textureV, topPivot, rotationOffset) != null) {
                return new CustomizableModelPart(builder.getVanillaCubes(), builder.getCubes(), Collections.emptyMap());
            }
            return Mesh.EMPTY;
        }

        @Override
        public Mesh create3DMesh(NativeImage natImage, int width, int height, int depth, int textureU, int textureV, boolean topPivot, float rotationOffset) {
            return this.create3DMesh(natImage, width, height, depth, textureU, textureV, topPivot, rotationOffset, false);
        }
    }

    private static class MeshProviderImplementation implements MeshProvider {
        private MeshProviderImplementation() {
        }

        @Override
        public PlayerData getPlayerMesh(AbstractClientPlayer abstractClientPlayerEntity) {
            if (abstractClientPlayerEntity instanceof PlayerData) {
                return (PlayerData) abstractClientPlayerEntity;
            }
            return null;
        }
    }
}
