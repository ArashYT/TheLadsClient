package com.thelads.core.v1_20_1.input.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MouseHandler.class)
public interface MouseHandlerInvoker1201 {
    @Invoker("onMove")
    void lads$onMove(long window, double x, double y);
}
