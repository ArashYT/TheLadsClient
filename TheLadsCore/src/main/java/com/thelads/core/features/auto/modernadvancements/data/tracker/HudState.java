package com.thelads.core.features.auto.modernadvancements.data.tracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementRequirements;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.UnmodifiableView;

public class HudState {
   private static final long INITIAL_PAUSE_MS = 1000L;
   private static final float SCROLL_PX_PER_MS = 0.035F;
   private static final long END_PAUSE_MS = 1500L;
   private static final int PHASE_INITIAL = 0;
   private static final int PHASE_SCROLLING = 1;
   private static final int PHASE_END = 2;
   private static final long FADE_DURATION_MS = 400L;
   private static final long COMPLETION_FLASH_MS = 1200L;
   private static final Map<String, long[]> scrollStates = new HashMap<>();
   private static final Map<Identifier, Long> fadeInTimes = new HashMap<>();
   private static final Map<Identifier, Long> fadeOutTimes = new HashMap<>();
   private static final Map<Identifier, Long> completionTimes = new HashMap<>();

   public static int getScrollOffset(String key, int textWidth, int containerWidth) {
      if (textWidth <= containerWidth) {
         scrollStates.remove(key);
         return 0;
      } else {
         long[] state = scrollStates.computeIfAbsent(key, k -> new long[]{System.currentTimeMillis(), 0L});
         long elapsed = System.currentTimeMillis() - state[0];
         int maxScroll = textWidth - containerWidth;

         return switch ((int)state[1]) {
            case 0 -> {
               if (elapsed >= 1000L) {
                  state[0] = System.currentTimeMillis();
                  state[1] = 1L;
               }

               yield 0;
            }
            case 1 -> {
               int off = (int)((float)elapsed * 0.035F);
               if (off >= maxScroll) {
                  state[0] = System.currentTimeMillis();
                  state[1] = 2L;
                  yield maxScroll;
               } else {
                  yield off;
               }
            }
            case 2 -> {
               if (elapsed >= 1500L) {
                  state[0] = System.currentTimeMillis();
                  state[1] = 0L;
                  yield 0;
               } else {
                  yield maxScroll;
               }
            }
            default -> 0;
         };
      }
   }

   public static void resetScroll(String key) {
      scrollStates.remove(key);
   }

   public static void resetAllScroll() {
      scrollStates.clear();
   }

   public static void trackEntryAdded(Identifier id) {
      fadeInTimes.put(id, System.currentTimeMillis());
      fadeOutTimes.remove(id);
   }

   public static void trackEntryRemoved(Identifier id) {
      if (!fadeOutTimes.containsKey(id)) {
         fadeOutTimes.put(id, System.currentTimeMillis());
      }

      fadeInTimes.remove(id);
   }

   public static void trackEntryCompleted(Identifier id) {
      completionTimes.put(id, System.currentTimeMillis());
   }

   public static int getFadeInOverlayAlpha(Identifier id) {
      Long t = fadeInTimes.get(id);
      if (t == null) {
         return 0;
      } else {
         long elapsed = System.currentTimeMillis() - t;
         if (elapsed >= 400L) {
            fadeInTimes.remove(id);
            return 0;
         } else {
            return (int)(255.0 * (1.0 - elapsed / 400.0));
         }
      }
   }

   public static int getFadeOutOverlayAlpha(Identifier id) {
      Long t = fadeOutTimes.get(id);
      if (t == null) {
         return -1;
      } else {
         long elapsed = System.currentTimeMillis() - t;
         if (elapsed >= 400L) {
            fadeOutTimes.remove(id);
            return -1;
         } else {
            return (int)(255.0 * (elapsed / 400.0));
         }
      }
   }

   public static int getCompletionFlashAlpha(Identifier id) {
      Long t = completionTimes.get(id);
      if (t == null) {
         return 0;
      } else {
         long elapsed = System.currentTimeMillis() - t;
         if (elapsed >= 1200L) {
            completionTimes.remove(id);
            return 0;
         } else {
            int pulse = (int)(Math.sin(elapsed / 1200.0 * Math.PI * 2.5) * 96.0);
            return Math.max(0, pulse);
         }
      }
   }

   @UnmodifiableView
   public static Set<Identifier> getFadingOutEntries() {
      return Collections.unmodifiableSet(fadeOutTimes.keySet());
   }

   public static List<String> getAllRequirementsForCycling(AdvancementNode node) {
      AdvancementRequirements reqs = node.advancement().requirements();
      boolean isOr = reqs.requirements().size() == 1 && ((List)reqs.requirements().getFirst()).size() > 1;
      return (List<String>)(isOr ? (List)reqs.requirements().getFirst() : new ArrayList<>(reqs.names()));
   }

   public static void clearForSession() {
      scrollStates.clear();
      fadeInTimes.clear();
      fadeOutTimes.clear();
      completionTimes.clear();
   }
}
