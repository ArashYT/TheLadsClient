package com.thelads.core.v26_2.embedded.emf.models.parts;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.renderer.*;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import com.thelads.core.v26_2.embedded.emf.EMF;
import com.thelads.core.v26_2.embedded.emf.config.EMFConfig;
import com.thelads.core.v26_2.embedded.emf.mod_compat.IrisShadowPassDetection;
import com.thelads.core.v26_2.embedded.emf.models.animation.EMFAttachment;
import com.thelads.core.v26_2.embedded.emf.models.animation.math.EMFMath;
import com.thelads.core.v26_2.embedded.emf.models.animation.state.EMFState;
import com.thelads.core.v26_2.embedded.emf.utils.EMFUtils;
import com.thelads.core.v26_2.embedded.emf.utils.IEMFCuboidDataSupplier;
import com.thelads.core.v26_2.embedded.etf.ETF;
import com.thelads.core.v26_2.embedded.etf.features.state.ETFState;
import com.thelads.core.v26_2.embedded.etf.features.texture_handlers.ETFTexture;
import com.thelads.core.v26_2.embedded.etf.utils.ETFUtils2;
import com.thelads.core.v26_2.embedded.etf.utils.ETFVertexConsumer;

import net.minecraft.util.ARGB;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

import java.util.*;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

import static com.thelads.core.v26_2.embedded.etf.ETF.EYES_FEATURE_LIGHT_VALUE;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.thelads.core.v26_2.embedded.etf.utils.URenderTypeToVertexConsumer;

public abstract class EMFModelPart extends ModelPart {
    public Identifier textureOverride;
//    protected long lastTextureOverride = -1L;

    public boolean isSetByAnimation = false;

    private final EMFModelPartRoot root;

    public EMFModelPartRoot getRoot() {
        return root;
    }

    public EMFModelPart(List<Cube> cuboids, Map<String, ModelPart> children, EMFModelPartRoot root) {
        super(cuboids, children);

        // re assert children and cuboids as modifiable
        // required for sodium post 0.5.4
        // this should not cause issues as emf does not allow these model parts to pass through sodium's unique renderer
        this.cubes = new ArrayList<>(cuboids);
        this.children = new HashMap<>(children);
        this.root = root;
    }

    protected @Nullable Consumer<PoseStack> getAttachmentPositioner(EMFAttachment.Type type) {
        for (ModelPart p : children.values()) {
            if (p instanceof EMFModelPart part) {
                var subConsumer = part.getAttachmentPositioner(type); // Only a part with the correct attachment will end this deep search
                if (subConsumer != null) {
                    return (stack) -> {
                        // Add this 'parent' to the cumulative stack transformation started by the child, and send it up the chain
                        this.translateAndRotate(stack);
                        subConsumer.accept(stack);
                    };
                }
            }
        }
        return null;
    }

