package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.v1_8_9.feature.Cheats189;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiShareToLan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Open to LAN's Allow Cheats lasts beyond the session (Cheats189): it starts on the host's last choice, and Start keeps it. */
@Mixin(GuiShareToLan.class)
public abstract class GuiShareToLanMixin {
    @Shadow private boolean field_146600_i; // Allow Cheats
    @Shadow private void func_146595_g() {} // the buttons' labels

    @Inject(method = "initGui", at = @At("TAIL"), require = 1)
    private void ladsLastChoice(CallbackInfo ci) {
        Boolean last = Cheats189.lanChoice(Minecraft.getMinecraft());
        if (last == null) return;
        field_146600_i = last;
        func_146595_g();
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), require = 1)
    private void ladsKeep(GuiButton button, CallbackInfo ci) {
        if (button.id == 101) Cheats189.lan(Minecraft.getMinecraft(), field_146600_i);
    }
}
