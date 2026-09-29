package com.thelads.core.v26_2.mixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.OptionsList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(OptionsList.class)
public class InactivityOptionMixin {
    @ModifyVariable(method="addSmall([Lnet/minecraft/client/OptionInstance;)V",at=@At("HEAD"),argsOnly=true,require=1)
    private OptionInstance<?>[] ladsRemoveVanillaLimiter(OptionInstance<?>[] options){
        return java.util.Arrays.stream(options).filter(o->o!=Minecraft.getInstance().options.inactivityFpsLimit()).toArray(OptionInstance<?>[]::new);
    }
}
