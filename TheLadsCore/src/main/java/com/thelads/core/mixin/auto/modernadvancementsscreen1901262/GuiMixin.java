package com.thelads.core.mixin.auto.modernadvancementsscreen1901262;

import com.thelads.core.features.auto.modernadvancements.client.screen.ModernAdvancementsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// NOTE: targets Minecraft (not Gui) — in 26.1.2 setScreen/screen live on Minecraft,
// whereas the 26.2 original mod hooked Gui.setScreen.
@Mixin({Minecraft.class})
public class GuiMixin {
   @Inject(
      method = {"setScreen"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onSetScreen(Screen screen, CallbackInfo ci) {
      Minecraft client = (Minecraft)(Object)this;
      if (screen instanceof AdvancementsScreen) {
         if (client.player == null || client.player.connection == null) {
            return;
         }

         ModernAdvancementsScreen customScreen = new ModernAdvancementsScreen(client.player.connection.getAdvancements(), client.screen);
         client.setScreen(customScreen);
         ci.cancel();
      }
   }
}
