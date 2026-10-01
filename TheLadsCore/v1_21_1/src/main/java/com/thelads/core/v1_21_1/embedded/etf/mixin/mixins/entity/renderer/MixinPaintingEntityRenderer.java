package com.thelads.core.v1_21_1.embedded.etf.mixin.mixins.entity.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.PaintingRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.PaintingVariant;


import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.thelads.core.v1_21_1.embedded.etf.ETF;
import com.thelads.core.v1_21_1.embedded.etf.features.ETFManager;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFState;
import com.thelads.core.v1_21_1.embedded.etf.features.state.HoldsETFRenderState;
import com.thelads.core.v1_21_1.embedded.etf.features.texture_handlers.ETFSprite;
import com.thelads.core.v1_21_1.embedded.etf.features.texture_handlers.ETFTexture;
import com.thelads.core.v1_21_1.embedded.etf.utils.ETFEntity;
import com.thelads.core.v1_21_1.embedded.etf.utils.ETFUtils2;

@Mixin(PaintingRenderer.class)
public abstract class MixinPaintingEntityRenderer extends EntityRenderer<Painting> {


    @Unique
    private void uVertex(final PoseStack.Pose matrix, final VertexConsumer vertexConsumer, final float x, final float y, final float u, final float v, final float z, final int normalX, final int normalY, final int normalZ, final int light) {
        vertex(
                matrix
                , vertexConsumer, x, y, u, v, z, normalX, normalY, normalZ, light);
    }

    @Shadow
    protected abstract void vertex(final PoseStack.Pose matrix, final VertexConsumer vertexConsumer, final float x, final float y, final float u, final float v, final float z, final int normalX, final int normalY, final int normalZ, final int light);
    @Unique
    private static final ResourceLocation etf$BACK_SPRITE_ID = ETFUtils2.res("textures/painting/back.png");

