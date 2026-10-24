package com.thelads.core.v26_2.feature.pvp.mixin;

import com.thelads.core.v26_2.feature.pvp.PvpVisuals26;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Mount Opacity: whether this state is the player's mount, set when it is extracted and read when it is drawn. */
@Mixin(LivingEntityRenderState.class)
public class PvpMountStateMixin26 implements PvpVisuals26.MountState {
    @Unique
    private boolean lads$mount;

    @Override
    public boolean lads$mount() {
        return lads$mount;
    }

    @Override
    public void lads$mount(boolean mount) {
        lads$mount = mount;
    }
}
