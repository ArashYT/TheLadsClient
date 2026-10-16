package com.thelads.core.v26_2.mixin;

import com.thelads.core.modules.SurvivalTweaksModule;
import com.thelads.core.v26_2.feature.survivaltweaks.NativeSurvivalTweaks;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Creeper.class)
public abstract class SurvivalCreeperDropsMixin {
    @ModifyArg(
            method = "explodeCreeper",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;explode(Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Level$ExplosionInteraction;)V"
            ),
            index = 5
    )
    private Level.ExplosionInteraction lads$creeperExplosionDropsAll(Level.ExplosionInteraction original) {
        SurvivalTweaksModule mod = SurvivalTweaksModule.get();
        if (NativeSurvivalTweaks.active() && mod != null && mod.isCreeperFullDropsEnabled()
                && original == Level.ExplosionInteraction.MOB) {
            return Level.ExplosionInteraction.TNT;
        }
        return original;
    }
}
