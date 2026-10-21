package com.thelads.core.v1_8_9.mixin;

import java.util.List;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderGlobal.class)
public interface RenderGlobalAccessor {
    @Accessor("renderInfos")
    List<?> ladsRenderInfos();
}
