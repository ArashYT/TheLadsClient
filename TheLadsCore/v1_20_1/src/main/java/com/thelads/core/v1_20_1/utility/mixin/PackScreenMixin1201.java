package com.thelads.core.v1_20_1.utility.mixin;

import com.thelads.core.client.pack.PackMenuSettings;
import com.thelads.core.v1_20_1.utility.PackProfilesScreen1201;
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

/** Pack Profiles: a Profiles button in the pack screen's top right corner, beside the pack search box. */
@Mixin(PackSelectionScreen.class)
abstract class PackScreenMixin1201 extends Screen {
    @Shadow @Final private PackSelectionModel model;

    private PackScreenMixin1201(Component title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void lads$profiles(CallbackInfo ci) {
        if (!PackMenuSettings.profiles()) return;
        int w = Math.max(50, Math.min(80, width / 2 - 108));
        addRenderableWidget(Button.builder(Component.literal("Profiles"),
            b -> minecraft.setScreen(new PackProfilesScreen1201((PackSelectionScreen) (Object) this, (PackModelAccessor1201) model)))
            .bounds(width - w - 4, 6, w, 20).build());
    }
}
