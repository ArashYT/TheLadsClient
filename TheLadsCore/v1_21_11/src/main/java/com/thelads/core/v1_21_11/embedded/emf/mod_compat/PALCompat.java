package com.thelads.core.v1_21_11.embedded.emf.mod_compat;


import com.zigythebird.playeranim.accessors.IAnimatedAvatar;
import com.zigythebird.playeranim.animation.AvatarAnimManager;
import com.thelads.core.v1_21_11.embedded.emf.utils.EMFEntity;

public class PALCompat {
    public static boolean shouldPauseEntityAnim(EMFEntity entity) {
        if (entity instanceof IAnimatedAvatar animationState) {
            AvatarAnimManager manager = animationState.playerAnimLib$getAnimManager();
            return manager != null && manager.isActive();
        }
        return false;
    }
}