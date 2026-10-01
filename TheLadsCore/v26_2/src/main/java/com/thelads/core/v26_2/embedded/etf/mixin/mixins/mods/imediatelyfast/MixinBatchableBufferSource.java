package com.thelads.core.v26_2.embedded.etf.mixin.mixins.mods.imediatelyfast;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import com.thelads.core.v26_2.embedded.etf.mixin.CancelTarget;

@Mixin(CancelTarget.class)
public class MixinBatchableBufferSource {}