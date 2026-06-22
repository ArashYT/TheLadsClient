/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.model.player.PlayerModel
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.world.entity.Pose
 */
package dev.tr7zw.notenoughanimations.animations.vanilla;

import dev.tr7zw.notenoughanimations.access.PlayerData;
import dev.tr7zw.notenoughanimations.api.BasicAnimation;
import dev.tr7zw.notenoughanimations.versionless.NEABaseMod;
import dev.tr7zw.notenoughanimations.versionless.animations.BodyPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Pose;

public class SwimAnimation
extends BasicAnimation {
    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public boolean isValid(AbstractClientPlayer entity, PlayerData data) {
        return entity.getPose() == Pose.SWIMMING && (entity.isInWater() || !NEABaseMod.config.enableCrawlingAnimation);
    }

    @Override
    public BodyPart[] getBodyParts(AbstractClientPlayer entity, PlayerData data) {
        return BodyPart.values();
    }

    @Override
    public int getPriority(AbstractClientPlayer entity, PlayerData data) {
        return 3600;
    }

    @Override
    public void apply(AbstractClientPlayer entity, PlayerData data, PlayerModel model, BodyPart part, float delta, float tickCounter) {
    }
}

