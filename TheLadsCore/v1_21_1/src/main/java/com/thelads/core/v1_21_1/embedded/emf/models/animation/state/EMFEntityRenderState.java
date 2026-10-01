package com.thelads.core.v1_21_1.embedded.emf.models.animation.state;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import com.thelads.core.v1_21_1.embedded.emf.EMF;
import com.thelads.core.v1_21_1.embedded.emf.EMFManager;
import com.thelads.core.v1_21_1.embedded.emf.models.IEMFModel;
import com.thelads.core.v1_21_1.embedded.emf.models.animation.math.EMFMath;
import com.thelads.core.v1_21_1.embedded.emf.models.parts.EMFModelPartRoot;
import com.thelads.core.v1_21_1.embedded.emf.utils.EMFEntity;
import com.thelads.core.v1_21_1.embedded.emf.utils.EMFLODHandler;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFEntityRenderState;
import com.thelads.core.v1_21_1.embedded.etf.features.state.ETFSubmitData;
import com.thelads.core.v1_21_1.embedded.etf.features.state.HoldsETFRenderState;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.thelads.core.v1_21_1.embedded.etf.utils.ETFEntity;

public interface EMFEntityRenderState extends ETFEntityRenderState {

    /**
     * Deprecated - replace usages with 1.21+ impl that doesn't smuggle entity
     */
    @Deprecated
    EMFEntity emfEntity();

    boolean isManualPlayerState();
    void setManualPlayerState(boolean manualPlayerState);
    static @Nullable EMFEntityRenderState manualPlayerState() {
        if (Minecraft.getInstance().player == null) return null;

        var state = (EMFEntityRenderState) ETFEntityRenderState.forEntity((ETFEntity) Minecraft.getInstance().player);

        state.setManualPlayerState(true);
        return state;
    }

    double prevX();
    double x();
    double prevY();
    double y();
    double prevZ();
    double z();

    float prevPitch();
    float pitch();

    boolean isTouchingWater();
    boolean isOnFire();
    boolean hasVehicle();
    boolean isOnGround();
    boolean isAlive();
    boolean isGlowing();
    boolean isInLava();
    boolean isInvisible();
    boolean hasPassengers();
    boolean isSneaking();
    boolean isSprinting();
    boolean isWet();

    float age();
    float yaw();

    Vec3 emfVelocity(); // nullable

    String typeString(); // nullable

    Map<String, Float> variableMap(); // nullable

    Function<ResourceLocation, RenderType> layerFactory();
    void setLayerFactory(Function<ResourceLocation, RenderType> layerFactory);

    void setBipedPose(EMFBipedPose pose);
    /** Returns the biped pose if it was animated. */
    @Nullable EMFBipedPose getBipedPose();


    boolean isFirstPersonHand();
    void setIsFirstPersonHand(boolean isFirst);

    float shadowSize();
    void setShadowSize(float shadowSize);

    float shadowOpacity();
    void setShadowOpacity(float shadowOpacity);

    float shadowX();
    void setShadowX(float shadowX);

    float shadowZ();
    void setShadowZ(float shadowZ);

    float limbAngle();
    void setLimbAngle(float limbAngle);

    float limbDistance();
    void setLimbDistance(float limbDistance);

    float headYaw();
    void setHeadYaw(float headYaw);

    float headPitch();
    void setHeadPitch(float headPitch);

    default boolean needsToModifyShadow() {
        return !Float.isNaN(shadowSize())
                || !Float.isNaN(shadowOpacity())
                || !Float.isNaN(shadowX())
                || !Float.isNaN(shadowZ());
    }

    float fireX();
    void setFireX(float fireX);
    float fireY();
    void setFireY(float fireY);
    float fireZ();
    void setFireZ(float fireZ);

    float fireHeight();
    void setFireHeight(float fireHeightScale);
    float fireScale();
    void setFireScale(float fireWidthScale);

    default boolean needsToModifyFire() {
        return !Float.isNaN(fireScale())
                || !Float.isNaN(fireHeight())
                || !Float.isNaN(fireX())
                || !Float.isNaN(fireY())
                || !Float.isNaN(fireZ());
    }


    boolean skipModelVariate();
    void setSkipModelVariate(boolean value);

    boolean isSubmit();
    void setSubmit(boolean set);

    EMFState.EMFStateStaticSnapshot getEMFStateSnapshot();
    void setEMFStateSnapshot(EMFState.EMFStateStaticSnapshot snapshot);


    @Override
    default void activate(boolean inMount) {
        ETFEntityRenderState.super.activate(inMount);

        if (!isSubmit()) {
            if (inMount) setEMFStateSnapshot(EMFState.captureStatics());
            else if (getEMFStateSnapshot() != null) getEMFStateSnapshot().restoreStatics();
        }

        EMFManager.getInstance().entityRenderCount++;
        if (inMount && (!EMFState.isLayerPhase || EMFState.isInShoulderMethod)) {

            setLimbAngle(Float.NaN);
            setLimbDistance(Float.NaN);
            setHeadYaw(Float.NaN);
            setHeadPitch(Float.NaN);

            if (entity() instanceof Arrow) {
                setLayerFactory(
                        RenderType
                                ::entityCutout);
            } else if (isBlockEntity()) {
                setLayerFactory(
                        RenderType
                                ::entitySolid);
            }

            //perform variant checking for this entity types models
            //this is the only way to keep it generic and also before the entity is rendered and affect al its models
            boolean playerNeedsReset = emfEntity() instanceof Player
                    && EMF.config().getConfig().resetPlayerModelEachRender_v2
                    && !isFirstPersonHand();

            if (!skipModelVariate() || playerNeedsReset) {
                Set<EMFModelPartRoot> roots = EMFManager.getInstance().rootPartsPerEntityTypeForVariation.get(typeString());
                if (roots != null) {
                    if (!skipModelVariate()) {
                        if (EMFState.isEntityForcedToVanillaModel(this)) {
                            roots.forEach(root -> root.setVariantStateTo(0));
                        } else {
                            roots.forEach(root -> root.doVariantCheck(this));
                        }
                    }

                    if (playerNeedsReset) {
                        roots.forEach(EMFModelPartRoot::resetVanillaPartsToDefaults);
                    }
                }
            }

            //if this entity requires a debug print do it now after models have variated
            if (EMF.config().getConfig().debugOnRightClick
                    && uuid().equals(EMFManager.getInstance().entityForDebugPrint)) {
                EMFState.announceModels = true;
                EMFManager.getInstance().entityForDebugPrint = null;
            }
        }
        EMFLODHandler.setNullLodFrameSkipping();
    }

    @Override
    default void deactivate(boolean inMount) {
        ETFEntityRenderState.super.deactivate(inMount);
        EMFState.modelVariationIgnoresVisibility = false;

        if (!inMount) {
            setSkipModelVariate(false);
        }

        if (isSubmit()) {
            setSubmit(false);
            EMFState.clearFrameStatics();
        } else {
            if (!inMount) {
                var snapshot = getEMFStateSnapshot();
                if (snapshot != null) {
                    snapshot.restoreStatics();
                }
                setEMFStateSnapshot(null);
            }

        }
    }

}