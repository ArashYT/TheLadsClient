package com.thelads.core.v1_8_9.perf.mixin;

import java.util.Map;
import net.minecraft.client.resources.model.ModelBakery;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ModelBakery.class)
public interface ModelBakeryAccessor189 {
    @Accessor("models") Map<?, ?> lads$models();
    @Accessor("variants") Map<?, ?> lads$variants();
    @Accessor("blockDefinitions") Map<?, ?> lads$blockDefinitions();
}
