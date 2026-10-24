package com.thelads.core.v26_2.utility.mixin;

import com.thelads.core.client.pack.PackMenuSettings;
import com.thelads.core.v26_2.utility.PackProfilesScreen26;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pack Profiles: a Profiles button under the pack screen's Sort button (PackFilterMixin), top right in the header. */
@Mixin(PackSelectionScreen.class)
abstract class PackScreenMixin26 extends Screen {
    @Shadow @Final private PackSelectionModel model;

    private PackScreenMixin26(Component title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void lads$profiles(CallbackInfo ci) {
        if (!PackMenuSettings.profiles()) return;
        addRenderableWidget(Button.builder(Component.literal("Profiles"),
            b -> minecraft.setScreenAndShow(new PackProfilesScreen26((PackSelectionScreen) (Object) this, (PackModelAccessor26) model)))
            .bounds(width - 118, 27, 112, 20).build());
    }
}