    @Override
    public void render(final PoseStack matrices, final VertexConsumer vertices, final int light, final int overlay,
                       final int k
    ) {
        try {
            if (ETFState.currentModelPartDepth != 0) {
                renderWithTextureOverride(matrices, vertices, light, overlay,
                        k
                );
                return;
            }

            // This is the top level render

            var choice = EMF.config().getConfig().getRenderModeFor(EMFState.state());

            // Normal render
            if (choice == EMFConfig.RenderModeChoice.NORMAL) {
                renderWithTextureOverride(matrices, vertices, light, overlay,
                        k
                );
                return;
            }

            // We want debug rendering but can we?

            // Check if only render debug when hovered
            if (EMF.config().getConfig().onlyDebugRenderOnHover && !EMFMath.isClientHovered()) {
                renderWithTextureOverride(matrices, vertices, light, overlay,
                        k
                );
                return;
            }

            // Now lets debug render

            if (choice.allowsRegularDraw()) {
                renderWithTextureOverride(matrices, vertices, light, overlay,
                        k
                );
            }

            switch (choice) {
                case LINES ->{
                    if (vertices instanceof ETFVertexConsumer etfVertexConsumer) {
                        URenderTypeToVertexConsumer provider = etfVertexConsumer.etf$getProvider();
                        if (provider != null)
                            renderBoxes(matrices, provider.getBuffer(lines()), 1f);
                    }
                }
                case LINES_AND_TEXTURE -> {
                    if (vertices instanceof ETFVertexConsumer etfVertexConsumer) {
                        URenderTypeToVertexConsumer provider = etfVertexConsumer.etf$getProvider();
                        if (provider != null)
                            renderBoxes(matrices, provider.getBuffer(lines()), 1f);
                    }
                }
                case LINES_AND_TEXTURE_FLASH -> {
                    if (vertices instanceof ETFVertexConsumer etfVertexConsumer) {
                        URenderTypeToVertexConsumer provider = etfVertexConsumer.etf$getProvider();
                        if (provider != null) {
                            float flash = (Mth.sin(System.currentTimeMillis() / 1000f) + 1) / 2f;
                            renderBoxes(matrices, provider.getBuffer(lines()), flash);
                        }
                    }
                }
                case NONE -> {
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private RenderType lines() {
        return RenderTypes.lines();
    }

    private void renderDebugTinted(final PoseStack matrices, final VertexConsumer vertices, final int light, final int overlay,
                                   final int k
    ) {
        float flash = Math.abs(Mth.sin(System.currentTimeMillis() / 1000f));
        var col = ARGB.color(
                (int) (255 * flash),
                ARGB.green(k),
                (int) (255 * flash),
                ARGB.alpha(k)
        );
        renderWithTextureOverride(matrices, vertices, light, overlay, col);
    }

    void renderWithTextureOverride(PoseStack matrices, VertexConsumer vertices, int light, int overlay,
                                   final int k
    ) {
        if (textureOverride == null || (EMFState.isLayerPhase && getRoot().isMainModel)) {
            // || lastTextureOverride == EMFManager.getInstance().entityRenderCount // prevents texture overrides carrying over into feature renderers that reuse the base model
            //normal vertex consumer
            renderLikeETF(matrices, vertices, light, overlay,
                    k
            );
        } else if (light != EYES_FEATURE_LIGHT_VALUE // this is only the case for EyesFeatureRenderer
                && !ETFState.isIsInSpecialRenderOverlayPhase() //do not allow new etf emissive rendering here
                //&& vertices instanceof ETFVertexConsumer etfVertexConsumer
        ) { //can restore to previous render layer

            // check if need to skip due to being in iris shadow pass
            // fixed weird bug with certain texture overrides rendering in first person as though from the sun's POV
            // downside is incorrect shadows for some model parts :/
            //todo triple check it is only block entities, I so far cannot recreate the bug for regular mobs
            var state = EMFState.state();
            if ((state != null && state.isBlockEntity())
                    && ETF.IRIS_DETECTED && IrisShadowPassDetection.getInstance().inShadowPass()) {
                //skip texture override
                renderLikeETF(matrices, vertices, light, overlay,
                        k
                );
                return;
            }

            if (vertices instanceof ETFVertexConsumer etfVertexConsumer) {
                // if the texture override is the same as the current texture, render as normal
                var etfTextureTest = etfVertexConsumer.etf$getETFTexture();
                if (etfTextureTest != null && etfTextureTest.thisIdentifier.equals(textureOverride)) {
                    renderLikeETF(matrices, vertices, light, overlay,
                            k
                    );
                    return;
                }

                RenderType originalLayer = etfVertexConsumer.etf$getRenderLayer();
                if (originalLayer == null) return;

                URenderTypeToVertexConsumer provider = etfVertexConsumer.etf$getProvider();
                if (provider == null) return;
                renderTextureOverrideWithoutReset(provider, matrices, light, overlay,
                        k
                );

                //reset render settings
                provider.getBuffer(originalLayer);
            }else{
                //could be a sprite originally, if so lets ignore trying to reset the texture at all to its original
                URenderTypeToVertexConsumer provider = new URenderTypeToVertexConsumer(null) {
                    @Override
                    public VertexConsumer getBuffer(RenderType type) {
                        //TODO probably wrong investigate further
                        var draw = Minecraft.getInstance().gameRenderer.renderBuffers().stagedVertexBuffer().appendDraw(
                                type.format(),
                                com.mojang.blaze3d.PrimitiveTopology.QUADS
                                );
                        return Minecraft.getInstance().gameRenderer.renderBuffers().stagedVertexBuffer().getVertexBuilder(draw);
                    }
                };
                renderTextureOverrideWithoutReset(provider, matrices, light, overlay,
                        k
                );
            }
        }
        //else cancel out render
    }

    private void renderTextureOverrideWithoutReset(URenderTypeToVertexConsumer provider, PoseStack matrices, int light, int overlay,
                                                   final int k
    ){

//        lastTextureOverride = EMFManager.getInstance().entityRenderCount;
        RenderType layerModified = EMFState.getLayerFromRecentFactoryOrETFOverrideOrTranslucent(textureOverride);
        VertexConsumer newConsumer = provider.getBuffer(layerModified);

        renderLikeVanilla(matrices, newConsumer, light, overlay,
                k
        );

        if (newConsumer instanceof ETFVertexConsumer newETFConsumer) {
            ETFTexture etfTexture = newETFConsumer.etf$getETFTexture();
            if (etfTexture == null) return;
            ETFUtils2.RenderMethodForOverlay renderMethodForOverlay = (prov, ligh) -> renderLikeVanilla(matrices, prov, ligh, overlay,
                    k
            );
            ETFUtils2.renderEmissive(etfTexture, provider, renderMethodForOverlay);
            ETFUtils2.renderEnchanted(etfTexture, provider, light, renderMethodForOverlay);
        }
    }

    //required for sodium 0.5.4+
    void renderLikeVanilla(PoseStack matrices, VertexConsumer vertices, int light, int overlay,
                           final int k
    ) {
        if (this.visible) {
            if (!cubes.isEmpty() || !children.isEmpty()) {
                matrices.pushPose();
                this.translateAndRotate(matrices);
                if (!this.skipDraw) {
                    this.compile(matrices.last(), vertices, light, overlay,
                            k
                    );
                }

                for (ModelPart modelPart : children.values()) {
                    modelPart.render(matrices, vertices, light, overlay,
                            k
                    );
                }

                matrices.popPose();
            }
        }
    }
//todo 1.21 or 1.20.6?
    private VertexConsumer testForBuildingException(VertexConsumer vertices) {
        BufferBuilder testBuilding;
        if (vertices instanceof BufferBuilder) {
            testBuilding = (BufferBuilder) vertices;
        } else if (vertices instanceof SpriteCoordinateExpander sprite && sprite.delegate instanceof BufferBuilder) {
            testBuilding = (BufferBuilder) sprite.delegate;
        }else
        {
            //exit early if not a buffer builder
            return vertices;
        }


        if (testBuilding != null && !testBuilding.building){
            if (testBuilding instanceof ETFVertexConsumer etf
                    && etf.etf$getRenderLayer() != null
                    && etf.etf$getProvider() != null){
                ETFState.pushRenderLayerModifyState(false);

                vertices = etf.etf$getProvider().getBuffer(etf.etf$getRenderLayer());

                ETFState.popRenderLayerModifyState();
            }else {
                return null;
            }
        }
        return vertices;
    }


    //mimics etf model part mixins which can no longer be relied on due to sodium 0.5.5
    void renderLikeETF(PoseStack matrices, VertexConsumer vertices, int light, int overlay,
                       final int k
    ) {

        //todo 1.21 or 1.20.6?
        vertices = testForBuildingException(vertices);
        if (vertices == null) return;

        //etf ModelPartMixin copy
        ETFState.currentModelPartDepth++;

        renderLikeVanilla(matrices, vertices, light, overlay,
                k
        );

        //etf ModelPartMixin copy
        if (ETFState.currentModelPartDepth != 1) {
            ETFState.currentModelPartDepth--;
        } else {
            //top level model so try special rendering
            if (ETFState.isStateActive()
                    && vertices instanceof ETFVertexConsumer etfVertexConsumer) {
                ETFTexture texture = etfVertexConsumer.etf$getETFTexture();
                //is etf texture not null and does it special render?
                if (texture != null && (texture.isEmissive() || texture.isEnchanted())) {
                    URenderTypeToVertexConsumer provider = etfVertexConsumer.etf$getProvider();
                    //very important this is captured before doing the special renders as they can potentially modify
                    //the same ETFVertexConsumer down stream
                    RenderType layer = etfVertexConsumer.etf$getRenderLayer();
                    //are these render required objects valid?
                    if (provider != null && layer != null) {
                        //attempt special renders as eager OR checks
                        ETFUtils2.RenderMethodForOverlay renderMethodForOverlay = (prov, ligh) -> renderLikeVanilla(matrices, prov, ligh, overlay,
                                k
                        );
                        if (ETFUtils2.renderEmissive(texture, provider, renderMethodForOverlay) |
                                ETFUtils2.renderEnchanted(texture, provider, light, renderMethodForOverlay)) {
                            //reset render layer stuff behind the scenes if special renders occurred
                            //this will also return ETFVertexConsumer held data to normal if the same ETFVertexConsumer
                            //was previously affected by a special render
                            provider.getBuffer(layer);
                        }
                    }
                }
            }
            //ensure model count is reset
            ETFState.currentModelPartDepth = 0;
        }
    }

    public void renderBoxes(PoseStack matrices, VertexConsumer vertices, float alpha) {
        if (this.visible) {
            if (!cubes.isEmpty() || !children.isEmpty()) {
                matrices.pushPose();
                this.translateAndRotate(matrices);
                if (!this.skipDraw) {
                    for (Cube cuboid : cubes) {
                        AABB box = new AABB(cuboid.minX / 16, cuboid.minY / 16, cuboid.minZ / 16, cuboid.maxX / 16, cuboid.maxY / 16, cuboid.maxZ / 16);

                        var adds = ((IEMFCuboidDataSupplier) cuboid).emf$getSizeAdd();
                        if (adds != null) {
                            if (adds[0] != 0 || adds[1] != 0 || adds[2] != 0) {
                                box = box.inflate(adds[0] / 16, adds[1] / 16, adds[2] / 16);
                            }
                        }
                        box = box.inflate(0.0001);

                        var col = debugBoxColor();
                        EMFUtils.renderLineBox(matrices.last(), vertices, box, col[0], col[1], col[2], alpha);
                    }
                }
                for (ModelPart modelPart : children.values()) {
                    if (modelPart instanceof EMFModelPart emf)
                        emf.renderBoxes(matrices, vertices, alpha);
                }
                matrices.popPose();
            }
        }
    }

    protected abstract float[] debugBoxColor();

    //required for sodium pre 0.5.4
    // overrides to circumvent sodium optimizations that mess with custom uv quad creation and swapping out cuboids
    @Override
    public void compile(final PoseStack.Pose pose, final VertexConsumer vertexConsumer, final int i, final int j,
                        final int k
    ) {
        //this is a copy of the vanilla renderCuboids() method
        try {
            for (Cube cuboid : cubes) {
                cuboid.compile(pose, vertexConsumer, i, j,
                        k
                );
            }
        } catch (IllegalStateException e) {
            EMFUtils.logWarn("IllegalStateException caught in EMF model part");
        }
    }

    public String simplePrintChildren(int depth) {
        StringBuilder mapper = new StringBuilder();
        mapper.append("\n  | ");
        mapper.append("- ".repeat(Math.max(0, depth)));
        mapper.append(this.toStringShort());
        for (ModelPart child :
                children.values()) {
            if (child instanceof EMFModelPart emf) {
                mapper.append(emf.simplePrintChildren(depth + 1));
            }
        }
        return mapper.toString();
    }

    public String toStringShort() {
        return toString();
    }


    @Override
    public String toString() {
        return "generic emf part";
    }


    //    private static int indent = 0;
    public ModelPart getVanillaModelPartsOfCurrentState() {
//        indent++;
        Map<String, ModelPart> children = new HashMap<>();
        for (Map.Entry<String, ModelPart> child :
                this.children.entrySet()) {
            if (child.getValue() instanceof EMFModelPart emf) {
                children.put(child.getKey(), emf.getVanillaModelPartsOfCurrentState());
            }
        }

        List<Cube> finalCubes;
        if (cubes.isEmpty()) {
            finalCubes = List.of(new Cube(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, 0, 0, Set.of()));
        } else {
            finalCubes = cubes;
        }

        ModelPart part = new ModelPart(finalCubes, children);
        part.setInitialPose(getInitialPose());
        part.xRot = xRot;
        part.zRot = zRot;
        part.yRot = yRot;
        part.z = z;
        part.y = y;
        part.x = x;
        part.xScale = xScale;
        part.yScale = yScale;
        part.zScale = zScale;

        return part;
    }

    public HashMap<String, EMFModelPart> getAllChildPartsAsAnimationMap(String prefixableParents, int variantNum, Map<String, String> optifinePartNameMap) {
        if (this instanceof EMFModelPartRoot root)
            root.setVariantStateTo(variantNum);

        HashMap<String, EMFModelPart> mapOfAll = new HashMap<>();

        for (ModelPart part : children.values()) {
            if (part instanceof EMFModelPart emfPart) {
                String thisKey = "NULL_KEY_NAME";
                boolean addThis = false;

                if (part instanceof EMFModelPartCustom partCustom) {
                    thisKey = partCustom.id;
                    addThis = true;
                } else if (part instanceof EMFModelPartVanilla partVanilla) {
                    thisKey = partVanilla.name;
                    addThis = partVanilla.isOptiFinePartSpecified;
                }

                for (Map.Entry<String, String> entry : optifinePartNameMap.entrySet()) {
                    if (entry.getValue().equals(thisKey)) {
                        thisKey = entry.getKey();
                        break;
                    }
                }

                if (addThis) {
                    //put if absent so the first part with that id is the one referenced
                    mapOfAll.putIfAbsent(thisKey, emfPart);
                    if (prefixableParents.isBlank()) {
                        mapOfAll.putAll(emfPart.getAllChildPartsAsAnimationMap(thisKey, variantNum, optifinePartNameMap));
                    } else {
                        mapOfAll.putIfAbsent(prefixableParents + ':' + thisKey, emfPart);
                        mapOfAll.putAll(emfPart.getAllChildPartsAsAnimationMap(prefixableParents + ':' + thisKey, variantNum, optifinePartNameMap));
                    }
                } else {
                    mapOfAll.putAll(emfPart.getAllChildPartsAsAnimationMap(prefixableParents, variantNum, optifinePartNameMap));
                }

            }

        }
        return mapOfAll;
    }

}
