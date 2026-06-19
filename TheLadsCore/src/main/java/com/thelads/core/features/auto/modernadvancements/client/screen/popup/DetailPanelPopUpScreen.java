package com.thelads.core.features.auto.modernadvancements.client.screen.popup;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import com.thelads.core.features.auto.modernadvancements.ModernAdvancementsClient;
import com.thelads.core.features.auto.modernadvancements.data.handler.AdvancementScreenshotManager;
import com.thelads.core.features.auto.modernadvancements.network.ModernAdvancementsPackets;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class DetailPanelPopUpScreen {
   public static final int DEFAULT_PANEL_WIDTH = 350;
   private static final int TRACK_BUTTON_HEIGHT = 20;
   private static final int SCROLLBAR_WIDTH = 8;
   private static final int ADVANCEMENT_AREA_TOP = 70;
   public boolean visible = false;
   private final int panelWidth;
   private final Font font;
   private final DetailPanelPopUpScreen.Listener listener;
   private final DetailPanelPopUpScreen.ProgressProvider progressProvider;
   @Nullable
   private DetailPanelPopUpScreen.StateListener stateListener;
   @Nullable
   private AdvancementNode selectedAdvancement;
   private List<AdvancementNode> pathwayChain = new ArrayList<>();
   private int scrollOffset = 0;
   private int maxScroll = 0;
   private boolean isDraggingScroll = false;
   private int screenshotRenderX = -1;
   private int screenshotRenderY = -1;
   private int screenshotRenderW = -1;
   private int screenshotRenderH = -1;
   private boolean guideExpanded = false;
   private int guideHeaderRenderX = -1;
   private int guideHeaderRenderY = -1;
   @Nullable
   private DetailPanelPopUpScreen.ScreenshotListener screenshotListener;

   public void setScreenshotListener(@Nullable DetailPanelPopUpScreen.ScreenshotListener screenshotListener) {
      this.screenshotListener = screenshotListener;
   }

   public DetailPanelPopUpScreen(
      Font font, DetailPanelPopUpScreen.Listener listener, DetailPanelPopUpScreen.ProgressProvider progressProvider
   ) {
      this.font = font;
      this.listener = listener;
      this.progressProvider = progressProvider;

      this.panelWidth = switch (Minecraft.getInstance().getWindow().getGuiScale()) {
         case 1 -> 550;
         case 2 -> 350;
         case 3 -> 250;
         case 4 -> 150;
         default -> 350;
      };
   }

   public void setStateListener(@Nullable DetailPanelPopUpScreen.StateListener stateListener) {
      this.stateListener = stateListener;
   }

   private void notifyStateChanged() {
      if (this.stateListener != null) {
         this.stateListener.onStateChanged();
      }
   }

   public void open(AdvancementNode node) {
      this.selectedAdvancement = node;
      this.pathwayChain = this.buildPathwayChain(node);
      this.visible = true;
      this.scrollOffset = 0;
      this.guideExpanded = false;
      this.notifyStateChanged();
   }

   public void close() {
      this.visible = false;
      this.selectedAdvancement = null;
      this.notifyStateChanged();
   }

   public void setSelected(@Nullable AdvancementNode node) {
      this.selectedAdvancement = node;
      this.pathwayChain = (List<AdvancementNode>)(node != null ? this.buildPathwayChain(node) : new ArrayList<>());
      this.notifyStateChanged();
   }

   @Nullable
   public AdvancementNode getSelected() {
      return this.selectedAdvancement;
   }

   public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, int screenWidth, int screenHeight) {
      if (this.visible && this.selectedAdvancement != null) {
         this.screenshotRenderX = -1;
         this.screenshotRenderY = -1;
         this.screenshotRenderW = -1;
         this.screenshotRenderH = -1;
         this.guideHeaderRenderX = -1;
         this.guideHeaderRenderY = -1;
         int panelX = screenWidth - this.panelWidth;
         int panelY = 70;
         int panelHeight = screenHeight - 70 - 40;
         int maxWidth = this.panelWidth - 34;
         int scissorBottom = panelY + panelHeight - 20 - 15;
         int visibleHeight = scissorBottom - (panelY + 15);
         context.fill(panelX, panelY, screenWidth, panelY + panelHeight, -536870912);
         context.outline(panelX, panelY, this.panelWidth, panelHeight, -10461088);
         this.maxScroll = Math.max(0, this.calculateContentHeight(maxWidth) - visibleHeight);
         this.scrollOffset = Math.clamp((long)this.scrollOffset, 0, this.maxScroll);
         int contentX = panelX + 15;
         int contentY = panelY + 15 - this.scrollOffset;
         context.enableScissor(panelX, panelY, screenWidth, scissorBottom);
         Advancement advancement = this.selectedAdvancement.advancement();
         Optional<DisplayInfo> display = advancement.display();
         if (this.pathwayChain.size() > 1) {
            contentY = this.renderPathwayChain(context, mouseX, contentX, contentY, scissorBottom, panelX, screenWidth);
         }

         AdvancementNode parentNode = this.selectedAdvancement.parent();
         if (parentNode != null) {
            Optional<DisplayInfo> parentDisplay = parentNode.advancement().display();
            if (parentDisplay.isPresent()) {
               String parentTitle = parentDisplay.get().getTitle().getString();
               int parentLabelWidth = this.font.width(Component.translatable("gui.advancements.text.parent").getString());
               int parentLinkX = contentX + parentLabelWidth + 4;
               boolean parentHovered = mouseX >= parentLinkX
                  && mouseX <= parentLinkX + this.font.width(parentTitle)
                  && mouseY >= contentY + this.scrollOffset
                  && mouseY <= contentY + 9 + this.scrollOffset;
               context.text(this.font, Component.translatable("gui.advancements.text.parent"), contentX, contentY, -7829368, false);
               context.text(this.font, parentDisplay.get().getTitle(), parentLinkX, contentY, parentHovered ? -1 : -12281345, false);
               if (parentHovered) {
                  context.fill(parentLinkX, contentY + 9, parentLinkX + this.font.width(parentTitle), contentY + 9 + 1, -12281345);
               }

               contentY += 9 + 5;
            }
         }

         if (display.isPresent()) {
            this.renderMainContent(context, contentX, contentY, maxWidth, advancement, display.get(), mouseX, mouseY);
         }

         context.disableScissor();
         if (this.maxScroll > 0) {
            this.renderScrollbar(context, screenWidth - 8 - 5, panelY + 20, visibleHeight - 4, this.scrollOffset, this.maxScroll);
         }
      }
   }

   private int renderPathwayChain(GuiGraphicsExtractor context, int mouseX, int contentX, int chainY, int scissorBottom, int panelX, int screenWidth) {
      int chainX = contentX;
      int arrowW = this.font.width(" > ");

      for (int ci = 0; ci < this.pathwayChain.size(); ci++) {
         AdvancementNode chainNode = this.pathwayChain.get(ci);
         Optional<DisplayInfo> chainDisplay = chainNode.advancement().display();
         if (!chainDisplay.isEmpty()) {
            ItemStack chainIcon = chainDisplay.get().getIcon().create();
            boolean chainHovered = mouseX >= chainX
               && mouseX <= chainX + 12
               && mouseX >= chainY + this.scrollOffset
               && mouseX <= chainY + 12 + this.scrollOffset;
            if (!chainIcon.isEmpty()) {
               context.enableScissor(panelX, 70, screenWidth - 4, scissorBottom);
               context.pose().pushMatrix();
               context.pose().translate(chainX, chainY);
               context.pose().scale(0.75F, 0.75F);
               context.item(chainIcon, 0, 0);
               context.pose().popMatrix();
               context.disableScissor();
               if (chainHovered) {
                  context.outline(chainX - 1, chainY - 1, 14, 14, -1);
               }
            }

            chainX += 14;
            if (ci < this.pathwayChain.size() - 1) {
               context.text(this.font, ">", chainX, chainY + 2, -10066330, false);
               chainX += arrowW;
            }
         }
      }

      return chainY + 18;
   }

   private void renderMainContent(
      GuiGraphicsExtractor context,
      int contentX,
      int contentY,
      int maxWidth,
      Advancement advancement,
      DisplayInfo advDisplay,
      int mouseX,
      int mouseY
   ) {
      boolean hidden = advDisplay.isHidden();
      if (!advDisplay.getIcon().create().isEmpty()) {
         context.item(advDisplay.getIcon().create(), contentX, contentY);
         contentY += 20;
      }

      context.text(this.font, advDisplay.getTitle(), contentX, contentY, hidden ? -7829368 : -1, true);
      if (hidden) {
         int titleWidth = this.font.width(advDisplay.getTitle());
         context.text(this.font, Component.translatable("gui.advancements.text.hidden"), contentX + titleWidth + 4, contentY, -10066364, false);
      }

      contentY += 20;
      context.text(this.font, Component.literal(advDisplay.getType().toString()), contentX, contentY, -5592576, false);
      contentY += 15;

      for (FormattedCharSequence line : this.font.split(advDisplay.getDescription(), maxWidth)) {
         context.text(this.font, line, contentX, contentY, hidden ? -8947849 : -5592406, false);
         contentY += 12;
      }

      contentY += 15;
      AdvancementProgress progress = this.progressProvider.getProgress(this.selectedAdvancement.holder());
      if (progress == null || !progress.isDone()) {
         String guideKey = this.getGuideKey(advDisplay);
         if (guideKey != null) {
            contentY = this.renderGuideSection(context, contentX, contentY, maxWidth, guideKey, mouseX, mouseY);
         }
      }

      ModernAdvancementsPackets.SimpleRewards rewards = ModernAdvancementsClient.serverRewards.get(this.selectedAdvancement.holder().id());
      contentY = this.renderRewards(context, contentX, contentY, maxWidth, rewards);
      AdvancementRequirements advReqs = advancement.requirements();
      if (progress != null) {
         this.renderProgressSection(context, contentX, contentY, maxWidth, this.selectedAdvancement.holder(), progress, advReqs, mouseX, mouseY);
      } else {
         this.renderUnknownProgress(context, contentX, contentY, maxWidth, advReqs);
      }
   }

   private int renderRewards(
      GuiGraphicsExtractor context, int contentX, int contentY, int maxWidth, @Nullable ModernAdvancementsPackets.SimpleRewards rewards
   ) {
      if (rewards != null && !rewards.isEmpty()) {
         context.text(this.font, Component.translatable("gui.advancements.text.rewards"), contentX, contentY, -3355444, true);
         contentY += 14;
         if (rewards.experience() > 0) {
            context.text(this.font, Component.literal(rewards.experience() + " XP"), contentX + 8, contentY, -8892, false);
            contentY += 12;
         }

         for (Identifier loot : rewards.loot()) {
            for (FormattedCharSequence line : this.font.split(Component.literal(loot.toString()), maxWidth - 8)) {
               context.text(this.font, line, contentX + 8, contentY, -5579265, false);
               contentY += 12;
            }
         }

         for (Identifier recipe : rewards.recipes()) {
            for (FormattedCharSequence line : this.font.split(Component.literal(recipe.toString()), maxWidth - 8)) {
               context.text(this.font, line, contentX + 8, contentY, -17528, false);
               contentY += 12;
            }
         }

         if (rewards.hasFunction()) {
            context.text(this.font, Component.literal("Function"), contentX + 8, contentY, -4486913, false);
            contentY += 12;
         }

         return contentY + 8;
      } else {
         return contentY;
      }
   }

   private int calculateRewardsHeight(int maxWidth, @Nullable ModernAdvancementsPackets.SimpleRewards rewards) {
      if (rewards != null && !rewards.isEmpty()) {
         int h = 14;
         if (rewards.experience() > 0) {
            h += 12;
         }

         for (Identifier loot : rewards.loot()) {
            h += this.font.split(Component.literal(loot.toString()), maxWidth - 8).size() * 12;
         }

         for (Identifier recipe : rewards.recipes()) {
            h += this.font.split(Component.literal(recipe.toString()), maxWidth - 8).size() * 12;
         }

         if (rewards.hasFunction()) {
            h += 12;
         }

         return h + 8;
      } else {
         return 0;
      }
   }

   private void renderProgressSection(
      GuiGraphicsExtractor context,
      int contentX,
      int contentY,
      int maxWidth,
      AdvancementHolder holder,
      AdvancementProgress progress,
      AdvancementRequirements advReqs,
      int mouseX,
      int mouseY
   ) {
      int totalGroups = advReqs.size();
      int completedGroups = advReqs.count(c -> {
         CriterionProgress cp = progress.getCriterion(c);
         return cp != null && cp.isDone();
      });
      boolean completed = progress.isDone();
      boolean isOr = advReqs.requirements().size() == 1 && ((List<String>)advReqs.requirements().getFirst()).size() > 1;
      int fillWidth = completed ? maxWidth : (totalGroups > 0 ? (int)((float)completedGroups / totalGroups * maxWidth) : 0);
      context.fill(contentX, contentY, contentX + maxWidth, contentY + 20, -13421773);
      context.fill(contentX, contentY, contentX + fillWidth, contentY + 20, completed ? -16733696 : -11184811);
      context.outline(contentX, contentY, maxWidth, 20, -8355712);
      context.centeredText(
         this.font,
         Component.literal(
            (completed ? "✔ " : "")
               + completedGroups
               + "/"
               + totalGroups
               + (completed ? " - " + Component.translatable("gui.advancements.text.requirements.completed").getString() + "!" : "")
         ),
         contentX + maxWidth / 2,
         contentY + 6,
         completed ? -16711936 : -1
      );
      contentY += 35;
      if (completed) {
         Instant date = progress.getFirstProgressDate();
         if (date != null) {
            context.text(
               this.font,
               Component.literal(Component.translatable("gui.advancements.text.requirements.completed_on").getString() + " " + getDateFormatter().format(date)),
               contentX,
               contentY,
               -16733696,
               false
            );
            contentY += 15;
         }

         Identifier advId = holder.id();
         AdvancementScreenshotManager.ensureLoaded(advId, Minecraft.getInstance());
         Identifier texId = AdvancementScreenshotManager.getTextureId(advId);
         if (texId != null) {
            int[] dims = AdvancementScreenshotManager.getTextureDimensions(advId);
            if (dims != null && dims[0] > 0) {
               context.text(this.font, Component.translatable("gui.advancements.text.screenshot"), contentX, contentY, -7829368, false);
               contentY += 9 + 4;
               int displayH = (int)((float)dims[1] / dims[0] * maxWidth);
               context.blit(texId, contentX, contentY, contentX + maxWidth, contentY + displayH, 0.0F, 1.0F, 0.0F, 1.0F);
               this.screenshotRenderX = contentX;
               this.screenshotRenderY = contentY;
               this.screenshotRenderW = maxWidth;
               this.screenshotRenderH = displayH;
               boolean screenshotHovered = mouseX >= contentX && mouseX <= contentX + maxWidth && mouseY >= contentY && mouseY <= contentY + displayH;
               if (screenshotHovered) {
                  context.outline(contentX - 1, contentY - 1, maxWidth + 2, displayH + 2, -5592406);
               }

               contentY += displayH + 5;
            }
         }
      }

      if (totalGroups > 1 || isOr) {
         context.text(
            this.font,
            Component.translatable(isOr ? "gui.advancements.text.requirements.complete_any" : "gui.advancements.text.requirements.complete_all"),
            contentX,
            contentY,
            -3355444,
            true
         );
         contentY += 15;
         int incompletePrefix = this.font.width("○ ");
         int completePrefix = this.font.width("✔ ");
         if (isOr) {
            for (String criterion : (List<String>)advReqs.requirements().getFirst()) {
               CriterionProgress cp = progress.getCriterion(criterion);
               boolean done = cp != null && cp.isDone();
               String prefix = done ? "✔ " : "○ ";
               int prefixWidth = done ? completePrefix : incompletePrefix;
               int color = done ? -16733696 : -7829368;
               List<FormattedCharSequence> lines = this.font.split(Component.literal(criterion), maxWidth - 10 - prefixWidth);

               for (int i = 0; i < lines.size(); i++) {
                  if (i == 0) {
                     context.text(this.font, prefix, contentX + 10, contentY, color, false);
                  }

                  context.text(this.font, lines.get(i), contentX + 10 + prefixWidth, contentY, color, false);
                  contentY += 12;
               }
            }
         } else {
            for (String req : progress.getRemainingCriteria()) {
               List<FormattedCharSequence> lines = this.font.split(Component.literal(req), maxWidth - 10 - incompletePrefix);

               for (int i = 0; i < lines.size(); i++) {
                  if (i == 0) {
                     context.text(this.font, "○ ", contentX + 10, contentY, -7829368, false);
                  }

                  context.text(this.font, lines.get(i), contentX + 10 + incompletePrefix, contentY, -7829368, false);
                  contentY += 12;
               }
            }

            for (String req : progress.getCompletedCriteria()) {
               List<FormattedCharSequence> lines = this.font.split(Component.literal(req), maxWidth - 10 - completePrefix);

               for (int i = 0; i < lines.size(); i++) {
                  if (i == 0) {
                     context.text(this.font, "✔ ", contentX + 10, contentY, -16733696, false);
                  }

                  context.text(this.font, lines.get(i), contentX + 10 + completePrefix, contentY, -16733696, false);
                  contentY += 12;
               }
            }
         }
      }
   }

   private void renderUnknownProgress(GuiGraphicsExtractor context, int contentX, int contentY, int maxWidth, AdvancementRequirements advReqs) {
      if (!advReqs.isEmpty()) {
         context.text(this.font, Component.translatable("gui.advancements.text.requirements"), contentX, contentY, -3355444, true);
         contentY += 15;
         int prefixWidth = this.font.width("○ ");

         for (String criterion : advReqs.names()) {
            List<FormattedCharSequence> lines = this.font.split(Component.literal(criterion), maxWidth - 10 - prefixWidth);

            for (int i = 0; i < lines.size(); i++) {
               if (i == 0) {
                  context.text(this.font, "○ ", contentX + 10, contentY, -7829368, false);
               }

               context.text(this.font, lines.get(i), contentX + 10 + prefixWidth, contentY, -7829368, false);
               contentY += 12;
            }
         }
      } else {
         context.text(this.font, Component.translatable("gui.advancements.text.progress_unknown"), contentX, contentY, -7829368, false);
      }
   }

   @Nullable
   private String getGuideKey(DisplayInfo display) {
      String key;
      if (display.getDescription().getContents() instanceof TranslatableContents tc) {
         key = tc.getKey() + ".guide";
      } else {
         if (this.selectedAdvancement == null) {
            return null;
         }

         Identifier id = this.selectedAdvancement.holder().id();
         key = "advancements." + id.getPath().replace("/", ".") + ".description.guide";
      }

      String resolved = Language.getInstance().getOrDefault(key);
      return !resolved.isEmpty() && !resolved.equals(key) ? key : null;
   }

   private int renderGuideSection(
      GuiGraphicsExtractor context, int contentX, int contentY, int maxWidth, String guideKey, int mouseX, int mouseY
   ) {
      this.guideHeaderRenderX = contentX;
      this.guideHeaderRenderY = contentY;
      String arrow = this.guideExpanded ? "▼ " : "▶ ";
      Component header = Component.literal(arrow).append(Component.translatable("gui.advancements.text.guide"));
      boolean hovered = mouseX >= contentX && mouseX <= contentX + maxWidth && mouseY >= contentY - 1 && mouseY < contentY + 9 + 7;
      context.fill(contentX, contentY - 1, contentX + maxWidth, contentY + 9 + 7, hovered ? -14013910 : -15066598);
      context.outline(contentX, contentY - 1, maxWidth, 9 + 8, -12303292);
      context.text(this.font, header, contentX + 6, contentY + 4, -3355444, false);
      contentY += 9 + 10;
      if (this.guideExpanded) {
         int boxInset = 6;
         int textInset = 4;
         int contentBoxX = contentX + boxInset;
         int contentBoxWidth = maxWidth - boxInset * 2;
         int boxStartY = contentY + 4;
         String raw = Language.getInstance().getOrDefault(guideKey);
         List<FormattedCharSequence> allLines = new ArrayList<>();

         for (String segment : raw.split("\n", -1)) {
            allLines.addAll(this.font.split(Component.literal(segment), contentBoxWidth - textInset * 2));
         }

         int boxHeight = allLines.size() * 12 + textInset * 2;
         context.fill(contentBoxX, boxStartY, contentBoxX + contentBoxWidth, boxStartY + boxHeight, -15658735);
         context.outline(contentBoxX, boxStartY, contentBoxWidth, boxHeight, -13421773);
         int textY = boxStartY + textInset;

         for (FormattedCharSequence line : allLines) {
            context.text(this.font, line, contentBoxX + textInset, textY, -4473925, false);
            textY += 12;
         }

         contentY = boxStartY + boxHeight + 5;
      }

      return contentY + 8;
   }

   private int calculateGuideHeight(int maxWidth, String guideKey) {
      int h = 9 + 18;
      if (!this.guideExpanded) {
         return h;
      } else {
         int boxInset = 6;
         int textInset = 4;
         int contentBoxWidth = maxWidth - boxInset * 2;
         String raw = Language.getInstance().getOrDefault(guideKey);
         int lines = 0;

         for (String segment : raw.split("\n", -1)) {
            lines += this.font.split(Component.literal(segment), contentBoxWidth - textInset * 2).size();
         }

         return h + 4 + lines * 12 + textInset * 2 + 5;
      }
   }

   private void renderScrollbar(GuiGraphicsExtractor context, int x, int y, int height, int scrollOffset, int maxScroll) {
      context.fill(x, y, x + 8, y + height, Integer.MIN_VALUE);
      int handleHeight = Math.max(20, (int)(height * ((float)height / (height + maxScroll))));
      int handleY = y + (int)((height - handleHeight) * ((float)scrollOffset / maxScroll));
      context.fill(x, handleY, x + 8, handleY + handleHeight, -5592406);
      context.outline(x, handleY, 8, handleHeight, -3355444);
   }

   public boolean mouseClicked(double mouseX, double mouseY, int screenWidth, int screenHeight) {
      if (this.visible && this.selectedAdvancement != null) {
         int panelX = screenWidth - this.panelWidth;
         int panelY = 70;
         int panelHeight = screenHeight - 70 - 40;
         int visibleHeight = panelHeight - 20 - 30;
         int scrollbarX = screenWidth - 8 - 5;
         if (this.maxScroll > 0 && mouseX >= scrollbarX && mouseX <= scrollbarX + 8 && mouseY >= panelY + 20 && mouseY <= panelY + 20 + visibleHeight - 4) {
            this.isDraggingScroll = true;
            this.scrollOffset = Math.clamp((long)((int)((float)(mouseY - (panelY + 20)) / (visibleHeight - 4) * this.maxScroll)), 0, this.maxScroll);
            return true;
         } else {
            int detailScissorBottom = panelY + panelHeight - 20 - 15;
            if (this.screenshotListener != null
               && this.screenshotRenderX >= 0
               && mouseX >= this.screenshotRenderX
               && mouseX <= this.screenshotRenderX + this.screenshotRenderW
               && mouseY >= this.screenshotRenderY
               && mouseY <= this.screenshotRenderY + this.screenshotRenderH
               && mouseY >= panelY
               && mouseY <= detailScissorBottom) {
               this.screenshotListener.onScreenshotClicked(this.selectedAdvancement.holder().id());
               return true;
            } else if (this.guideHeaderRenderX >= 0
               && mouseX >= this.guideHeaderRenderX
               && mouseX <= this.guideHeaderRenderX + (this.panelWidth - 34)
               && mouseY >= this.guideHeaderRenderY - 1
               && mouseY < this.guideHeaderRenderY + 9 + 7
               && mouseY >= panelY
               && mouseY < detailScissorBottom) {
               this.guideExpanded = !this.guideExpanded;
               return true;
            } else if (mouseX >= panelX && mouseY >= panelY + 15 && mouseY <= panelY + 35) {
               return this.handlePathwayClick(mouseX, mouseY, screenWidth);
            } else {
               return mouseX >= panelX && mouseY > panelY + 15 && mouseY < detailScissorBottom
                  ? this.handleParentLinkClick(mouseX, mouseY, screenWidth)
                  : false;
            }
         }
      } else {
         return false;
      }
   }

   public boolean mouseDragged(double mouseY, int screenHeight) {
      if (!this.isDraggingScroll) {
         return false;
      } else {
         int panelHeight = screenHeight - 70 - 40;
         int visibleHeight = panelHeight - 20 - 30;
         float pct = (float)Math.clamp((mouseY - 90.0) / (visibleHeight - 4), 0.0, 1.0);
         this.scrollOffset = Math.clamp((long)((int)(pct * this.maxScroll)), 0, this.maxScroll);
         return true;
      }
   }

   public boolean mouseScrolled(double mouseX, double verticalAmount, int screenWidth) {
      if (!this.visible || this.selectedAdvancement == null) {
         return false;
      } else if (mouseX >= screenWidth - this.panelWidth && this.maxScroll > 0) {
         this.scrollOffset = Math.clamp((long)(this.scrollOffset - (int)(verticalAmount * 20.0)), 0, this.maxScroll);
         return true;
      } else {
         return false;
      }
   }

   public void mouseReleased() {
      this.isDraggingScroll = false;
   }

   private boolean handlePathwayClick(double mouseX, double mouseY, int screenWidth) {
      if (this.pathwayChain.size() <= 1) {
         return false;
      } else {
         int chainX = screenWidth - this.panelWidth + 15;
         int chainY = 85 - this.scrollOffset;
         int arrowW = this.font.width(" > ");

         for (int ci = 0; ci < this.pathwayChain.size(); ci++) {
            AdvancementNode chainNode = this.pathwayChain.get(ci);
            if (!chainNode.advancement().display().isEmpty()) {
               if (mouseX >= chainX && mouseX <= chainX + 12 && mouseY >= chainY && mouseY <= chainY + 12) {
                  if (chainNode != this.selectedAdvancement) {
                     this.open(chainNode);
                     this.listener.onNavigateTo(chainNode);
                  }

                  return true;
               }

               chainX += 14;
               if (ci < this.pathwayChain.size() - 1) {
                  chainX += arrowW;
               }
            }
         }

         return false;
      }
   }

   private boolean handleParentLinkClick(double mouseX, double mouseY, int screenWidth) {
      if (this.selectedAdvancement == null) {
         return false;
      } else {
         AdvancementNode parentNode = this.selectedAdvancement.parent();
         if (parentNode != null && !parentNode.advancement().display().isEmpty()) {
            Optional<DisplayInfo> parentDisplay = parentNode.advancement().display();
            if (parentDisplay.isEmpty()) {
               return false;
            } else {
               int contentX = screenWidth - this.panelWidth + 15;
               int contentY = 85 - this.scrollOffset;
               if (this.pathwayChain.size() > 1) {
                  contentY += 18;
               }

               String parentTitle = parentDisplay.get().getTitle().getString();
               int parentLinkX = contentX + this.font.width(Component.translatable("gui.advancements.text.parent").getString()) + 4;
               int parentLinkW = this.font.width(parentTitle);
               if (!(mouseX < parentLinkX) && !(mouseX > parentLinkX + parentLinkW) && !(mouseY < contentY) && !(mouseY > contentY + 9)) {
                  this.open(parentNode);
                  this.listener.onNavigateTo(parentNode);
                  return true;
               } else {
                  return false;
               }
            }
         } else {
            return false;
         }
      }
   }

   private List<AdvancementNode> buildPathwayChain(AdvancementNode node) {
      List<AdvancementNode> chain = new ArrayList<>();

      for (AdvancementNode current = node; current != null; current = current.parent()) {
         chain.addFirst(current);
      }

      return chain;
   }

   private int calculateContentHeight(int maxWidth) {
      int h = 0;
      if (this.selectedAdvancement == null) {
         return h;
      } else {
         if (this.pathwayChain.size() > 1) {
            h += 18;
         }

         if (this.selectedAdvancement.parent() != null && this.selectedAdvancement.parent().advancement().display().isPresent()) {
            h += 9 + 5;
         }

         Advancement advancement = this.selectedAdvancement.advancement();
         Optional<DisplayInfo> display = advancement.display();
         if (display.isEmpty()) {
            return h;
         } else {
            DisplayInfo d = display.get();
            if (!d.getIcon().create().isEmpty()) {
               h += 20;
            }

            h += 35;
            h += this.font.split(d.getDescription(), maxWidth).size() * 12;
            h += 15;
            AdvancementProgress guideProgress = this.progressProvider.getProgress(this.selectedAdvancement.holder());
            if (guideProgress == null || !guideProgress.isDone()) {
               String guideKey = this.getGuideKey(d);
               if (guideKey != null) {
                  h += this.calculateGuideHeight(maxWidth, guideKey);
               }
            }

            h += this.calculateRewardsHeight(maxWidth, ModernAdvancementsClient.serverRewards.get(this.selectedAdvancement.holder().id()));
            AdvancementProgress progress = this.progressProvider.getProgress(this.selectedAdvancement.holder());
            if (progress != null) {
               h += 35;
               if (progress.isDone() && progress.getFirstProgressDate() != null) {
                  h += 15;
               }

               if (progress.isDone()) {
                  int[] dims = AdvancementScreenshotManager.getTextureDimensions(this.selectedAdvancement.holder().id());
                  if (dims != null && dims[0] > 0) {
                     h += 9 + 4;
                     h += (int)((float)dims[1] / dims[0] * maxWidth) + 5;
                  }
               }

               AdvancementRequirements advReqs = advancement.requirements();
               boolean isOr = advReqs.requirements().size() == 1 && ((List<String>)advReqs.requirements().getFirst()).size() > 1;
               int criteriaCount = isOr ? ((List<String>)advReqs.requirements().getFirst()).size() : advReqs.size();
               if (criteriaCount > 1 || isOr) {
                  h += 15;
                  int incompletePrefix = this.font.width("○ ");
                  int completePrefix = this.font.width("✔ ");
                  if (isOr) {
                     for (String criterion : (List<String>)advReqs.requirements().getFirst()) {
                        CriterionProgress cp = progress.getCriterion(criterion);
                        boolean done = cp != null && cp.isDone();
                        h += this.font.split(Component.literal(criterion), maxWidth - 10 - (done ? completePrefix : incompletePrefix)).size() * 12;
                     }
                  } else {
                     for (String req : progress.getRemainingCriteria()) {
                        h += this.font.split(Component.literal(req), maxWidth - 10 - incompletePrefix).size() * 12;
                     }

                     for (String req : progress.getCompletedCriteria()) {
                        h += this.font.split(Component.literal(req), maxWidth - 10 - completePrefix).size() * 12;
                     }
                  }
               }
            } else {
               AdvancementRequirements advReqs = advancement.requirements();
               if (!advReqs.isEmpty()) {
                  h += 15;
                  int prefixWidth = this.font.width("○ ");

                  for (String criterion : advReqs.names()) {
                     h += this.font.split(Component.literal(criterion), maxWidth - 10 - prefixWidth).size() * 12;
                  }
               } else {
                  h += 15;
               }
            }

            return h;
         }
      }
   }

   public static int getPanelWidth() {
      return switch (Minecraft.getInstance().getWindow().getGuiScale()) {
         case 1 -> 550;
         case 2 -> 350;
         case 3 -> 250;
         case 4 -> 150;
         default -> 350;
      };
   }

   private static DateTimeFormatter getDateFormatter() {
      String langCode = Minecraft.getInstance().getLanguageManager().getSelected();
      String[] parts = langCode.split("_");
      Locale locale = parts.length >= 2 ? Locale.of(parts[0], parts[1].toUpperCase()) : Locale.of(parts[0]);
      return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale).withZone(ZoneId.systemDefault());
   }

   public interface Listener {
      void onNavigateTo(AdvancementNode var1);
   }

   public interface ProgressProvider {
      @Nullable
      AdvancementProgress getProgress(AdvancementHolder var1);
   }

   public interface ScreenshotListener {
      void onScreenshotClicked(Identifier var1);
   }

   public interface StateListener {
      void onStateChanged();
   }
}
