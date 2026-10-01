package com.thelads.core.v1_21_1.embedded.emf.mixin.mixins.exporting;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import com.thelads.core.v1_21_1.embedded.emf.EMF;

@Mixin(value = com.thelads.core.v1_21_1.embedded.etf.mixin.CancelTarget.class)
public class MixinBabyModelTransform {}
