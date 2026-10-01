package com.thelads.core.v26_2.embedded.emf.models.parts;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.jetbrains.annotations.NotNull;
import com.thelads.core.v26_2.embedded.emf.models.animation.state.EMFState;
import com.thelads.core.v26_2.embedded.emf.utils.EMFUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.resources.Identifier;

public abstract class EMFModelPartWithState extends EMFModelPart {

    public final Map<Integer, EMFModelState> allKnownStateVariants = new HashMap<>() {
        @Override
        public EMFModelState get(Object k) {
            var val = super.get(k);
            if (val == null) {
                EMFUtils.logWarn("EMFModelState variant with key " + k + " does not exist in part [" + toStringShort() + "], returning copy of 0. State is :" + EMFState.state());
                val = get(0).copy();
                put((Integer) k, val);
            }
            return val;
        }
    };
    public int currentModelVariant = 0;
    Map<String, ModelPart> vanillaChildren = new HashMap<>();

    public EMFModelPartWithState(List<Cube> cuboids, Map<String, ModelPart> children, EMFModelPartRoot root) {
        super(cuboids, children, root);
    }

    @Override
    public void render(PoseStack matrices, VertexConsumer vertices, int light, int overlay,
                       final int k
    ) {
        var root = getRoot();
        root.oneTimeRunnable();
        root.animate();

        super.render(matrices, vertices, light, overlay,
                k
        );

    }

    EMFModelState getCurrentState() {
        return new EMFModelState(
                getInitialPose(),
                cubes,
                children,
                visible, skipDraw,
                textureOverride
        );
    }

    EMFModelState getStateOf(ModelPart modelPart) {
        if (modelPart instanceof EMFModelPartWithState emf) {
            return new EMFModelState(
                    modelPart.getInitialPose(),
                    modelPart.cubes,
                    modelPart.children,
                    modelPart.visible, modelPart.skipDraw,
                    emf.textureOverride
            );
        }
        return new EMFModelState(
                modelPart.getInitialPose(),
                modelPart.cubes,
                new HashMap<>(),
                modelPart.visible, modelPart.skipDraw,
                null
        );
    }

    void setFromState(EMFModelState newState) {
        setInitialPose(newState.defaultTransform());
        loadPose(getInitialPose());

        cubes = newState.cuboids();
        children = newState.variantChildren();


        if (!EMFState.modelVariationIgnoresVisibility) {
            visible = newState.visible();
            skipDraw = newState.hidden();
        }
        textureOverride = newState.texture();
    }

    protected void resetState(){
        setFromState(allKnownStateVariants.get(currentModelVariant));
    }

    public void setVariantStateTo(int newVariant) {
        if (currentModelVariant != newVariant) {
            setFromState(allKnownStateVariants.get(newVariant));
            currentModelVariant = newVariant;
            for (ModelPart part :
                    children.values()) {
                if (part instanceof EMFModelPartWithState p3)
                    p3.setVariantStateTo(newVariant);
            }
        }
    }

    public void copyVariantTo(int from, int to) {
        allKnownStateVariants.putIfAbsent(to, allKnownStateVariants.get(from).copy());
        for (ModelPart value : children.values()) {
            if (value instanceof EMFModelPartWithState p3)
                p3.copyVariantTo(from, to);
        }
    }

    public record
            EMFModelState(
                    PartPose defaultTransform,
                    // ModelTransform currentTransform,
                    List<Cube> cuboids,
                    Map<String, ModelPart> variantChildren,
                    boolean visible,
                    boolean hidden,
                    Identifier texture
            )

    {

        public EMFModelState copyWithoutTexture() {
            return copy(visible(), false);
        }

        public EMFModelState copy() {
             return copy(visible(), true);
        }

        public EMFModelState copy(boolean visibleOverride) {
            return copy(visibleOverride, true);
        }

        public EMFModelState copy(boolean visibleOverride, boolean keepTexture) {
            PartPose trans = defaultTransform();
            return new EMFModelState(
                    new PartPose(trans.x(), trans.y(), trans.z(), trans.xRot(), trans.yRot(), trans.zRot(), trans.xScale(), trans.yScale(), trans.zScale()),
                    new ArrayList<>(cuboids()),
                    new HashMap<>(variantChildren()),

                    visibleOverride,
                    hidden(),
                    keepTexture ? texture() : null
            );
        }
    }
}
