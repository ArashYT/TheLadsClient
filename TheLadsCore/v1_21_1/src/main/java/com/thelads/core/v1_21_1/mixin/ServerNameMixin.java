package com.thelads.core.v1_21_1.mixin;

import com.thelads.core.shared.ServerNames;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.EditServerScreen;
import net.minecraft.client.resources.language.I18n;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Add/Edit Server: while the name is empty or still the default, it follows the address with the server's friendly name
 * (ServerNames, known_servers.json). Both fields' responders and init call updateAddButtonStatus, so this sees every change.
 */
@Mixin(EditServerScreen.class)
public abstract class ServerNameMixin {
    @Shadow private EditBox nameEdit;
    @Shadow private EditBox ipEdit;
    @Unique private final ServerNames.AutoName ladsAutoName = new ServerNames.AutoName(I18n.get("selectServer.defaultName"));

    @Inject(method = "updateAddButtonStatus", at = @At("HEAD"))
    private void ladsAutoName(CallbackInfo ci) {
        String name = ladsAutoName.update(nameEdit.getValue(), ipEdit.getValue());
        if (name != null) nameEdit.setValue(name); // its responder calls back here with the same address: no loop
    }
}
