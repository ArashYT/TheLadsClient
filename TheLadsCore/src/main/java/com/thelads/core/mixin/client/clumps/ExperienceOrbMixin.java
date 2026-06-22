package com.thelads.core.mixin.client.clumps;

import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.ClumpsModule;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// @Mixin(ExperienceOrb.class)
public abstract class ExperienceOrbMixin extends Entity {

    @Shadow private int value;
    @Shadow private int count;
    @Shadow private int age;

    public ExperienceOrbMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void clumps$onTick(CallbackInfo ci) {
        ClumpsModule module = (ClumpsModule) ModuleManager.getInstance().getModule("Clumps");
        if (module != null && module.isEnabled()) {
            if (!this.level().isClientSide() && this.isAlive()) {
                AABB box = this.getBoundingBox().inflate(0.5D);
                List<ExperienceOrb> others = this.level().getEntitiesOfClass(ExperienceOrb.class, box, e -> e.isAlive() && e != (Object)this);
                
                for (ExperienceOrb other : others) {
                    ExperienceOrbMixin otherMixin = (ExperienceOrbMixin)(Object)other;
                    
                    if (this.isAlive() && other.isAlive()) {
                        if (this.age == otherMixin.age) {
                            this.value += otherMixin.value;
                            this.count += otherMixin.count;
                            other.discard();
                        }
                    }
                }
            }
        }
    }
}
