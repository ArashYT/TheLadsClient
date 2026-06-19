package com.thelads.core.features.auto.modernadvancements.data.toast;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import com.thelads.core.features.auto.modernadvancements.ModernAdvancementsClient;
import com.thelads.core.features.auto.modernadvancements.config.ModernAdvancementsClientConfig;
import com.thelads.core.features.auto.modernadvancements.data.tracker.HudAnchor;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.components.toasts.Toast.Visibility;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public class ModernAdvancementToast implements Toast {
   private static final long ANIM_MS = 400L;
   private static final long SLIDE_MS = 600L;
   static final List<ModernAdvancementToast> ACTIVE_STACK = new CopyOnWriteArrayList<>();
   private final AdvancementHolder advancement;
   private final AdvancementType type;
   private final ItemStack iconItem;
   @Nullable
   private final Runnable onFinished;
   private Visibility wantedVisibility = Visibility.HIDE;
   private long firstRenderTime = -1L;
   private long exitStartTime = -1L;
   private boolean exitSignaled = false;
   private boolean registered = false;
   private float displayRank = 0.0F;
   private int targetRank = 0;
   private long lastFrameMs = -1L;

   public ModernAdvancementToast(AdvancementHolder advancement, @Nullable Runnable onFinished) {
      this.advancement = advancement;
      this.onFinished = onFinished;
      this.type = advancement.value().display().<AdvancementType>map(DisplayInfo::getType).orElse(AdvancementType.TASK);
      this.iconItem = advancement.value().display().map(d -> d.getIcon().create()).orElse(ItemStack.EMPTY);
   }

   public static void clearActiveStack() {
      ACTIVE_STACK.clear();
   }

   public int width() {
      return ModernAdvancementsClient.CONFIG.toastSize().width;
   }

   public int height() {
      return ModernAdvancementsClient.CONFIG.toastSize().height;
   }

   public int occcupiedSlotCount() {
      return 1;
   }

   public Visibility getWantedVisibility() {
      return this.wantedVisibility;
   }

   public void update(ToastManager manager, long fullyVisibleForMs) {
      if (this.advancement.value().display().isEmpty()) {
         this.wantedVisibility = Visibility.HIDE;
      } else {
         long threshold = (long)(ModernAdvancementsClient.CONFIG.toastDurationMs() * manager.getNotificationDisplayTimeMultiplier());
         boolean shouldHide = fullyVisibleForMs >= threshold;
         if (shouldHide && !this.exitSignaled) {
            this.exitSignaled = true;
            this.exitStartTime = Util.getMillis();
         }

         this.wantedVisibility = shouldHide ? Visibility.HIDE : Visibility.SHOW;
      }
   }

   public void onFinishedRendering() {
      ACTIVE_STACK.remove(this);
      int i = 0;

      while (i < ACTIVE_STACK.size()) {
         ACTIVE_STACK.get(i).targetRank = i++;
      }

      if (this.onFinished != null) {
         this.onFinished.run();
      }
   }

   @Nullable
   public SoundEvent getSoundEvent() {
      return switch (this.type) {
         case CHALLENGE -> ModernAdvancementsClient.CONFIG.toastSoundChallenge() ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : null;
         case TASK -> ModernAdvancementsClient.CONFIG.toastSoundTask() ? SoundEvents.UI_TOAST_IN : null;
         default -> null;
      };
   }

   public float xPos(int screenWidth, float visiblePortion) {
      ModernAdvancementsClientConfig cfg = ModernAdvancementsClient.CONFIG;
      HudAnchor anchor = cfg.toastAnchor();
      int w = this.width();
      int base = Math.clamp((long)(anchor.getRefX(screenWidth) - anchor.getPivotX(w) + cfg.toastOffsetX()), 0, screenWidth - w);
      if (cfg.toastAnimation() != ToastAnimationStyle.SLIDE) {
         return base;
      } else {
         float offset = w * (1.0F - visiblePortion);

         return switch (anchor) {
            case TOP_RIGHT, CENTER_RIGHT, BOTTOM_RIGHT -> base + offset;
            case TOP_LEFT, CENTER_LEFT, BOTTOM_LEFT -> base - offset;
            default -> base;
         };
      }
   }

   public float yPos(int firstSlotIndex) {
      ModernAdvancementsClientConfig cfg = ModernAdvancementsClient.CONFIG;
      HudAnchor anchor = cfg.toastAnchor();
      int h = this.height();
      int screenH = Minecraft.getInstance().getWindow().getGuiScaledHeight();
      int base = Math.clamp((long)(anchor.getRefY(screenH) - anchor.getPivotY(h) + cfg.toastOffsetY()), 0, screenH - h);
      float stack = this.displayRank * (h + 2);

      boolean isBottom = switch (anchor) {
         case BOTTOM_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER -> true;
         default -> false;
      };
      return Math.clamp(base + (isBottom ? -stack : stack), 0.0F, (float)(screenH - h));
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
      long now = Util.getMillis();
      if (this.firstRenderTime < 0L) {
         this.firstRenderTime = now;
      }

      long age = now - this.firstRenderTime;
      if (!this.registered) {
         this.registered = true;
         ACTIVE_STACK.add(this);
         this.targetRank = ACTIVE_STACK.size() - 1;
         this.displayRank = this.targetRank;
         this.lastFrameMs = now;
      }

      if (this.lastFrameMs > 0L && now > this.lastFrameMs) {
         float dt = (float)(now - this.lastFrameMs) / 1000.0F;
         float factor = 1.0F - (float)Math.pow(0.001, dt);
         this.displayRank = Mth.lerp(factor, this.displayRank, this.targetRank);
      }

      this.lastFrameMs = now;
      DisplayInfo display = (DisplayInfo)this.advancement.value().display().orElse(null);
      if (display != null) {
         ToastAnimationStyle anim = ModernAdvancementsClient.CONFIG.toastAnimation();
         int w = this.width();
         int h = this.height();
         float globalAlpha = 1.0F;
         if (anim == ToastAnimationStyle.FADE) {
            if (age < 400L) {
               globalAlpha = (float)age / 400.0F;
            } else if (this.exitStartTime >= 0L) {
               long exitAge = now - this.exitStartTime;
               globalAlpha = Math.max(0.0F, 1.0F - (float)exitAge / 600.0F);
               if (globalAlpha <= 0.0F) {
                  return;
               }
            }
         }

         boolean matrixPushed = false;
         if (anim == ToastAnimationStyle.POP) {
            float scale;
            if (age < 400L) {
               scale = (float)age / 400.0F;
            } else if (this.exitStartTime >= 0L) {
               long exitAge = now - this.exitStartTime;
               scale = Math.max(0.0F, 1.0F - (float)exitAge / 400.0F);
               if (scale <= 0.0F) {
                  return;
               }
            } else {
               scale = 1.0F;
            }

            graphics.pose().pushMatrix();
            graphics.pose().translate(w / 2.0F, h / 2.0F);
            graphics.pose().scale(scale, scale);
            graphics.pose().translate(-w / 2.0F, -h / 2.0F);
            matrixPushed = true;
         }

         this.renderBackground(graphics, w, h, globalAlpha);
         this.renderFrame(graphics, w, h, globalAlpha);
         this.renderContent(graphics, font, display, w, h, globalAlpha);
         if (matrixPushed) {
            graphics.pose().popMatrix();
         }
      }
   }

   private void renderBackground(GuiGraphicsExtractor graphics, int w, int h, float alpha) {
      switch (ModernAdvancementsClient.CONFIG.toastBackground()) {
         case SOLID:
            graphics.fill(0, 0, w, h, withAlpha(this.bgColor(), alpha));
            break;
         case GRADIENT:
            graphics.fill(0, 0, w, h / 2, withAlpha(this.bgColor(), alpha));
            graphics.fill(0, h / 2, w, h, withAlpha(darken(this.bgColor()), alpha));
         case TRANSPARENT:
      }
   }

   private void renderFrame(GuiGraphicsExtractor graphics, int w, int h, float alpha) {
      ToastFrameStyle style = ModernAdvancementsClient.CONFIG.toastFrameStyle();
      if (style != ToastFrameStyle.NONE) {
         int color = withAlpha(style == ToastFrameStyle.AUTO ? this.frameColor() : -8355712, alpha);
         graphics.outline(0, 0, w, h, color);
         if (style == ToastFrameStyle.AUTO && this.type == AdvancementType.CHALLENGE) {
            graphics.outline(2, 2, w - 4, h - 4, withAlpha(darken(this.frameColor()), alpha));
         }
      }
   }

   private void renderContent(GuiGraphicsExtractor graphics, Font font, DisplayInfo display, int w, int h, float alpha) {
      int iconX = 5;
      boolean large = ModernAdvancementsClient.CONFIG.toastIconLayout() == ToastIconLayout.LEFT_LARGE;
      int iconSize = large ? Math.min(h - iconX * 2, 32) : 16;
      int iconY = (h - iconSize) / 2;
      this.renderIcon(graphics, iconX, iconY, iconSize);
      int textX = iconX + iconSize + iconX;
      int typeColor = withAlpha(this.typeTextColor(), alpha);
      int titleColor = withAlpha(-1, alpha);
      List<FormattedCharSequence> titleLines = font.split(display.getTitle(), w - textX - iconX);
      FormattedCharSequence titleLine = titleLines.isEmpty() ? display.getTitle().getVisualOrderText() : titleLines.getFirst();
      if (h >= 48) {
         int mid = h / 2;
         graphics.text(font, display.getType().getDisplayName(), textX, mid - 9 - 1, typeColor, false);
         graphics.text(font, titleLine, textX, mid + 2, titleColor, false);
      } else {
         graphics.text(font, display.getType().getDisplayName(), textX, 5, typeColor, false);
         graphics.text(font, titleLine, textX, 5 + 9 + 1, titleColor, false);
      }
   }

   private void renderIcon(GuiGraphicsExtractor graphics, int x, int y, int size) {
      if (!this.iconItem.isEmpty()) {
         if (size <= 16) {
            graphics.fakeItem(this.iconItem, x, y);
         } else {
            float scale = size / 16.0F;
            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);
            graphics.pose().scale(scale, scale);
            graphics.fakeItem(this.iconItem, 0, 0);
            graphics.pose().popMatrix();
         }
      }
   }

   private int bgColor() {
      return switch (this.type) {
         case CHALLENGE -> -535492070;
         case TASK -> -535752431;
         case GOAL -> -535750127;
         default -> throw new MatchException(null, null);
      };
   }

   private int frameColor() {
      return switch (this.type) {
         case CHALLENGE -> -7851060;
         case TASK -> -11184794;
         case GOAL -> -14505404;
         default -> throw new MatchException(null, null);
      };
   }

   private int typeTextColor() {
      return switch (this.type) {
         case CHALLENGE -> -5614081;
         case TASK -> -7829368;
         case GOAL -> -12268476;
         default -> throw new MatchException(null, null);
      };
   }

   static int withAlpha(int argb, float a) {
      if (a >= 1.0F) {
         return argb;
      } else {
         int origA = argb >> 24 & 0xFF;
         return argb & 16777215 | Mth.clamp((int)(origA * a), 0, 255) << 24;
      }
   }

   static int darken(int argb) {
      int a = argb >> 24 & 0xFF;
      int r = (int)((argb >> 16 & 0xFF) * 0.6F);
      int g = (int)((argb >> 8 & 0xFF) * 0.6F);
      int b = (int)((argb & 0xFF) * 0.6F);
      return a << 24 | r << 16 | g << 8 | b;
   }

   public static void renderFakePreview(
      GuiGraphicsExtractor graphics,
      Font font,
      ToastSize size,
      ToastBackgroundStyle bg,
      ToastFrameStyle frame,
      ToastIconLayout iconLayout,
      ToastAnimationStyle anim,
      float animPhase,
      float globalAlpha
   ) {
      int w = size.width;
      int h = size.height;
      boolean matrixPushed = false;
      if (anim == ToastAnimationStyle.POP) {
         float scale;
         if (animPhase < 0.2F) {
            scale = animPhase / 0.2F;
         } else if (animPhase < 0.7F) {
            scale = 1.0F;
         } else {
            scale = 1.0F - (animPhase - 0.7F) / 0.3F;
         }

         scale = Math.max(0.0F, scale);
         if (scale <= 0.0F) {
            return;
         }

         graphics.pose().pushMatrix();
         graphics.pose().translate(w / 2.0F, h / 2.0F);
         graphics.pose().scale(scale, scale);
         graphics.pose().translate(-w / 2.0F, -h / 2.0F);
         matrixPushed = true;
      }

      float fa = globalAlpha;
      if (anim == ToastAnimationStyle.FADE) {
         float fadeAlpha;
         if (animPhase < 0.2F) {
            fadeAlpha = animPhase / 0.2F;
         } else if (animPhase < 0.7F) {
            fadeAlpha = 1.0F;
         } else {
            fadeAlpha = 1.0F - (animPhase - 0.7F) / 0.3F;
         }

         fa = Math.max(0.0F, fadeAlpha) * globalAlpha;
         if (fa <= 0.0F) {
            if (matrixPushed) {
               graphics.pose().popMatrix();
            }

            return;
         }
      }

      int bgColor = withAlpha(-535752431, fa);
      switch (bg) {
         case SOLID:
            graphics.fill(0, 0, w, h, bgColor);
            break;
         case GRADIENT:
            graphics.fill(0, 0, w, h / 2, bgColor);
            graphics.fill(0, h / 2, w, h, withAlpha(darken(-535752431), fa));
         case TRANSPARENT:
      }

      if (frame != ToastFrameStyle.NONE) {
         int fc = withAlpha(frame == ToastFrameStyle.AUTO ? -11184794 : -8355712, fa);
         graphics.outline(0, 0, w, h, fc);
      }

      int iconX = 5;
      boolean large = iconLayout == ToastIconLayout.LEFT_LARGE;
      int iconSize = large ? Math.min(h - iconX * 2, 32) : 16;
      int iconY = (h - iconSize) / 2;
      graphics.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, withAlpha(-11184811, fa));
      graphics.outline(iconX, iconY, iconSize, iconSize, withAlpha(-8947849, fa));
      int textX = iconX + iconSize + iconX;
      int labelColor = withAlpha(-7829368, fa);
      int titleColor = withAlpha(-1, fa);
      if (h >= 48) {
         int mid = h / 2;
         graphics.text(font, "Advancement", textX, mid - 9 - 1, labelColor, false);
         graphics.text(font, "Example Title", textX, mid + 2, titleColor, false);
      } else {
         graphics.text(font, "Advancement", textX, 5, labelColor, false);
         graphics.text(font, "Example Title", textX, 5 + 9 + 1, titleColor, false);
      }

      if (matrixPushed) {
         graphics.pose().popMatrix();
      }
   }
}
