package com.thelads.core.v1_20_1.mixin.menus;

import com.thelads.core.shared.ServerNames;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.EditServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
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
public abstract class ServerNameMixin extends Screen {
    @Shadow private EditBox nameEdit;
    @Shadow private EditBox ipEdit;
    @Shadow @Final private ServerData serverData;
    @Unique private final ServerNames.AutoName ladsAutoName = new ServerNames.AutoName(I18n.get("selectServer.defaultName"));

    @Inject(method = "updateAddButtonStatus", at = @At("HEAD"))
    private void ladsAutoName(CallbackInfo ci) {
        String name = ladsAutoName.update(nameEdit.getValue(), ipEdit.getValue());
        if (name != null) nameEdit.setValue(name); // its responder calls back here with the same address: no loop
    }

    protected ServerNameMixin() {
        super(Component.empty());
    }

    /** A new server (empty address) opens on the address field, so the IP can be typed or pasted at once; editing keeps vanilla's focus. */
    @Inject(method = "init", at = @At("TAIL"))
    private void ladsFocusAddress(CallbackInfo ci) {
        if (serverData.ip == null || serverData.ip.isEmpty()) setInitialFocus(ipEdit);
    }
}
