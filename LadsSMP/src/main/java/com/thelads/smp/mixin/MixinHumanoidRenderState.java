package com.thelads.smp.mixin;

import com.thelads.smp.features.LeaderRenderStateExtension;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Consolidates 'zombieimprovements': Stores leader flag on HumanoidRenderState.
 */
@Mixin(HumanoidRenderState.class)
public class MixinHumanoidRenderState implements LeaderRenderStateExtension {

    @Unique
    private boolean ladssmp$leader = false;

    @Override
    public boolean ladssmp$isLeader() {
        return this.ladssmp$leader;
    }

    @Override
    public void ladssmp$setLeader(boolean leader) {
        this.ladssmp$leader = leader;
    }
}
