/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.model.player.PlayerModel
 *  net.minecraft.client.player.AbstractClientPlayer
 */
package dev.tr7zw.notenoughanimations.api;

import dev.tr7zw.notenoughanimations.access.PlayerData;
import dev.tr7zw.notenoughanimations.versionless.animations.BodyPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;

public abstract class BasicAnimation {
    private boolean isPrepared = false;

    public abstract boolean isEnabled();

    public abstract boolean isValid(AbstractClientPlayer var1, PlayerData var2);

    public abstract BodyPart[] getBodyParts(AbstractClientPlayer var1, PlayerData var2);

    public abstract int getPriority(AbstractClientPlayer var1, PlayerData var2);

    public void prepare(AbstractClientPlayer entity, PlayerData data, PlayerModel model, float delta, float swing) {
        if (!this.isPrepared) {
            this.precalculate(entity, data, model, delta, swing);
            this.isPrepared = true;
        }
    }

    public void cleanup() {
        this.isPrepared = false;
    }

    protected void precalculate(AbstractClientPlayer entity, PlayerData data, PlayerModel model, float delta, float tickCounter) {
    }

    public abstract void apply(AbstractClientPlayer var1, PlayerData var2, PlayerModel var3, BodyPart var4, float var5, float var6);
}

