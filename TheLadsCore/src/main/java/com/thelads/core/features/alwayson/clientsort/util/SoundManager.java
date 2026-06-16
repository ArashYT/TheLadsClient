/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.resources.sounds.SimpleSoundInstance
 *  net.minecraft.client.resources.sounds.SoundInstance
 *  net.minecraft.client.resources.sounds.SoundInstance$Attenuation
 *  net.minecraft.sounds.SoundSource
 *  net.minecraft.util.Util
 *  net.minecraft.world.item.ItemStack
 *  org.jetbrains.annotations.Nullable
 */
package com.thelads.core.features.alwayson.clientsort.util;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import com.thelads.core.features.alwayson.clientsort.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class SoundManager {
    private static long nextSoundTime = Long.MIN_VALUE;
    private static float pitch = 1.0f;
    private static float increment = 0.01f;
    @Nullable
    private static SoundInstance sound;

    public static boolean shouldPlaySortingSounds() {
        return Config.options().playSoundSort && Config.options().soundVolume > 0.0f;
    }

    public static boolean shouldPlayOtherSounds() {
        return Config.options().playSoundOther && Config.options().soundVolume > 0.0f;
    }

    public static void resetForCount(int size) {
        increment = (Config.options().soundPitchMax - Config.options().soundPitchMin) / (float)size;
        pitch = Config.options().soundPitchMin;
    }

    public static void play() {
        long now = Util.getMillis();
        float soundPitch = SoundManager.getPitch();
        if (now >= nextSoundTime) {
            nextSoundTime = now + (long)Config.options().soundInterval;
            Identifier location = Config.options().sortSoundLoc;
            if (location != null) {
                if (sound != null && !Config.options().allowSoundOverlap) {
                    Minecraft.getInstance().getSoundManager().stop(sound);
                }
                sound = new SimpleSoundInstance(location, SoundSource.MASTER, Config.options().soundVolume, soundPitch, SoundInstance.createUnseededRandom(), false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
                Minecraft.getInstance().getSoundManager().play(sound);
            }
        }
    }

    private static float getPitch() {
        float val = pitch;
        if ((pitch += increment) > Config.options().soundPitchMax) {
            pitch = Config.options().soundPitchMax;
        }
        return val;
    }

    public static int estimateSortSounds(ItemStack[] stacks) {
        int stackCount = 0;
        for (ItemStack stack : stacks) {
            if (stack == ItemStack.EMPTY) continue;
            ++stackCount;
        }
        int compaction = 0;
        for (int i = 0; i < stackCount; ++i) {
            if (stacks[i] != ItemStack.EMPTY) continue;
            ++compaction;
        }
        int size = stackCount + compaction;
        size += size / 15;
        return size;
    }

    public static int estimateStackFillSounds(ItemStack[] srcStacks, ItemStack[] dstStacks) {
        int dstPartialCount = 0;
        for (ItemStack stack : dstStacks) {
            if (stack == ItemStack.EMPTY || stack.getCount() >= stack.getMaxStackSize()) continue;
            ++dstPartialCount;
        }
        int srcStackCount = 0;
        for (ItemStack stack : srcStacks) {
            if (stack == ItemStack.EMPTY) continue;
            ++srcStackCount;
        }
        return (dstPartialCount / 2 + srcStackCount / 2) / 2;
    }

    public static int estimateTransferSounds(ItemStack[] srcStacks, ItemStack[] dstStacks) {
        int srcStackCount = 0;
        for (ItemStack stack : srcStacks) {
            if (stack == ItemStack.EMPTY) continue;
            ++srcStackCount;
        }
        int dstHoleCount = 0;
        for (ItemStack stack : dstStacks) {
            if (stack != ItemStack.EMPTY) continue;
            ++dstHoleCount;
        }
        dstHoleCount += dstHoleCount / 8;
        return Math.min(srcStackCount, dstHoleCount);
    }
}
