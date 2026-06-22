/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.model.player.PlayerModel
 *  net.minecraft.client.player.AbstractClientPlayer
 *  net.minecraft.world.item.ItemUseAnimation
 */
package dev.tr7zw.notenoughanimations.animations.fullbody;

import dev.tr7zw.notenoughanimations.access.PlayerData;
import dev.tr7zw.notenoughanimations.api.BasicAnimation;
import dev.tr7zw.notenoughanimations.versionless.NEABaseMod;
import dev.tr7zw.notenoughanimations.versionless.animations.BodyPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.item.ItemUseAnimation;

public class ActionRotationLockAnimation
extends BasicAnimation {
    private BodyPart[] target = new BodyPart[]{BodyPart.BODY};

    @Override
    public boolean isEnabled() {
        return NEABaseMod.config.enableRotationLocking;
    }

    @Override
    public boolean isValid(AbstractClientPlayer entity, PlayerData data) {
        block2: {
            block3: {
                if (entity.getUseItemRemainingTicks() <= 0) break block2;
                ItemUseAnimation action = entity.getUseItem().getUseAnimation();
                if (action == ItemUseAnimation.EAT) break block3;
                if (action == ItemUseAnimation.DRINK) break block3;
                if (action != ItemUseAnimation.BLOCK) break block2;
            }
            return true;
        }
        return false;
    }

    @Override
    public BodyPart[] getBodyParts(AbstractClientPlayer entity, PlayerData data) {
        return this.target;
    }

    @Override
    public int getPriority(AbstractClientPlayer entity, PlayerData data) {
        return 1250;
    }

    @Override
    public void apply(AbstractClientPlayer entity, PlayerData data, PlayerModel model, BodyPart part, float delta, float tickCounter) {
        data.setRotateBodyToHead(true);
    }
}

