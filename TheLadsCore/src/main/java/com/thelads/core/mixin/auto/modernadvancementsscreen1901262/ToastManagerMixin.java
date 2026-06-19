package com.thelads.core.mixin.auto.modernadvancementsscreen1901262;

import java.util.ArrayDeque;
import java.util.Deque;
import com.thelads.core.features.auto.modernadvancements.ModernAdvancementsClient;
import com.thelads.core.features.auto.modernadvancements.data.handler.AdvancementScreenshotManager;
import com.thelads.core.features.auto.modernadvancements.data.toast.ModernAdvancementToast;
import com.thelads.core.features.auto.modernadvancements.data.toast.ToastDisplayStyle;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ToastManager.class})
public class ToastManagerMixin {
   @Final
   @Shadow
   private Deque<Toast> queued;
   @Unique
   private final Deque<AdvancementHolder> pendingSingle = new ArrayDeque<>();
   @Unique
   private boolean singleActive = false;

   @Inject(
      method = {"addToast"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onAddToast(Toast toast, CallbackInfo ci) {
      if (toast instanceof AdvancementToast advancementToast) {
         AdvancementHolder holder = ((AdvancementToastAccessor)advancementToast).getAdvancement();
         if (ModernAdvancementsClient.CONFIG.takeScreenshots()) {
            AdvancementScreenshotManager.schedule(holder);
         }

         if (ModernAdvancementsClient.CONFIG.toastDisplayStyle() != ToastDisplayStyle.VANILLA) {
            ci.cancel();
            if (ModernAdvancementsClient.CONFIG.toastDisplayStyle() != ToastDisplayStyle.DISABLED) {
               if (ModernAdvancementsClient.CONFIG.toastDisplayStyle() == ToastDisplayStyle.STACKING) {
                  this.queued.add(new ModernAdvancementToast(holder, null));
               } else if (this.singleActive) {
                  this.pendingSingle.add(holder);
               } else {
                  this.singleActive = true;
                  this.enqueueNextSingle(holder);
               }
            }
         }
      }
   }

   @Inject(
      method = {"clear"},
      at = {@At("HEAD")}
   )
   private void onClear(CallbackInfo ci) {
      this.pendingSingle.clear();
      this.singleActive = false;
      ModernAdvancementToast.clearActiveStack();
   }

   @Unique
   private void enqueueNextSingle(AdvancementHolder holder) {
      this.queued.add(new ModernAdvancementToast(holder, () -> {
         if (this.pendingSingle.isEmpty()) {
            this.singleActive = false;
         } else {
            this.enqueueNextSingle(this.pendingSingle.poll());
         }
      }));
   }
}
