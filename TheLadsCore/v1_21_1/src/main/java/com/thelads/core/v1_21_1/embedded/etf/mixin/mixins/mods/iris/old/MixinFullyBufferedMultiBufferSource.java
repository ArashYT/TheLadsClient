package com.thelads.core.v1_21_1.embedded.etf.mixin.mixins.mods.iris.old;


import org.spongepowered.asm.mixin.Mixin;
import com.thelads.core.v1_21_1.embedded.etf.mixin.CancelTarget;
import org.spongepowered.asm.mixin.Pseudo;

@Pseudo
@Mixin(CancelTarget.class)
public class MixinFullyBufferedMultiBufferSource {}
