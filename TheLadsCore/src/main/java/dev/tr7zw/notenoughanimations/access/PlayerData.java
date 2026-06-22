/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.entity.Pose
 *  net.minecraft.world.item.ItemStack
 */
package dev.tr7zw.notenoughanimations.access;

import dev.tr7zw.notenoughanimations.versionless.animations.DataHolder;
import java.util.function.Supplier;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;

public interface PlayerData {
    public int isUpdated(int var1);

    public void setUpdated(int var1);

    public float[] getLastRotations();

    public ItemStack getSideSword();

    public void setSideSword(ItemStack var1);

    public void setDisableBodyRotation(boolean var1);

    public boolean isDisableBodyRotation();

    public void setRotateBodyToHead(boolean var1);

    public boolean isRotateBodyToHead();

    public ItemStack[] getLastHeldItems();

    public int getItemSwapAnimationTimer();

    public void setItemSwapAnimationTimer(int var1);

    public int getLastAnimationSwapTick();

    public void setLastAnimationSwapTick(int var1);

    public void setPoseOverwrite(Pose var1);

    public Pose getPoseOverwrite();

    public <T> T getData(DataHolder<T> var1, Supplier<T> var2);
}

