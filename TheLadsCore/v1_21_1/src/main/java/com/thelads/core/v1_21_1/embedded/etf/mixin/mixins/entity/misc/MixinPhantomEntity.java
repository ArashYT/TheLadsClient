package com.thelads.core.v1_21_1.embedded.etf.mixin.mixins.entity.misc;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Phantom;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import com.thelads.core.v1_21_1.embedded.etf.ETF;
import com.thelads.core.v1_21_1.embedded.etf.features.ETFManager;

@Mixin(Phantom.class)
public abstract class MixinPhantomEntity extends Entity {

    @SuppressWarnings("DataFlowIssue")
    protected MixinPhantomEntity() {super(null, null);}

    @ModifyArg(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"),
            index = 2
    )
    private double mixin(double x) {
        if (ETF.config().getConfig().canDoCustomTextures()
                && ETFManager.getInstance().ENTITY_TYPE_IGNORE_PARTICLES.contains(this.getType())) {
            return -500;
        }
        return x;
    }
}


