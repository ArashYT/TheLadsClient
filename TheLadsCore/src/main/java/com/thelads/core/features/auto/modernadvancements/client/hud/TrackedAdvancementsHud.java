package com.thelads.core.features.auto.modernadvancements.client.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.thelads.core.features.auto.modernadvancements.ModernAdvancementsClient;
import com.thelads.core.features.auto.modernadvancements.data.tracker.HudAnchor;
import com.thelads.core.features.auto.modernadvancements.data.tracker.HudState;
import com.thelads.core.features.auto.modernadvancements.data.tracker.TrackerDisplayMode;
import com.thelads.core.features.auto.modernadvancements.data.tracker.TrackingManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

public class TrackedAdvancementsHud implements HudElement {
   static final int ICON_SIZE = 16;
   static final int PADDING = 3;
   public static final int ENTRY_GAP = 2;
   private static final int ICON_TEXT_GAP = 3;

   private static int resolveBoxWidth() {
      return ModernAdvancementsClient.CONFIG.trackerSize().width;
   }

   private static int resolveBoxMaxHeight() {
      return ModernAdvancementsClient.CONFIG.trackerSize().maxHeight;
   }

   public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
      if (!ModernAdvancementsClient.CONFIG.hideTracker()) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.screen == null && mc.getConnection() != null) {
            ClientAdvancements handler = mc.getConnection().getAdvancements();
            Font font = mc.font;
            List<Identifier> tracked = TrackingManager.getInstance().getTracked();
            Set<Identifier> fadingOut = HudState.getFadingOutEntries();
            List<Identifier> toDisplay = new ArrayList<>(tracked);

            for (Identifier id : fadingOut) {
               if (!toDisplay.contains(id)) {
                  toDisplay.add(id);
               }
            }

            if (!toDisplay.isEmpty()) {
               int screenW = mc.getWindow().getGuiScaledWidth();
               int screenH = mc.getWindow().getGuiScaledHeight();
               int boxWidth = resolveBoxWidth();
               int boxMaxHeight = resolveBoxMaxHeight();
               TrackerDisplayMode mode = ModernAdvancementsClient.CONFIG.trackerDisplayMode();
               int totalContentHeight = 0;

               for (Identifier idx : toDisplay) {
                  int h = calculateEntryHeight(font, idx, handler, mode, boxWidth);
                  if (h > 0) {
                     totalContentHeight += h + 2;
                  }
               }

               if (totalContentHeight != 0) {
                  int boxHeight = Math.min(Math.min(boxMaxHeight, totalContentHeight), screenH - 4);
                  int[] origin = computeBoxOrigin(
                     screenW,
                     screenH,
                     boxHeight,
                     boxWidth,
                     ModernAdvancementsClient.CONFIG.boundingBoxAnchor(),
                     ModernAdvancementsClient.CONFIG.boundingBoxOffsetX(),
                     ModernAdvancementsClient.CONFIG.boundingBoxOffsetY()
                  );
                  renderEntries(graphics, font, handler, toDisplay, origin[0], origin[1], boxHeight, boxWidth, mode, false);
               }
            }
         }
      }
   }

   public static void renderEntries(
      GuiGraphicsExtractor graphics,
      Font font,
      ClientAdvancements handler,
      List<Identifier> toDisplay,
      int boxX,
      int boxY,
      int boxHeight,
      int boxWidth,
      TrackerDisplayMode mode,
      boolean isFakePreview
   ) {
      graphics.enableScissor(boxX, boxY, boxX + boxWidth, boxY + boxHeight);
      int y = boxY;

      for (int i = 0; i < toDisplay.size(); i++) {
         Identifier id = toDisplay.get(i);
         boolean isHighlighted = i == 0;
         boolean isFadingOut = !isFakePreview && HudState.getFadingOutEntries().contains(id);
         if (isFadingOut) {
            int fadeAlpha = HudState.getFadeOutOverlayAlpha(id);
            if (fadeAlpha == -1) {
               continue;
            }
         }

         AdvancementNode node = resolveNode(id, handler, isFakePreview);
         if (isFakePreview || node != null) {
            DisplayInfo display = node != null ? (DisplayInfo)node.advancement().display().orElse(null) : null;
            if (isFakePreview || display != null) {
               AdvancementProgress progress = resolveProgress(node, isFakePreview);
               boolean completed = !isFakePreview && progress != null && progress.isDone();
               int entryHeight = isFakePreview ? estimateFakeEntryHeight(font, mode) : calculateEntryHeight(font, id, handler, mode, boxWidth);
               if (entryHeight > 0) {
                  int frameColor = isHighlighted ? -16750934 : (completed ? -16733696 : -11184811);
                  graphics.fill(boxX, y, boxX + boxWidth, y + entryHeight, isHighlighted ? -804187887 : -1073741824);
                  graphics.outline(boxX, y, boxWidth, entryHeight, frameColor);
                  int contentY = y + 3;
                  int textWidth = boxWidth - 6;
                  int row1H = Math.max(16, 9);
                  int iconOffY = (row1H - 16) / 2;
                  if (!isFakePreview && display != null) {
                     ItemStack icon = display.getIcon().create();
                     if (!icon.isEmpty()) {
                        graphics.item(icon, boxX + 3, contentY + iconOffY);
                     }
                  } else {
                     graphics.fill(boxX + 3, contentY + iconOffY, boxX + 3 + 16, contentY + iconOffY + 16, -11184811);
                  }

                  int titleX = boxX + 3 + 16 + 3;
                  int titleTextY = contentY + (row1H - 9) / 2;
                  String countStr = "";
                  if (!isFakePreview && node != null) {
                     AdvancementRequirements reqs = node.advancement().requirements();
                     int totalG = reqs.size();
                     if (totalG > 0) {
                        int doneG = 0;
                        if (progress != null) {
                           doneG = reqs.count(c -> {
                              CriterionProgress cp = progress.getCriterion(c);
                              return cp != null && cp.isDone();
                           });
                        }

                        countStr = doneG + "/" + totalG;
                     }
                  } else if (isFakePreview) {
                     countStr = "1/2";
                  }

                  int countW = font.width(countStr);
                  int countX = boxX + boxWidth - 3 - countW;
                  int maxTitleW = countX - titleX - 4;
                  String titleStr = display != null ? display.getTitle().getString() : "Advancement " + (i + 1);
                  if (!isFakePreview) {
                     int scrollOff = HudState.getScrollOffset(id + "_title", font.width(titleStr), maxTitleW);
                     graphics.enableScissor(titleX, contentY, titleX + maxTitleW, contentY + row1H);
                     graphics.text(font, titleStr, titleX - scrollOff, titleTextY, completed ? -16711936 : -1, true);
                     graphics.disableScissor();
                  } else {
                     graphics.text(font, titleStr, titleX, titleTextY, -1, true);
                  }

                  if (!countStr.isEmpty()) {
                     graphics.text(font, countStr, countX, titleTextY, completed ? -16733696 : -7829368, false);
                  }

                  contentY += row1H + 2;
                  if (mode == TrackerDisplayMode.COMPACT) {
                     renderProgressBar(graphics, node, progress, completed, boxX, contentY, boxWidth, isFakePreview);
                  } else if (mode == TrackerDisplayMode.DETAILED) {
                     renderProgressBar(graphics, node, progress, completed, boxX, contentY, boxWidth, isFakePreview);
                     if (!isFakePreview && node != null) {
                        AdvancementRequirements reqs = node.advancement().requirements();
                        if (!reqs.isEmpty()) {
                           contentY += 7;
                        }

                        if (completed) {
                           graphics.text(font, Component.translatable("gui.advancements.text.completed").getString(), boxX + 3, contentY, -16711936, true);
                        } else {
                           String descText = display != null ? display.getDescription().getString() : "";

                           for (FormattedCharSequence line : font.split(Component.literal(descText), textWidth)) {
                              graphics.text(font, line, boxX + 3, contentY, -5592406, false);
                              contentY += 9;
                           }

                           if (progress != null) {
                              boolean isOr = reqs.requirements().size() == 1 && ((List)reqs.requirements().getFirst()).size() > 1;
                              boolean hasMultiple = isOr || reqs.size() > 1;
                              if (hasMultiple) {
                                 List<String> allReqs = HudState.getAllRequirementsForCycling(node);
                                 if (!allReqs.isEmpty()) {
                                    int activeIdx = TrackingManager.getInstance().getRequirementIndex(id) % allReqs.size();
                                    int indent = 8;
                                    int reqW = textWidth - indent;

                                    for (int r = 0; r < allReqs.size(); r++) {
                                       int actualIdx = (activeIdx + r) % allReqs.size();
                                       String reqText = allReqs.get(actualIdx);
                                       CriterionProgress reqCp = progress.getCriterion(reqText);
                                       boolean reqDone = reqCp != null && reqCp.isDone();
                                       String displayReqText = reqDone ? "✔ " + reqText : reqText;
                                       boolean isActive = r == 0;
                                       int reqTextColor = reqDone ? -11141291 : (isActive ? -3355444 : -10066330);
                                       List<FormattedCharSequence> reqLines = font.split(Component.literal(displayReqText), reqW);
                                       if (isActive && reqLines.size() == 1) {
                                          String reqKey = id + "_req";
                                          int scrollOff = HudState.getScrollOffset(reqKey, font.width(displayReqText), reqW);
                                          graphics.enableScissor(boxX + 3 + indent, contentY, boxX + 3 + indent + reqW, contentY + 9);
                                          graphics.text(font, displayReqText, boxX + 3 + indent - scrollOff, contentY, reqTextColor, false);
                                          graphics.disableScissor();
                                          contentY += 9;
                                       } else {
                                          for (FormattedCharSequence line : reqLines) {
                                             graphics.text(font, line, boxX + 3 + indent, contentY, reqTextColor, false);
                                             contentY += 9;
                                          }
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     } else if (isFakePreview) {
                        contentY += 7;
                        graphics.text(font, "DESCRIPTION", boxX + 3, contentY, -5592406, false);
                        contentY += 9;
                        graphics.text(font, "  REQUIREMENT 1", boxX + 3, contentY, -3355444, false);
                        contentY += 9;
                        graphics.text(font, "  REQUIREMENT 2", boxX + 3, contentY, -11141291, false);
                     }
                  } else {
                     renderProgressBar(graphics, node, progress, completed, boxX, contentY, boxWidth, isFakePreview);
                     if (!isFakePreview && node != null) {
                        AdvancementRequirements reqsx = node.advancement().requirements();
                        if (!reqsx.isEmpty()) {
                           contentY += 7;
                        }

                        if (completed) {
                           graphics.text(font, Component.translatable("gui.advancements.text.completed").getString(), boxX + 3, contentY, -16711936, true);
                        } else {
                           String descText = display != null ? display.getDescription().getString() : "";

                           for (FormattedCharSequence line : font.split(Component.literal(descText), textWidth)) {
                              graphics.text(font, line, boxX + 3, contentY, -5592406, false);
                              contentY += 9;
                           }

                           if (progress != null) {
                              boolean isOr = reqsx.requirements().size() == 1 && ((List)reqsx.requirements().getFirst()).size() > 1;
                              boolean hasMultiple = isOr || reqsx.size() > 1;
                              if (hasMultiple) {
                                 List<String> allReqs = HudState.getAllRequirementsForCycling(node);
                                 if (!allReqs.isEmpty()) {
                                    int reqIdx = TrackingManager.getInstance().getRequirementIndex(id) % allReqs.size();
                                    String reqText = allReqs.get(reqIdx);
                                    CriterionProgress reqCp = progress.getCriterion(reqText);
                                    boolean reqDone = reqCp != null && reqCp.isDone();
                                    String displayReqText = reqDone ? "✔ " + reqText : reqText;
                                    int reqTextColor = reqDone ? -11141291 : -7829368;
                                    int indent = 8;
                                    int reqW = textWidth - indent;
                                    String reqKey = id + "_req";
                                    List<FormattedCharSequence> reqLines = font.split(Component.literal(displayReqText), reqW);
                                    if (reqLines.size() == 1) {
                                       int scrollOff = HudState.getScrollOffset(reqKey, font.width(displayReqText), reqW);
                                       graphics.enableScissor(boxX + 3 + indent, contentY, boxX + 3 + indent + reqW, contentY + 9);
                                       graphics.text(font, displayReqText, boxX + 3 + indent - scrollOff, contentY, reqTextColor, false);
                                       graphics.disableScissor();
                                    } else {
                                       for (FormattedCharSequence line : reqLines) {
                                          graphics.text(font, line, boxX + 3 + indent, contentY, reqTextColor, false);
                                          contentY += 9;
                                       }
                                    }
                                 }
                              }
                           }
                        }
                     } else if (isFakePreview) {
                        contentY += 7;
                        graphics.text(font, "DESCRIPTION", boxX + 3, contentY, -5592406, false);
                        contentY += 9;
                        graphics.text(font, "  REQUIREMENT", boxX + 3, contentY, -7829368, false);
                     }
                  }

                  if (!isFakePreview) {
                     int fadeInAlpha = HudState.getFadeInOverlayAlpha(id);
                     if (fadeInAlpha > 0) {
                        graphics.fill(boxX, y, boxX + boxWidth, y + entryHeight, fadeInAlpha << 24);
                     }

                     int flashAlpha = HudState.getCompletionFlashAlpha(id);
                     if (flashAlpha > 0) {
                        graphics.fill(boxX, y, boxX + boxWidth, y + entryHeight, flashAlpha << 24 | 0xFF00);
                     }

                     if (isFadingOut) {
                        int fadeOutAlpha = HudState.getFadeOutOverlayAlpha(id);
                        if (fadeOutAlpha >= 0) {
                           graphics.fill(boxX, y, boxX + boxWidth, y + entryHeight, fadeOutAlpha << 24);
                        }
                     }
                  }

                  y += entryHeight + 2;
               }
            }
         }
      }

      graphics.disableScissor();
   }

   private static void renderProgressBar(
      GuiGraphicsExtractor graphics,
      AdvancementNode node,
      AdvancementProgress progress,
      boolean completed,
      int boxX,
      int contentY,
      int boxWidth,
      boolean isFakePreview
   ) {
      if (!isFakePreview && node != null) {
         AdvancementRequirements requirements = node.advancement().requirements();
         int total = requirements.size();
         if (total > 0) {
            int doneG = 0;
            if (progress != null) {
               doneG = requirements.count(c -> {
                  CriterionProgress cp = progress.getCriterion(c);
                  return cp != null && cp.isDone();
               });
            }

            int barWidth = boxWidth - 6;
            int barX = boxX + 3;
            graphics.fill(barX, contentY, barX + barWidth, contentY + 4, -13421773);
            int fill = completed ? barWidth : (total > 0 ? (int)((float)doneG / total * barWidth) : 0);
            if (fill > 0) {
               graphics.fill(barX, contentY, barX + fill, contentY + 4, completed ? -16733696 : -11184811);
            }

            graphics.outline(barX, contentY, barWidth, 4, -11513776);
         }
      } else if (isFakePreview) {
         int barWidth = boxWidth - 6;
         graphics.fill(boxX + 3, contentY, boxX + 3 + barWidth, contentY + 4, -13421773);
         graphics.fill(boxX + 3, contentY, boxX + 3 + (int)(barWidth * 0.6F), contentY + 4, -16733696);
         graphics.outline(boxX + 3, contentY, barWidth, 4, -11513776);
      }
   }

   private static AdvancementNode resolveNode(Identifier id, ClientAdvancements handler, boolean isFakePreview) {
      if (!isFakePreview && handler != null) {
         AdvancementNode node = handler.getTree().get(id);
         if (node == null) {
            AdvancementTree serverTree = ModernAdvancementsClient.getServerAdvancementTree();
            if (serverTree != null) {
               node = serverTree.get(id);
            }
         }

         return node;
      } else {
         return null;
      }
   }

   private static AdvancementProgress resolveProgress(AdvancementNode node, boolean isFakePreview) {
      return !isFakePreview && node != null ? TrackingManager.getInstance().getProgress(node.holder()) : null;
   }

   public static int calculateEntryHeight(Font font, Identifier id, ClientAdvancements handler, TrackerDisplayMode mode, int boxWidth) {
      AdvancementNode node = resolveNode(id, handler, false);
      if (node != null && !node.advancement().display().isEmpty()) {
         DisplayInfo display = (DisplayInfo)node.advancement().display().get();
         AdvancementProgress progress = TrackingManager.getInstance().getProgress(node.holder());
         boolean completed = progress != null && progress.isDone();
         AdvancementRequirements requirements = node.advancement().requirements();
         int textWidth = boxWidth - 6;
         int height = 3;
         int row1Height = Math.max(16, 9);
         height += row1Height + 2;
         if (mode == TrackerDisplayMode.COMPACT) {
            if (!requirements.isEmpty()) {
               height += 7;
            }
         } else if (mode == TrackerDisplayMode.DETAILED) {
            if (!requirements.isEmpty()) {
               height += 7;
            }

            if (completed) {
               height += 9;
            } else {
               height += font.split(Component.literal(display.getDescription().getString()), textWidth).size() * 9;
               List<String> allRequirementsForCycling = HudState.getAllRequirementsForCycling(node);
               if (!allRequirementsForCycling.isEmpty()) {
                  int indent = 8;
                  int requirementWidth = textWidth - indent;
                  boolean isOr = requirements.requirements().size() == 1 && ((List)requirements.requirements().getFirst()).size() > 1;
                  if (isOr || requirements.size() > 1) {
                     for (String reqText : allRequirementsForCycling) {
                        CriterionProgress requirementProgress = progress != null ? progress.getCriterion(reqText) : null;
                        String displayRequirementText = requirementProgress != null && requirementProgress.isDone() ? "✔ " + reqText : reqText;
                        height += font.split(Component.literal(displayRequirementText), requirementWidth).size() * 9;
                     }
                  }
               }
            }
         } else {
            if (!requirements.isEmpty()) {
               height += 7;
            }

            if (completed) {
               height += 9;
            } else {
               height += font.split(Component.literal(display.getDescription().getString()), textWidth).size() * 9;
               boolean isOr = requirements.requirements().size() == 1 && ((List)requirements.requirements().getFirst()).size() > 1;
               boolean hasMultiple = isOr || requirements.size() > 1;
               if (hasMultiple) {
                  List<String> allRequirementsForCycling = HudState.getAllRequirementsForCycling(node);
                  if (!allRequirementsForCycling.isEmpty()) {
                     int requirementIndex = TrackingManager.getInstance().getRequirementIndex(id) % allRequirementsForCycling.size();
                     String requirementText = allRequirementsForCycling.get(requirementIndex);
                     CriterionProgress requirementProgress = progress != null ? progress.getCriterion(requirementText) : null;
                     String displayReqText = requirementProgress != null && requirementProgress.isDone() ? "✔ " + requirementText : requirementText;
                     int requirementWidth = textWidth - 8;
                     height += font.split(Component.literal(displayReqText), requirementWidth).size() * 9;
                  }
               }
            }
         }

         return height + 3;
      } else {
         return 0;
      }
   }

   public static int estimateFakeEntryHeight(Font font, TrackerDisplayMode mode) {
      int height = 3;
      height += Math.max(16, 9) + 2;
      if (mode == TrackerDisplayMode.COMPACT) {
         height += 7;
      } else if (mode == TrackerDisplayMode.DETAILED) {
         height += 7;
         height += 9;
         height += 9 * 2;
      } else {
         height += 7;
         height += 9;
         height += 9;
      }

      return height + 3;
   }

   public static int[] computeBoxOrigin(int screenW, int screenH, int boxH, int boxWidth, HudAnchor anchor, int offsetX, int offsetY) {
      int boxX = anchor.getRefX(screenW) - anchor.getPivotX(boxWidth) + offsetX;
      int boxY = anchor.getRefY(screenH) - anchor.getPivotY(boxH) + offsetY;
      boxX = Math.clamp((long)boxX, 0, Math.max(0, screenW - boxWidth));
      boxY = Math.clamp((long)boxY, 0, Math.max(0, screenH - boxH));
      return new int[]{boxX, boxY};
   }
}
