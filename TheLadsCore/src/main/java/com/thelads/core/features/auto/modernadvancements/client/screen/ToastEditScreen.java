package com.thelads.core.features.auto.modernadvancements.client.screen;

import com.thelads.core.features.auto.modernadvancements.ModernAdvancementsClient;
import com.thelads.core.features.auto.modernadvancements.config.ModernAdvancementsClientConfig;
import com.thelads.core.features.auto.modernadvancements.data.toast.ModernAdvancementToast;
import com.thelads.core.features.auto.modernadvancements.data.toast.ToastAnimationStyle;
import com.thelads.core.features.auto.modernadvancements.data.toast.ToastBackgroundStyle;
import com.thelads.core.features.auto.modernadvancements.data.toast.ToastDisplayStyle;
import com.thelads.core.features.auto.modernadvancements.data.toast.ToastFrameStyle;
import com.thelads.core.features.auto.modernadvancements.data.toast.ToastIconLayout;
import com.thelads.core.features.auto.modernadvancements.data.toast.ToastSize;
import com.thelads.core.features.auto.modernadvancements.data.tracker.HudAnchor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

public class ToastEditScreen extends Screen {
   private static final int PANEL_W = 185;
   private static final int PANEL_H = 354;
   private static final int ROW_H = 22;
   private static final int CB_SIZE = 14;
   private static final int CB_GAP = 3;
   private static final int LABEL_W = 72;
   private static final int ROW_SIZE = 0;
   private static final int ROW_ANIM = 1;
   private static final int ROW_FRAME = 2;
   private static final int ROW_BG = 3;
   private static final int ROW_ICON = 4;
   private static final int ROW_DISPLAY = 5;
   private static final int ROW_DURATION = 6;
   private static final int ROW_SOUND = 7;
   private static final Identifier VANILLA_TOAST_SPRITE = Identifier.withDefaultNamespace("toast/advancement");
   private static final int VANILLA_W = 160;
   private static final int VANILLA_H = 32;
   private static final long LOOP_MS = 2600L;
   private long loopStartMs;
   private final Screen parent;
   private final ToastSize oldSize;
   private final ToastAnimationStyle oldAnim;
   private final ToastFrameStyle oldFrame;
   private final ToastBackgroundStyle oldBg;
   private final ToastIconLayout oldIconLayout;
   private final ToastDisplayStyle oldDisplayStyle;
   private final long oldDurationMs;
   private final boolean oldSoundTask;
   private final boolean oldSoundChallenge;
   private final HudAnchor oldAnchor;
   private final int oldOffsetX;
   private final int oldOffsetY;
   private ToastSize pendingSize;
   private ToastAnimationStyle pendingAnim;
   private ToastFrameStyle pendingFrame;
   private ToastBackgroundStyle pendingBg;
   private ToastIconLayout pendingIconLayout;
   private ToastDisplayStyle pendingDisplayStyle;
   private float pendingDurationSec;
   private boolean pendingSoundTask;
   private boolean pendingSoundChallenge;
   private HudAnchor pendingAnchor;
   private int pendingOffsetX;
   private int pendingOffsetY;
   private boolean isDraggingPreview;
   private double dragOffsetX;
   private double dragOffsetY;
   private boolean isDraggingSlider;
   private final float[] stackPreviewRanks = new float[]{0.0F, 1.0F, 2.0F};
   private long stackPreviewLastMs = -1L;