    @SuppressWarnings("unused")
    protected MixinPaintingEntityRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }


    @Inject(method = "render(Lnet/minecraft/world/entity/decoration/Painting;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "HEAD"), cancellable = true)
    private void etf$getSprites(Painting paintingEntity, float f, float g, PoseStack matrixStack, net.minecraft.client.renderer.MultiBufferSource vertexConsumerProvider, int i, CallbackInfo ci) {

        ETFEntityRenderState etfEntity =
                ETFEntityRenderState.forEntity((ETFEntity) paintingEntity);


        try {
            TextureAtlasSprite paintingSprite = Minecraft.getInstance().getPaintingTextures().get(paintingEntity.getVariant().value());
            TextureAtlasSprite backSprite = Minecraft.getInstance().getPaintingTextures().getBackSprite();
            ResourceLocation paintingId = paintingSprite.contents().name();
            String paintingFileName = paintingId.getPath();
            ResourceLocation paintingTexture = ETFUtils2.res(paintingId.getNamespace(), "textures/painting/" + paintingFileName + ".png");


            boolean aztec = "aztec".equals(paintingFileName);
            if (aztec) ETFState.onlyRandomizeViaPropertiesFiles = true;

            ETFTexture frontTexture = ETFManager.getInstance().getETFTextureVariant(paintingTexture, etfEntity);
            ETFSprite etf$Sprite = frontTexture.getPaintingSprite(paintingSprite, paintingTexture);

            if (aztec) ETFState.onlyRandomizeViaPropertiesFiles = false;

            ETFTexture backTexture = ETFManager.getInstance().getETFTextureVariant(etf$BACK_SPRITE_ID, etfEntity);
            ETFSprite etf$BackSprite = backTexture.getPaintingSprite(backSprite, etf$BACK_SPRITE_ID);


            if (etf$Sprite.isETFAltered || etf$Sprite.isEmissive() || etf$BackSprite.isETFAltered || etf$BackSprite.isEmissive()) {
                matrixStack.pushPose();
                matrixStack.mulPose(Axis.YP.rotationDegrees(180.0F - f));
                PaintingVariant paintingVariant = paintingEntity.getVariant().value();


                int width =  paintingVariant.
                width()
                ;
                int height = paintingVariant.
                height()
                ;

                etf$renderETFPainting(matrixStack.last(),
                        vertexConsumerProvider,
                        paintingEntity,
                        width,
                        height,
                        etf$Sprite,
                        etf$BackSprite);
                matrixStack.popPose();
                    super.render(paintingEntity, f, g, matrixStack, vertexConsumerProvider, i);
                ci.cancel();
            }


        } catch (Exception e) {
            //ETFUtils2.logError("painting failed at "+paintingEntity.getBlockPos().toShortString());
        }

    }
    @Unique
    private void etf$renderETFPainting(PoseStack.Pose entry, net.minecraft.client.renderer.MultiBufferSource vertexConsumerProvider, Painting entity, int width, int height, ETFSprite ETFPaintingSprite, ETFSprite ETFBackSprite) {
        ETFState.pushRenderLayerModifyState(false);
        VertexConsumer vertexConsumerFront = vertexConsumerProvider.getBuffer(RenderType.entitySolid(ETFPaintingSprite.getSpriteVariant().atlasLocation()));
        etf$renderETFPaintingFront(entry, vertexConsumerFront, entity, width, height, ETFPaintingSprite.getSpriteVariant(), false);
    
        VertexConsumer vertexConsumerBack = vertexConsumerProvider.getBuffer(RenderType.entitySolid(ETFBackSprite.getSpriteVariant().atlasLocation()));
        etf$renderETFPaintingBack(entry, vertexConsumerBack, entity, width, height, ETFBackSprite.getSpriteVariant(), false);
    
        if (ETFPaintingSprite.isEmissive()) {
            vertexConsumerFront = vertexConsumerProvider.getBuffer(RenderType.entityTranslucent(ETFPaintingSprite.getEmissive().atlasLocation()));
            etf$renderETFPaintingFront(entry, vertexConsumerFront, entity, width, height, ETFPaintingSprite.getEmissive(), true);
        }
    
        if (ETFBackSprite.isEmissive()) {
            vertexConsumerFront = vertexConsumerProvider.getBuffer(RenderType.entityTranslucent(ETFBackSprite.getEmissive().atlasLocation()));
            etf$renderETFPaintingBack(entry, vertexConsumerFront, entity, width, height, ETFBackSprite.getEmissive(), true);
        }
        ETFState.popRenderLayerModifyState();
    }

    @Unique
    private void etf$renderETFPaintingFront(PoseStack.Pose entry, VertexConsumer vertexConsumerFront, Painting entity, int width, int height, TextureAtlasSprite paintingSprite, boolean emissive) {

//        PoseStack.Pose entry = matrices.last();
//        Matrix4f matrix4f = entry.getPositionMatrix();
//        Matrix3f matrix3f = entry.getNormalMatrix();

        float f = (float) (-width) / 2.0F;
        float g = (float) (-height) / 2.0F;
        int u = width;
        int v = height;

        double d = 1.0 / (double) u;
        double e = 1.0 / (double) v;

        for (int w = 0; w < u; ++w) {
            for (int x = 0; x < v; ++x) {
                float y = f + (float) ((w + 1));
                float z = f + (float) (w);
                float aa = g + (float) ((x + 1));
                float ab = g + (float) (x);

                int light;
                if (emissive) {
                    light = ETF.EMISSIVE_FEATURE_LIGHT_VALUE;
                } else {

                    float divider = 1F;

                    int ac = entity.getBlockX();
                    int ad = Mth.floor(entity.getY() + (double) ((aa + ab) / 2.0F / divider));
                    int ae = entity.getBlockZ();
                    Direction direction = entity.getDirection();
                    if (direction == Direction.NORTH) {
                        ac = Mth.floor(entity.getX() + (double) ((y + z) / 2.0F / divider));
                    }

                    if (direction == Direction.WEST) {
                        ae = Mth.floor(entity.getZ() - (double) ((y + z) / 2.0F / divider));
                    }

                    if (direction == Direction.SOUTH) {
                        ac = Mth.floor(entity.getX() - (double) ((y + z) / 2.0F / divider));
                    }

                    if (direction == Direction.EAST) {
                        ae = Mth.floor(entity.getZ() + (double) ((y + z) / 2.0F / divider));
                    }

                    light = LevelRenderer.getLightColor(entity.level(), new BlockPos(ac, ad, ae));
                }

                float zConst =
                0.03125F;

                float ag = paintingSprite.getU((float) (d * (double) (u - w)));
                float ah = paintingSprite.getU((float) (d * (double) (u - (w + 1))));
                float ai = paintingSprite.getV((float) (e * (double) (v - x)));
                float aj = paintingSprite.getV((float) (e * (double) (v - (x + 1))));
                this.uVertex(entry, vertexConsumerFront, y, ab, ah, ai, -zConst, 0, 0, -1, light);
                this.uVertex(entry, vertexConsumerFront, z, ab, ag, ai, -zConst, 0, 0, -1, light);
                this.uVertex(entry, vertexConsumerFront, z, aa, ag, aj, -zConst, 0, 0, -1, light);
                this.uVertex(entry, vertexConsumerFront, y, aa, ah, aj, -zConst, 0, 0, -1, light);

            }
        }

    }

    @Unique
    private void etf$renderETFPaintingBack(PoseStack.Pose entry, VertexConsumer vertexConsumerBack, Painting entity, int width, int height, TextureAtlasSprite backSprite, boolean emissive) {

//        PoseStack.Pose entry = matrices.last();
//        Matrix4f matrix4f = entry.getPositionMatrix();
//        Matrix3f matrix3f = entry.getNormalMatrix();


        float f = (float) (-width) / 2.0F;
        float g = (float) (-height) / 2.0F;
        //float h = 0.5F;
        float i = backSprite.getU0();
        float j = backSprite.getU1();
        float k = backSprite.getV0();
        float l = backSprite.getV1();
        float m = backSprite.getU0();
        float n = backSprite.getU1();
        float o = backSprite.getV0();
        float p = backSprite.getV(0.0625F);
        float q = backSprite.getU0();
        float r = backSprite.getU(0.0625F);
        float s = backSprite.getV0();
        float t = backSprite.getV1();
        int u = width;
        int v = height;

        for (int w = 0; w < u; ++w) {
            for (int x = 0; x < v; ++x) {
                float y = f + (float) ((w + 1));
                float z = f + (float) (w);
                float aa = g + (float) ((x + 1));
                float ab = g + (float) (x);

                int light;
                if (emissive) {
                    light = ETF.EMISSIVE_FEATURE_LIGHT_VALUE;
                } else {
                    float divider = 1F;

                    int ac = entity.getBlockX();
                    int ad = Mth.floor(entity.getY() + (double) ((aa + ab) / 2.0F / divider));
                    int ae = entity.getBlockZ();
                    Direction direction = entity.getDirection();
                    if (direction == Direction.NORTH) {
                        ac = Mth.floor(entity.getX() + (double) ((y + z) / 2.0F / divider));
                    }

                    if (direction == Direction.WEST) {
                        ae = Mth.floor(entity.getZ() - (double) ((y + z) / 2.0F / divider));
                    }

                    if (direction == Direction.SOUTH) {
                        ac = Mth.floor(entity.getX() - (double) ((y + z) / 2.0F / divider));
                    }

                    if (direction == Direction.EAST) {
                        ae = Mth.floor(entity.getZ() + (double) ((y + z) / 2.0F / divider));
                    }

                    light = LevelRenderer.getLightColor(entity.level(), new BlockPos(ac, ad, ae));
                }

                float zConst =
                        0.03125F;

                this.uVertex(entry, vertexConsumerBack, y, aa, j, k, zConst, 0, 0, 1, light);
                this.uVertex(entry, vertexConsumerBack, z, aa, i, k, zConst, 0, 0, 1, light);
                this.uVertex(entry, vertexConsumerBack, z, ab, i, l, zConst, 0, 0, 1, light);
                this.uVertex(entry, vertexConsumerBack, y, ab, j, l, zConst, 0, 0, 1, light);
                this.uVertex(entry, vertexConsumerBack, y, aa, m, o, -zConst, 0, 1, 0, light);
                this.uVertex(entry, vertexConsumerBack, z, aa, n, o, -zConst, 0, 1, 0, light);
                this.uVertex(entry, vertexConsumerBack, z, aa, n, p, zConst, 0, 1, 0, light);
                this.uVertex(entry, vertexConsumerBack, y, aa, m, p, zConst, 0, 1, 0, light);
                this.uVertex(entry, vertexConsumerBack, y, ab, m, o, zConst, 0, -1, 0, light);
                this.uVertex(entry, vertexConsumerBack, z, ab, n, o, zConst, 0, -1, 0, light);
                this.uVertex(entry, vertexConsumerBack, z, ab, n, p, -zConst, 0, -1, 0, light);
                this.uVertex(entry, vertexConsumerBack, y, ab, m, p, -zConst, 0, -1, 0, light);
                this.uVertex(entry, vertexConsumerBack, y, aa, r, s, zConst, -1, 0, 0, light);
                this.uVertex(entry, vertexConsumerBack, y, ab, r, t, zConst, -1, 0, 0, light);
                this.uVertex(entry, vertexConsumerBack, y, ab, q, t, -zConst, -1, 0, 0, light);
                this.uVertex(entry, vertexConsumerBack, y, aa, q, s, -zConst, -1, 0, 0, light);
                this.uVertex(entry, vertexConsumerBack, z, aa, r, s, -zConst, 1, 0, 0, light);
                this.uVertex(entry, vertexConsumerBack, z, ab, r, t, -zConst, 1, 0, 0, light);
                this.uVertex(entry, vertexConsumerBack, z, ab, q, t, zConst, 1, 0, 0, light);
                this.uVertex(entry, vertexConsumerBack, z, aa, q, s, zConst, 1, 0, 0, light);
            }
        }

    }


}


