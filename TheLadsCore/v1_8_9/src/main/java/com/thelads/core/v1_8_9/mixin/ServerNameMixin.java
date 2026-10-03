package com.thelads.core.v1_8_9.mixin;

import com.thelads.core.shared.ServerNames;
import net.minecraft.client.gui.GuiScreenAddServer;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Add/Edit Server: while the name is empty or still the default, it follows the address with the server's friendly name
 * (ServerNames, known_servers.json). Checked when the screen opens and after each key reaches both fields, before keyTyped
 * enables Done (which needs a name) or Enter saves.
 */
@Mixin(GuiScreenAddServer.class)
public abstract class ServerNameMixin {
    @Shadow private GuiTextField serverNameField;
    @Shadow private GuiTextField serverIPField;
    @Unique private ServerNames.AutoName ladsAutoName;

    @Inject(method = "initGui", at = @At("TAIL"))
    private void ladsAutoNameOnOpen(CallbackInfo ci) {
        ladsAutoName();
    }

    @Inject(method = "keyTyped", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiTextField;textboxKeyTyped(CI)Z",
        ordinal = 1, shift = At.Shift.AFTER))
    private void ladsAutoNameOnKey(char typedChar, int keyCode, CallbackInfo ci) {
        ladsAutoName();
    }

    @Unique
    private void ladsAutoName() {
        if (ladsAutoName == null) ladsAutoName = new ServerNames.AutoName(I18n.format("selectServer.defaultName"));
        String name = ladsAutoName.update(serverNameField.getText(), serverIPField.getText());
        if (name != null) serverNameField.setText(name);
    }
}
