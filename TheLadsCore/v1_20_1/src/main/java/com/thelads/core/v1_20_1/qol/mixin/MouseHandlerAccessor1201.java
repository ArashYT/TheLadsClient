package com.thelads.core.v1_20_1.qol.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Keep Cursor Position puts the pointer back where MouseHandler keeps it. */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor1201 {
    @Accessor("xpos") void lads$setXpos(double x);
    @Accessor("ypos") void lads$setYpos(double y);
}
