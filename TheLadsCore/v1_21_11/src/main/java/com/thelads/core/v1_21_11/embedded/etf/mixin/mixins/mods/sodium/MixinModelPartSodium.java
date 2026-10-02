package com.thelads.core.v1_21_11.embedded.etf.mixin.mixins.mods.sodium;

import org.spongepowered.asm.mixin.Mixin;

// todo seems to be no longer needed as sodium mixins to Cube now
import com.thelads.core.v1_21_11.embedded.etf.mixin.CancelTarget;

@Mixin(value = CancelTarget.class)
public abstract class MixinModelPartSodium { }
