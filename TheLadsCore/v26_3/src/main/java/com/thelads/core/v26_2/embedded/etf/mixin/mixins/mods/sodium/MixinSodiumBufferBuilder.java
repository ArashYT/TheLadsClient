package com.thelads.core.v26_2.embedded.etf.mixin.mixins.mods.sodium;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import com.thelads.core.v26_2.embedded.etf.mixin.CancelTarget;

@Pseudo
@Mixin(CancelTarget.class)
public class MixinSodiumBufferBuilder {}
