/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  lombok.Generated
 *  net.minecraft.world.entity.Pose
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.ItemStack
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfo
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package dev.tr7zw.notenoughanimations.mixins;

import dev.tr7zw.notenoughanimations.access.PlayerData;
import dev.tr7zw.notenoughanimations.versionless.NEABaseMod;
import dev.tr7zw.notenoughanimations.versionless.animations.DataHolder;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={Player.class})
public class PlayerEntityMixin
implements PlayerData {
    private int armsUpdated = 0;
    private float[] lastRotations = new float[45];
    private ItemStack sideSword = ItemStack.EMPTY;
    private ItemStack[] lastHeldItems = new ItemStack[2];
    private boolean disableBodyRotation = false;
    private boolean rotateBodyToHead = false;
    private int itemSwapAnimationTimer = 0;
    private int lastAnimationSwapTick = -1;
    private Pose poseOverwrite = null;
    private Map<DataHolder<?>, Object> animationData = new HashMap();

    @Inject(method={"tick"}, at={@At(value="RETURN")})
    public void tick(CallbackInfo info) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("NotEnoughAnimations").isEnabled()) {
            return;
        }
        this.updateRenderLayerItems();
        this.setRotateBodyToHead(false);
    }

    @Inject(method={"getMaxHeadRotationRelativeToBody"}, at={@At(value="HEAD")}, cancellable=true)
    protected void overrideMaxHeadRoationRelativeToBody(CallbackInfoReturnable<Float> ci) {
        if (!com.thelads.core.config.ModuleManager.getInstance().getModule("NotEnoughAnimations").isEnabled()) {
            return;
        }
        Player player;
        if (NEABaseMod.config.maxBlockingAngle < 0.0f || NEABaseMod.config.maxBlockingAngle > 15.0f) {
            NEABaseMod.config.maxBlockingAngle = 15.0f;
        }
        if (NEABaseMod.config.maxNormalAngle < 0.0f || NEABaseMod.config.maxNormalAngle > 50.0f) {
            NEABaseMod.config.maxNormalAngle = 50.0f;
        }
        ci.setReturnValue(Float.valueOf((player = (Player)(Object)this).isBlocking() ? NEABaseMod.config.maxBlockingAngle : NEABaseMod.config.maxNormalAngle));
    }

    @Override
    public int isUpdated(int frameId) {
        return Math.abs(frameId - this.armsUpdated);
    }

    @Override
    public void setUpdated(int frameId) {
        this.armsUpdated = frameId;
    }

    private void updateRenderLayerItems() {
    }

    @Override
    public ItemStack[] getLastHeldItems() {
        return this.lastHeldItems;
    }

    @Override
    public int getItemSwapAnimationTimer() {
        return this.itemSwapAnimationTimer;
    }

    @Override
    public void setItemSwapAnimationTimer(int count) {
        this.itemSwapAnimationTimer = count;
    }

    @Override
    public int getLastAnimationSwapTick() {
        return this.lastAnimationSwapTick;
    }

    @Override
    public void setLastAnimationSwapTick(int count) {
        this.lastAnimationSwapTick = count;
    }

    @Override
    public void setPoseOverwrite(Pose state) {
        this.poseOverwrite = state;
    }

    @Override
    public Pose getPoseOverwrite() {
        return this.poseOverwrite;
    }

    @Override
    public <T> T getData(DataHolder<T> holder, Supplier<T> builder) {
        return (T)this.animationData.computeIfAbsent(holder, h -> builder.get());
    }

    @Override
    public float[] getLastRotations() {
        return this.lastRotations;
    }

    public void setLastRotations(float[] lastRotations) {
        this.lastRotations = lastRotations;
    }

    @Override
    public ItemStack getSideSword() {
        return this.sideSword;
    }

    @Override
    public void setSideSword(ItemStack sideSword) {
        this.sideSword = sideSword;
    }

    @Override
    public boolean isDisableBodyRotation() {
        return this.disableBodyRotation;
    }

    @Override
    public void setDisableBodyRotation(boolean disableBodyRotation) {
        this.disableBodyRotation = disableBodyRotation;
    }

    @Override
    public boolean isRotateBodyToHead() {
        return this.rotateBodyToHead;
    }

    @Override
    public void setRotateBodyToHead(boolean rotateBodyToHead) {
        this.rotateBodyToHead = rotateBodyToHead;
    }
}

