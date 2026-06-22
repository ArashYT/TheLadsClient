/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.model.player.PlayerModel
 *  net.minecraft.client.player.AbstractClientPlayer
 */
package dev.tr7zw.notenoughanimations.api;

import dev.tr7zw.notenoughanimations.access.PlayerData;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;

public interface PoseOverwrite {
    public void updateState(AbstractClientPlayer var1, PlayerData var2, PlayerModel var3);
}

