package com.thelads.core.mixin.auto.modernadvancementsscreen1901262;

import java.util.Optional;
import com.thelads.core.features.auto.modernadvancements.ModernAdvancementsClient;
import com.thelads.core.features.auto.modernadvancements.client.screen.ModernAdvancementsScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.ClickEvent.Custom;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({Screen.class})
public class ScreenClickMixin {
   @Inject(
      method = {"defaultHandleGameClickEvent"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void modern$handleGameClickEvent(ClickEvent event, Minecraft minecraft, @Nullable Screen activeScreen, CallbackInfo ci) {
      if (event instanceof Custom custom) {
         Identifier id = custom.id();
         Optional<Tag> payload = custom.payload();
         if (id.equals(Identifier.fromNamespaceAndPath("modern-advancements", "open_advancement"))) {
            if (minecraft.getConnection() != null) {
               payload.flatMap(Tag::asString).map(Identifier::parse).ifPresent(tag -> ModernAdvancementsClient.pendingFocusAdvancementId = tag);
               minecraft.setScreenAndShow(new ModernAdvancementsScreen(minecraft.getConnection().getAdvancements(), activeScreen));
               ci.cancel();
            }
         }
      }
   }
}