   public ToastEditScreen(Screen parent) {
      super(Component.translatable("gui.advancements.text.toast.edit"));
      this.parent = parent;
      ModernAdvancementsClientConfig cfg = ModernAdvancementsClient.CONFIG;
      this.oldSize = cfg.toastSize();
      this.pendingSize = this.oldSize;
      this.oldAnim = cfg.toastAnimation();
      this.pendingAnim = this.oldAnim;
      this.oldFrame = cfg.toastFrameStyle();
      this.pendingFrame = this.oldFrame;
      this.oldBg = cfg.toastBackground();
      this.pendingBg = this.oldBg;
      this.oldIconLayout = cfg.toastIconLayout();
      this.pendingIconLayout = this.oldIconLayout;
      this.oldDisplayStyle = cfg.toastDisplayStyle();
      this.pendingDisplayStyle = this.oldDisplayStyle;
      this.oldDurationMs = cfg.toastDurationMs();
      this.pendingDurationSec = (float)this.oldDurationMs / 1000.0F;
      this.oldSoundTask = cfg.toastSoundTask();
      this.pendingSoundTask = this.oldSoundTask;
      this.oldSoundChallenge = cfg.toastSoundChallenge();
      this.pendingSoundChallenge = this.oldSoundChallenge;
      this.oldAnchor = cfg.toastAnchor();
      this.pendingAnchor = this.oldAnchor;
      this.oldOffsetX = cfg.toastOffsetX();
      this.pendingOffsetX = this.oldOffsetX;
      this.oldOffsetY = cfg.toastOffsetY();
      this.pendingOffsetY = this.oldOffsetY;
      this.loopStartMs = Util.getMillis();
   }

   private int panelX() {
      return this.width - 185 - 12;
   }

   private int panelY() {
      return (this.height - 354) / 2;
   }

   private int rowY(int idx) {
      return this.panelY() + 26 + idx * 22;
   }

   private int anchorSectionY() {
      return this.rowY(7) + 22 + 5;
   }

   private int anchorGridY() {
      return this.anchorSectionY() + 9 + 4;
   }

   private int anchorGridX() {
      return this.panelX() + 55;
   }

