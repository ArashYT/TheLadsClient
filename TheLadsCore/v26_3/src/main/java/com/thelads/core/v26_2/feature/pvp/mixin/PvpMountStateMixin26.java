package com.thelads.core.v26_2.feature.pvp.mixin;

import com.thelads.core.v26_2.feature.pvp.PvpVisuals26;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Mount Opacity & Damage State on LivingEntityRenderState. */
@Mixin(LivingEntityRenderState.class)
public class PvpMountStateMixin26 implements PvpVisuals26.MountState, PvpVisuals26.DamageState {
    @Unique
    private boolean lads$mount;
    @Unique
    private int lads$damageV = 3;

    @Override
    public boolean lads$mount() {
        return lads$mount;
    }

    @Override
    public void lads$mount(boolean mount) {
        lads$mount = mount;
    }

    @Override
    public int lads$damageV() {
        return lads$damageV;
    }

    @Override
    public void lads$damageV(int v) {
        lads$damageV = v;
    }
}
