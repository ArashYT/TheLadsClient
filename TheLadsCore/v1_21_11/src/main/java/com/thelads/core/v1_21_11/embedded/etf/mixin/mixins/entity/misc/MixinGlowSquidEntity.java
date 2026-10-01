package com.thelads.core.v1_21_11.embedded.etf.mixin.mixins.entity.misc;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.squid.GlowSquid;
import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import com.thelads.core.v1_21_11.embedded.etf.ETF;
import com.thelads.core.v1_21_11.embedded.etf.features.ETFManager;

@Mixin(GlowSquid.class)
public abstract class MixinGlowSquidEntity extends Squid {


    @SuppressWarnings("unused")
    public MixinGlowSquidEntity(EntityType<? extends Squid> entityType, Level world) {
        super(entityType, world);
    }

    @ModifyArg(
            method = "aiStep",
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