   protected void init() {
      super.init();
      this.clearWidgets();
      int px = this.panelX();
      int py = this.panelY();
      int bw = 84;
      int btnY = py + 354 - 48;
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.advancements.hud.edit.reset"), var1x -> this.reset()).bounds(px + 6, btnY, bw, 20).build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.advancements.hud.edit.default"), var1x -> this.applyDefault())
            .bounds(px + 6 + bw + 4, btnY, bw, 20)
            .build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.advancements.hud.edit.save"), var1x -> this.save()).bounds(px + 6, btnY + 24, bw, 20).build()
      );
      this.addRenderableWidget(
         Button.builder(Component.translatable("gui.advancements.hud.edit.cancel"), var1x -> this.cancel()).bounds(px + 6 + bw + 4, btnY + 24, bw, 20).build()
      );
   }

   private int[] getPreviewOrigin() {
      int w = this.pendingSize.width;
      int h = this.pendingSize.height;
      int bx = Math.clamp((long)(this.pendingAnchor.getRefX(this.width) - this.pendingAnchor.getPivotX(w) + this.pendingOffsetX), 0, this.width - w);
      int by = Math.clamp((long)(this.pendingAnchor.getRefY(this.height) - this.pendingAnchor.getPivotY(h) + this.pendingOffsetY), 0, this.height - h);
      return new int[]{bx, by};
   }

   public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
      context.fill(0, 0, this.width, this.height, 1426063360);
      long now = Util.getMillis();
      float animPhase = (float)((now - this.loopStartMs) % 2600L) / 2600.0F;
      int[] origin = this.getPreviewOrigin();
      int bx = origin[0];
      int by = origin[1];
      int pw = this.pendingSize.width;
      int ph = this.pendingSize.height;
      if (this.pendingDisplayStyle != ToastDisplayStyle.DISABLED) {
         if (this.pendingDisplayStyle == ToastDisplayStyle.VANILLA) {
            this.renderVanillaPreview(context, mouseX, mouseY);
         } else if (this.pendingDisplayStyle == ToastDisplayStyle.STACKING) {
            this.renderStackingPreview(context, animPhase, now, bx, by, pw, ph, mouseX, mouseY);
         } else {
            this.renderSinglePreview(context, animPhase, bx, by, pw, ph, mouseX, mouseY);
         }
      }

      this.renderPanel(context, mouseX, mouseY);
      super.extractRenderState(context, mouseX, mouseY, delta);
   }

   private void renderSinglePreview(GuiGraphicsExtractor ctx, float animPhase, int bx, int by, int pw, int ph, int mouseX, int mouseY) {
      float slideX = this.computeSlideX(computeVisiblePortion(animPhase), pw);
      ctx.pose().pushMatrix();
      ctx.pose().translate(bx + slideX, by);
      ModernAdvancementToast.renderFakePreview(
         ctx, this.font, this.pendingSize, this.pendingBg, this.pendingFrame, this.pendingIconLayout, this.pendingAnim, animPhase, 1.0F
      );
      ctx.pose().popMatrix();
      boolean mouseOver = !this.isDraggingSlider && mouseX >= bx && mouseX <= bx + pw && mouseY >= by && mouseY <= by + ph;
      ctx.outline(bx, by, pw, ph, mouseOver ? -1 : -7829368);
   }

   private void renderStackingPreview(GuiGraphicsExtractor ctx, float animPhase, long now, int bx, int by, int pw, int ph, int mouseX, int mouseY) {
      float[] phases = new float[]{animPhase, (animPhase + 0.33F) % 1.0F, (animPhase + 0.66F) % 1.0F};
      boolean[] alive = new boolean[3];

      for (int i = 0; i < 3; i++) {
         alive[i] = phases[i] < 0.95F;
      }

      float[] targetRanks = new float[3];

      for (int i = 0; i < 3; i++) {
         if (!alive[i]) {
            targetRanks[i] = this.stackPreviewRanks[i];
         } else {
            int rank = 0;

            for (int j = 0; j < i; j++) {
               if (alive[j]) {
                  rank++;
               }
            }

            targetRanks[i] = rank;
         }
      }

      if (this.stackPreviewLastMs > 0L && now > this.stackPreviewLastMs) {
         float dt = (float)(now - this.stackPreviewLastMs) / 1000.0F;
         float factor = 1.0F - (float)Math.pow(0.001, dt);

         for (int ix = 0; ix < 3; ix++) {
            this.stackPreviewRanks[ix] = Mth.lerp(factor, this.stackPreviewRanks[ix], targetRanks[ix]);
         }
      }

      this.stackPreviewLastMs = now;

      boolean isBottom = switch (this.pendingAnchor) {
         case BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT -> true;
         default -> false;
      };

      for (int ix = 0; ix < 3; ix++) {
         if (alive[ix]) {
            float phase = phases[ix];
            float slideX = this.computeSlideX(computeVisiblePortion(phase), pw);
            float stackOffset = this.stackPreviewRanks[ix] * (ph + 2);
            float ghostY = by + (isBottom ? -stackOffset : stackOffset);
            ctx.pose().pushMatrix();
            ctx.pose().translate(bx + slideX, ghostY);
            ModernAdvancementToast.renderFakePreview(
               ctx, this.font, this.pendingSize, this.pendingBg, this.pendingFrame, this.pendingIconLayout, this.pendingAnim, phase, 1.0F
            );
            ctx.pose().popMatrix();
         }
      }

      boolean mouseOver = !this.isDraggingSlider && mouseX >= bx && mouseX <= bx + pw && mouseY >= by && mouseY <= by + ph;
      ctx.outline(bx, by, pw, ph, mouseOver ? -1 : -7829368);
   }

   private void renderVanillaPreview(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
      int vx = this.width - 160 - 2;
      int vy = 2;
      ctx.pose().pushMatrix();
      ctx.pose().translate(vx, vy);
      ctx.blitSprite(RenderPipelines.GUI_TEXTURED, VANILLA_TOAST_SPRITE, 0, 0, 160, 32);
      ctx.text(this.font, Component.translatable("advancements.toast.challenge"), 30, 7, -256, false);
      ctx.text(this.font, Component.translatable("advancements.adventure.sleep_in_bed.title"), 30, 18, -1, false);
      ctx.pose().popMatrix();
      boolean mouseOver = !this.isDraggingSlider && mouseX >= vx && mouseX <= vx + 160 && mouseY >= vy && mouseY <= vy + 32;
      ctx.outline(vx, vy, 160, 32, mouseOver ? -1 : -7829368);
   }

   private static float computeVisiblePortion(float phase) {
      if (phase < 0.2F) {
         return phase / 0.2F;
      } else {
         return phase < 0.7F ? 1.0F : 1.0F - (phase - 0.7F) / 0.3F;
      }
   }

   private float computeSlideX(float visiblePortion, int w) {
      if (this.pendingAnim != ToastAnimationStyle.SLIDE) {
         return 0.0F;
      } else {
         float hidden = 1.0F - visiblePortion;

         return switch (this.pendingAnchor) {
            case BOTTOM_LEFT, TOP_LEFT, CENTER_LEFT -> -w * hidden;
            default -> 0.0F;
            case BOTTOM_RIGHT, TOP_RIGHT, CENTER_RIGHT -> w * hidden;
         };
      }
   }

   private void renderPanel(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
      int px = this.panelX();
      int py = this.panelY();
      ctx.fill(px, py, px + 185, py + 354, -770633455);
      ctx.outline(px, py, 185, 354, -8355712);
      ctx.centeredText(this.font, this.title, px + 92, py + 7, -1);
      ctx.fill(px + 5, py + 19, px + 185 - 5, py + 20, -11513776);
      this.renderLabeledCheckboxRow(ctx, mouseX, mouseY, "size", new String[]{"small", "medium", "large"}, this.pendingSize.ordinal(), 0);
      this.renderLabeledCheckboxRow(ctx, mouseX, mouseY, "animation", new String[]{"slide", "fade", "pop", "none"}, this.pendingAnim.ordinal(), 1);
      this.renderLabeledCheckboxRow(ctx, mouseX, mouseY, "frame", new String[]{"per_type", "uniform", "none"}, this.pendingFrame.ordinal(), 2);
      this.renderLabeledCheckboxRow(ctx, mouseX, mouseY, "background", new String[]{"solid", "gradient", "transparent"}, this.pendingBg.ordinal(), 3);
      this.renderLabeledCheckboxRow(ctx, mouseX, mouseY, "icon", new String[]{"small", "large"}, this.pendingIconLayout.ordinal(), 4);
      this.renderLabeledCheckboxRow(
         ctx, mouseX, mouseY, "display", new String[]{"single", "stacking", "disabled", "vanilla"}, this.pendingDisplayStyle.ordinal(), 5
      );
      this.renderDurationRow(ctx, mouseX, mouseY);
      this.renderSoundRow(ctx, mouseX, mouseY);
      ctx.text(this.font, Component.translatable("gui.advancements.text.toast.position"), this.panelX() + 6, this.anchorSectionY(), -7829368, false);
      this.renderAnchorGrid(ctx, mouseX, mouseY);
   }

   private void renderLabeledCheckboxRow(
      GuiGraphicsExtractor ctx, int mouseX, int mouseY, String label, String[] opts, int selectedIdx, int row
   ) {
      int px = this.panelX();
      int y = this.rowY(row);
      int midY = y + (22 - 9) / 2;
      ctx.text(this.font, Component.translatable("gui.advancements.text.toast.label." + label), px + 6, midY, -7829368, false);
      int count = opts.length;
      int totalW = count * 14 + (count - 1) * 3;
      int startX = px + 185 - 6 - totalW;
      int cbY = y + 4;

      for (int i = 0; i < count; i++) {
         int cx = startX + i * 17;
         boolean selected = i == selectedIdx;
         boolean hovered = mouseX >= cx && mouseX <= cx + 14 && mouseY >= cbY && mouseY <= cbY + 14;
         ctx.fill(cx, cbY, cx + 14, cbY + 14, selected ? -14927002 : (hovered ? -13619152 : -15066598));
         ctx.outline(cx, cbY, 14, 14, selected ? -12276993 : (hovered ? -9408400 : -12961222));
         if (selected) {
            ctx.centeredText(this.font, "✔", cx + 7, cbY + (14 - 9) / 2, -7811841);
         }

         if (hovered) {
            ctx.setTooltipForNextFrame(this.font, Component.translatable("gui.advancements.text.toast.option." + opts[i]), mouseX, mouseY);
         }
      }
   }

   private void renderDurationRow(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
      int px = this.panelX();
      int y = this.rowY(6);
      int sliderX = px + 72 + 8;
      int sliderW = 93;
      int sliderY = y + 7;
      ctx.text(this.font, Component.translatable("gui.advancements.text.toast.duration"), px + 6, y + (22 - 9) / 2, -7829368, false);
      ctx.fill(sliderX, sliderY, sliderX + sliderW, sliderY + 8, -15066598);
      ctx.outline(sliderX, sliderY, sliderW, 8, -12961222);
      float t = this.pendingDurationSec / 60.0F;
      int fillW = (int)(t * (sliderW - 6));
      if (fillW > 0) {
         ctx.fill(sliderX + 1, sliderY + 1, sliderX + 1 + fillW, sliderY + 7, -13408615);
      }

      int handleX = sliderX + (int)(t * (sliderW - 8));
      ctx.fill(handleX, sliderY - 1, handleX + 8, sliderY + 9, -12276993);
      String dStr = this.pendingDurationSec < 10.0F ? String.format("%.1fs", this.pendingDurationSec) : String.format("%.0fs", this.pendingDurationSec);
      ctx.centeredText(this.font, Component.literal(dStr), sliderX + sliderW / 2, sliderY + (8 - 9) / 2, -2236963);
      if (mouseX >= sliderX && mouseX <= sliderX + sliderW && mouseY >= sliderY - 2 && mouseY <= sliderY + 10) {
         ctx.setTooltipForNextFrame(this.font, Component.translatable("gui.advancements.text.toast.diplay_duration", new Object[]{dStr}), mouseX, mouseY);
      }
   }

   private void renderSoundRow(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
      int px = this.panelX();
      int y = this.rowY(7);
      int cbY = y + 4;
      int rightX = px + 185 - 6;
      ctx.text(this.font, Component.translatable("gui.advancements.text.toast.sound"), px + 6, y + (22 - 9) / 2, -7829368, false);
      int x2 = rightX - 14;
      int x1 = x2 - 3 - 14;
      this.renderStandaloneCheckbox(ctx, mouseX, mouseY, x1, cbY, this.pendingSoundTask, "gui.advancements.text.toast.task_sound");
      this.renderStandaloneCheckbox(ctx, mouseX, mouseY, x2, cbY, this.pendingSoundChallenge, "gui.advancements.text.toast.challenge_sound");
   }

   private void renderStandaloneCheckbox(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int cx, int cbY, boolean checked, String tooltip) {
      boolean hovered = mouseX >= cx && mouseX <= cx + 14 && mouseY >= cbY && mouseY <= cbY + 14;
      ctx.fill(cx, cbY, cx + 14, cbY + 14, checked ? -14927002 : (hovered ? -13619152 : -15066598));
      ctx.outline(cx, cbY, 14, 14, checked ? -12276993 : (hovered ? -9408400 : -12961222));
      if (checked) {
         ctx.centeredText(this.font, "✔", cx + 7, cbY + (14 - 9) / 2, -7811841);
      }

      if (hovered) {
         ctx.setTooltipForNextFrame(this.font, Component.translatable(tooltip), mouseX, mouseY);
      }
   }

   private void renderAnchorGrid(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
      int gx = this.anchorGridX();
      int gy = this.anchorGridY();
      HudAnchor[] anchors = HudAnchor.values();

      for (int row = 0; row < 3; row++) {
         for (int col = 0; col < 3; col++) {
            HudAnchor anchor = anchors[row * 3 + col];
            int ax = gx + col * 25;
            int ay = gy + row * 25;
            boolean selected = anchor == this.pendingAnchor;
            boolean hovered = mouseX >= ax && mouseX <= ax + 22 && mouseY >= ay && mouseY <= ay + 22;
            ctx.fill(ax, ay, ax + 22, ay + 22, selected ? -16759672 : (hovered ? -13421773 : -14540254));
            ctx.outline(ax, ay, 22, 22, selected ? -12276993 : (hovered ? -8355712 : -12303292));
            ctx.centeredText(this.font, anchor.symbol(), ax + 11, ay + (22 - 9) / 2, selected ? -12276993 : -3355444);
         }
      }
   }

   public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
      double mx = click.x();
      double my = click.y();
      if (click.button() != 0) {
         return super.mouseClicked(click, doubled);
      } else if (this.tryRowClick(mx, my, 0, 3)) {
         return true;
      } else if (this.tryRowClick(mx, my, 1, 4)) {
         return true;
      } else if (this.tryRowClick(mx, my, 2, 3)) {
         return true;
      } else if (this.tryRowClick(mx, my, 3, 3)) {
         return true;
      } else if (this.tryRowClick(mx, my, 4, 2)) {
         return true;
      } else if (this.tryRowClick(mx, my, 5, 4)) {
         return true;
      } else if (this.trySliderClick(mx, my)) {
         return true;
      } else if (this.trySoundClick(mx, my)) {
         return true;
      } else if (this.tryAnchorClick(mx, my)) {
         return true;
      } else {
         int px = this.panelX();
         int py = this.panelY();
         boolean overPanel = mx >= px && mx <= px + 185 && my >= py && my <= py + 354;
         if (!overPanel) {
            int[] origin = this.getPreviewOrigin();
            int pw = this.pendingSize.width;
            int ph = this.pendingSize.height;
            if (mx >= origin[0] && mx <= origin[0] + pw && my >= origin[1] && my <= origin[1] + ph) {
               this.isDraggingPreview = true;
               this.dragOffsetX = mx - origin[0];
               this.dragOffsetY = my - origin[1];
               return true;
            }
         }

         return super.mouseClicked(click, doubled);
      }
   }

   private boolean tryRowClick(double mx, double my, int row, int count) {
      int px = this.panelX();
      int y = this.rowY(row);
      int totalW = count * 14 + (count - 1) * 3;
      int startX = px + 185 - 6 - totalW;
      int cbY = y + 4;

      for (int i = 0; i < count; i++) {
         int cx = startX + i * 17;
         if (mx >= cx && mx <= cx + 14 && my >= cbY && my <= cbY + 14) {
            this.playClickSound();
            switch (row) {
               case 0:
                  this.pendingSize = ToastSize.values()[i];
                  break;
               case 1:
                  this.pendingAnim = ToastAnimationStyle.values()[i];
                  this.resetLoopAndStack();
                  break;
               case 2:
                  this.pendingFrame = ToastFrameStyle.values()[i];
                  break;
               case 3:
                  this.pendingBg = ToastBackgroundStyle.values()[i];
                  break;
               case 4:
                  this.pendingIconLayout = ToastIconLayout.values()[i];
                  break;
               case 5:
                  this.pendingDisplayStyle = ToastDisplayStyle.values()[i];
                  this.resetLoopAndStack();
            }

            return true;
         }
      }

      return false;
   }

   private boolean trySliderClick(double mx, double my) {
      int px = this.panelX();
      int y = this.rowY(6);
      int sliderX = px + 72 + 8;
      int sliderW = 93;
      int sliderY = y + 7;
      if (mx >= sliderX && mx <= sliderX + sliderW && my >= sliderY - 2 && my <= sliderY + 10) {
         this.isDraggingSlider = true;
         float t = (float)((mx - sliderX) / sliderW);
         this.pendingDurationSec = Mth.clamp(t * 60.0F, 0.0F, 60.0F);
         return true;
      } else {
         return false;
      }
   }

   private boolean trySoundClick(double mx, double my) {
      int px = this.panelX();
      int y = this.rowY(7);
      int cbY = y + 4;
      int rightX = px + 185 - 6;
      int x2 = rightX - 14;
      int x1 = x2 - 3 - 14;
      if (mx >= x1 && mx <= x1 + 14 && my >= cbY && my <= cbY + 14) {
         this.playClickSound();
         this.pendingSoundTask = !this.pendingSoundTask;
         return true;
      } else if (mx >= x2 && mx <= x2 + 14 && my >= cbY && my <= cbY + 14) {
         this.playClickSound();
         this.pendingSoundChallenge = !this.pendingSoundChallenge;
         return true;
      } else {
         return false;
      }
   }

   private boolean tryAnchorClick(double mx, double my) {
      int gx = this.anchorGridX();
      int gy = this.anchorGridY();
      HudAnchor[] anchors = HudAnchor.values();

      for (int row = 0; row < 3; row++) {
         for (int col = 0; col < 3; col++) {
            int ax = gx + col * 25;
            int ay = gy + row * 25;
            if (mx >= ax && mx <= ax + 22 && my >= ay && my <= ay + 22) {
               this.playClickSound();
               this.pendingAnchor = anchors[row * 3 + col];
               this.pendingOffsetX = 0;
               this.pendingOffsetY = 0;
               return true;
            }
         }
      }

      return false;
   }

   public boolean mouseDragged(MouseButtonEvent click, double offsetX, double offsetY) {
      if (click.button() == 0) {
         if (this.isDraggingPreview) {
            double mx = click.x();
            double my = click.y();
            int pw = this.pendingSize.width;
            int ph = this.pendingSize.height;
            this.pendingOffsetX = (int)(mx - this.dragOffsetX - this.pendingAnchor.getRefX(this.width) + this.pendingAnchor.getPivotX(pw));
            this.pendingOffsetY = (int)(my - this.dragOffsetY - this.pendingAnchor.getRefY(this.height) + this.pendingAnchor.getPivotY(ph));
            return true;
         }

         if (this.isDraggingSlider) {
            int px = this.panelX();
            int sliderX = px + 72 + 8;
            int sliderW = 93;
            float t = (float)((click.x() - sliderX) / sliderW);
            this.pendingDurationSec = Mth.clamp(t * 60.0F, 0.0F, 60.0F);
            return true;
         }
      }

      return super.mouseDragged(click, offsetX, offsetY);
   }

   public boolean mouseReleased(MouseButtonEvent click) {
      this.isDraggingPreview = false;
      this.isDraggingSlider = false;
      return super.mouseReleased(click);
   }

   public boolean keyPressed(KeyEvent input) {
      if (input.input() == 256) {
         this.cancel();
         return true;
      } else {
         return super.keyPressed(input);
      }
   }

   private void playClickSound() {
      AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
   }

   private void resetLoopAndStack() {
      this.loopStartMs = Util.getMillis();
      this.stackPreviewRanks[0] = 0.0F;
      this.stackPreviewRanks[1] = 1.0F;
      this.stackPreviewRanks[2] = 2.0F;
      this.stackPreviewLastMs = -1L;
   }

   private void reset() {
      this.pendingSize = this.oldSize;
      this.pendingAnim = this.oldAnim;
      this.pendingFrame = this.oldFrame;
      this.pendingBg = this.oldBg;
      this.pendingIconLayout = this.oldIconLayout;
      this.pendingDisplayStyle = this.oldDisplayStyle;
      this.pendingDurationSec = (float)this.oldDurationMs / 1000.0F;
      this.pendingSoundTask = this.oldSoundTask;
      this.pendingSoundChallenge = this.oldSoundChallenge;
      this.pendingAnchor = this.oldAnchor;
      this.pendingOffsetX = this.oldOffsetX;
      this.pendingOffsetY = this.oldOffsetY;
      this.resetLoopAndStack();
   }

   private void applyDefault() {
      this.pendingSize = ToastSize.MEDIUM;
      this.pendingAnim = ToastAnimationStyle.SLIDE;
      this.pendingFrame = ToastFrameStyle.AUTO;
      this.pendingBg = ToastBackgroundStyle.SOLID;
      this.pendingIconLayout = ToastIconLayout.LEFT_SMALL;
      this.pendingDisplayStyle = ToastDisplayStyle.SINGLE;
      this.pendingDurationSec = 5.0F;
      this.pendingSoundTask = false;
      this.pendingSoundChallenge = true;
      this.pendingAnchor = HudAnchor.TOP_RIGHT;
      this.pendingOffsetX = 0;
      this.pendingOffsetY = 0;
      this.resetLoopAndStack();
   }

   private void save() {
      ModernAdvancementsClientConfig cfg = ModernAdvancementsClient.CONFIG;
      cfg.toastSize(this.pendingSize);
      cfg.toastAnimation(this.pendingAnim);
      cfg.toastFrameStyle(this.pendingFrame);
      cfg.toastBackground(this.pendingBg);
      cfg.toastIconLayout(this.pendingIconLayout);
      cfg.toastDisplayStyle(this.pendingDisplayStyle);
      cfg.toastDurationMs((long)(this.pendingDurationSec * 1000.0F));
      cfg.toastSoundTask(this.pendingSoundTask);
      cfg.toastSoundChallenge(this.pendingSoundChallenge);
      cfg.toastAnchor(this.pendingAnchor);
      cfg.toastOffset(this.pendingOffsetX, this.pendingOffsetY);
      cfg.save();
      Minecraft.getInstance().setScreenAndShow(this.parent);
   }

   private void cancel() {
      Minecraft.getInstance().setScreenAndShow(this.parent);
   }
}
