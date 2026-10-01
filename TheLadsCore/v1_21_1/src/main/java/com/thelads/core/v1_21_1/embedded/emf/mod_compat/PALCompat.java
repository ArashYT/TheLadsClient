package com.thelads.core.v1_21_1.embedded.emf.mod_compat;


import com.zigythebird.playeranim.accessors.IAnimatedPlayer;
import com.zigythebird.playeranim.animation.PlayerAnimManager;
import com.thelads.core.v1_21_1.embedded.emf.utils.EMFEntity;

public class PALCompat {
    public static boolean shouldPauseEntityAnim(EMFEntity entity) {
        if (entity instanceof IAnimatedPlayer player) {
            PlayerAnimManager manager = player.playerAnimLib$getAnimManager();
            return manager != null && manager.isActive();
        }
        return false;
    }
}