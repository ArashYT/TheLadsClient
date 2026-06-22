package com.thelads.core.features.auto.modernadvancements.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import java.util.List;
import com.thelads.core.features.auto.modernadvancements.ModernAdvancementsClient;
import com.thelads.core.features.auto.modernadvancements.client.screen.HudEditScreen;
import com.thelads.core.features.auto.modernadvancements.client.screen.ToastEditScreen;
import com.thelads.core.features.auto.modernadvancements.data.tracker.HudState;
import com.thelads.core.features.auto.modernadvancements.data.tracker.TrackingManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.KeyMapping.Category;
import net.minecraft.resources.Identifier;

public class ModernAdvancementKeybinds {
   private static final Category KEYBIND_CATEGORY = new Category(Identifier.fromNamespaceAndPath("modern-advancements", "hud_keybinds"));
   public static final KeyMapping TOGGLE_HUD = KeyMappingHelper.registerKeyMapping(
      new KeyMapping("key.modern-advancements.toggle_hud", Type.KEYSYM, 92, KEYBIND_CATEGORY, 0)
   );
   public static final KeyMapping SELECTION_CYCLE_FORWARD = KeyMappingHelper.registerKeyMapping(
      new KeyMapping("key.modern-advancements.selection_cycle_forward", Type.KEYSYM, 91, KEYBIND_CATEGORY, 1)
   );
   public static final KeyMapping SELECTION_CYCLE_BACK = KeyMappingHelper.registerKeyMapping(
      new KeyMapping("key.modern-advancements.selection_cycle_back", Type.KEYSYM, InputConstants.UNKNOWN.getValue(), KEYBIND_CATEGORY, 2)
   );
   public static final KeyMapping REQUIREMENT_CYCLE_FORWARD = KeyMappingHelper.registerKeyMapping(
      new KeyMapping("key.modern-advancements.requirement_cycle_forward", Type.KEYSYM, 93, KEYBIND_CATEGORY, 3)
   );
   public static final KeyMapping REQUIREMENT_CYCLE_BACK = KeyMappingHelper.registerKeyMapping(
      new KeyMapping("key.modern-advancements.requirement_cycle_back", Type.KEYSYM, InputConstants.UNKNOWN.getValue(), KEYBIND_CATEGORY, 4)
   );
   public static final KeyMapping TRACKING_HUD_SETTINGS = KeyMappingHelper.registerKeyMapping(
      new KeyMapping("key.modern-advancements.tracking_hud_settings", Type.KEYSYM, InputConstants.UNKNOWN.getValue(), KEYBIND_CATEGORY, 5)
   );
   public static final KeyMapping TOAST_HUD_SETTINGS = KeyMappingHelper.registerKeyMapping(
      new KeyMapping("key.modern-advancements.toast_hud_settings", Type.KEYSYM, InputConstants.UNKNOWN.getValue(), KEYBIND_CATEGORY, 6)
   );

   public static void create() {
      ClientTickEvents.END_CLIENT_TICK.register((EndTick)client -> {
         if (client.gui.screen() == null) {
            while (TRACKING_HUD_SETTINGS.consumeClick()) {
               client.setScreenAndShow(new HudEditScreen(client.gui.screen()));
            }

            while (TOAST_HUD_SETTINGS.consumeClick()) {
               client.setScreenAndShow(new ToastEditScreen(client.gui.screen()));
            }

            while (TOGGLE_HUD.consumeClick()) {
               ModernAdvancementsClient.CONFIG.hideTracker(!ModernAdvancementsClient.CONFIG.hideTracker());
               ModernAdvancementsClient.CONFIG.save();
            }

            if (!ModernAdvancementsClient.CONFIG.hideTracker()) {
               TrackingManager tm = TrackingManager.getInstance();

               while (SELECTION_CYCLE_FORWARD.consumeClick()) {
                  tm.rotateHighlightForward();
               }

               while (SELECTION_CYCLE_BACK.consumeClick()) {
                  tm.rotateHighlightBackward();
               }

               while (REQUIREMENT_CYCLE_FORWARD.consumeClick()) {
                  List<Identifier> tracked = tm.getTracked();
                  if (!tracked.isEmpty()) {
                     Identifier top = tracked.getFirst();
                     AdvancementNode node = null;
                     if (client.getConnection() != null) {
                        node = client.getConnection().getAdvancements().getTree().get(top);
                     }

                     if (node == null && ModernAdvancementsClient.serverAdvancementTree != null) {
                        node = ModernAdvancementsClient.serverAdvancementTree.get(top);
                     }

                     if (node != null) {
                        AdvancementProgress progress = tm.getProgress(node.holder());
                        if (progress != null) {
                           List<String> allReqs = HudState.getAllRequirementsForCycling(node);
                           tm.cycleRequirementForward(top, allReqs.size());
                        }
                     }
                  }
               }

               while (REQUIREMENT_CYCLE_BACK.consumeClick()) {
                  List<Identifier> tracked = tm.getTracked();
                  if (!tracked.isEmpty()) {
                     Identifier topx = tracked.getFirst();
                     AdvancementNode nodex = null;
                     if (client.getConnection() != null) {
                        nodex = client.getConnection().getAdvancements().getTree().get(topx);
                     }

                     if (nodex == null && ModernAdvancementsClient.serverAdvancementTree != null) {
                        nodex = ModernAdvancementsClient.serverAdvancementTree.get(topx);
                     }

                     if (nodex != null) {
                        AdvancementProgress progress = tm.getProgress(nodex.holder());
                        if (progress != null) {
                           List<String> allReqs = HudState.getAllRequirementsForCycling(nodex);
                           tm.cycleRequirementBackward(topx, allReqs.size());
                        }
                     }
                  }
               }
            }
         }
      });
   }
}
