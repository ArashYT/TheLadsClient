package com.thelads.core.v26_2.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.resources.Identifier;
@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.config.builder.OptionBuilderImpl",remap=false)
public interface SodiumOptionAccessor { @Accessor("id") Identifier ladsOptionId(); }
