package com.thelads.core.client.hypixel;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import java.util.Locale;

/**
 * Clean-room client-side visual terminal solvers for Hypixel SkyBlock Dungeons (F7/M7):
 * 1. Click in order (Numbers 1-14)
 * 2. Select all matching color items ("Select all the Red items!")
 * 3. What starts with letter ("What starts with: 'B'?")
 * 4. Correct all the panes
 *
 * Visual only: highlights the slots in the container screen with zero automated packet clicks.
 * Gated by HypixelSafetyManager under module "SkyBlock Suite".
 */
public final class TerminalSolver {
    public enum TerminalType {
        NONE,
        CLICK_IN_ORDER,
        SELECT_COLOR,
        STARTS_WITH_LETTER,
        CORRECT_PANES
    }

    public static final int COLOR_TARGET = 0x7000FF00;      // Bright translucent green
    public static final int COLOR_NEXT = 0x50FFAA00;        // Soft translucent gold
    public static final int COLOR_PANES = 0x7000FF00;

    private static final String[] COLOR_NAMES = {
        "light blue", "light gray", "magenta", "yellow", "orange", "purple", "silver",
        "green", "white", "black", "brown", "pink", "cyan",
        "lime", "gray", "blue", "red"
    };

    static {
        // Ensure COLOR_NAMES is sorted by length descending so multi-word colors (e.g. "light blue") are checked before shorter substrings ("blue")
        java.util.Arrays.sort(COLOR_NAMES, (a, b) -> Integer.compare(b.length(), a.length()));
    }

    /**
     * Information about a slot in a container screen.
     */
    public record SlotInfo(int slotIndex, int stackSize, String itemName) {
        public SlotInfo(int slotIndex, int stackSize, String itemName) {
            this.slotIndex = slotIndex;
            this.stackSize = stackSize;
            this.itemName = itemName != null ? itemName : "";
        }
    }

    private TerminalSolver() {}

    public static boolean isActive() {
        Module m = ModuleManager.getInstance().getModule("SkyBlock Suite");
        if (m == null || !m.isEnabled()) return false;
        Option opt = m.getOption("Terminal Solvers");
        if (opt instanceof BoolOption b && !b.get()) return false;
        if (HypixelSafetyManager.isDisallowed("SkyBlock Suite")) return false;
        return true;
    }

    public static TerminalType detectType(String title) {
        if (title == null || title.isEmpty()) return TerminalType.NONE;
        String lower = title.toLowerCase(Locale.ROOT);
        if (lower.contains("click in order") || lower.contains("numbers")) {
            return TerminalType.CLICK_IN_ORDER;
        }
        if (lower.contains("select all the")) {
            return TerminalType.SELECT_COLOR;
        }
        if (lower.contains("what starts with") || lower.contains("starts with:")) {
            // Can be letter ("What starts with: 'B'?") or color ("What starts with: 'Red'?")
            String target = extractQuoted(title);
            if (target != null && target.length() > 1) {
                return TerminalType.SELECT_COLOR;
            }
            return TerminalType.STARTS_WITH_LETTER;
        }
        if (lower.contains("correct all the panes")) {
            return TerminalType.CORRECT_PANES;
        }
        return TerminalType.NONE;
    }

    public static String extractTargetColor(String title) {
        if (title == null) return null;
        String lower = title.toLowerCase(Locale.ROOT);
        for (String c : COLOR_NAMES) {
            if (lower.contains(c)) return c;
        }
        return extractQuoted(lower);
    }

    public static char extractTargetLetter(String title) {
        String quoted = extractQuoted(title);
        if (quoted != null && !quoted.isEmpty()) {
            return Character.toUpperCase(quoted.charAt(0));
        }
        if (title != null) {
            int idx = title.lastIndexOf(':');
            if (idx >= 0 && idx + 1 < title.length()) {
                String sub = title.substring(idx + 1).trim();
                if (!sub.isEmpty()) {
                    char ch = sub.charAt(0);
                    if (Character.isLetter(ch)) return Character.toUpperCase(ch);
                }
            }
        }
        return '\0';
    }

    private static String extractQuoted(String str) {
        if (str == null) return null;
        int first = str.indexOf('\'');
        if (first >= 0) {
            int second = str.indexOf('\'', first + 1);
            if (second > first) return str.substring(first + 1, second).trim();
        }
        first = str.indexOf('"');
        if (first >= 0) {
            int second = str.indexOf('"', first + 1);
            if (second > first) return str.substring(first + 1, second).trim();
        }
        return null;
    }

    /**
     * Determines whether an item slot in the terminal container is already completed.
     * In Hypixel, completed terminal slots turn into green/lime panes or lime dye, or empty.
     */
    public static boolean isSlotCompleted(String itemName, int stackSize) {
        return isSlotCompleted(itemName, stackSize, false);
    }

    /**
     * Determines whether an item slot in the terminal container is already completed.
     * Only treats items as completed if they are green/lime glass panes or completed indicators,
     * and NOT when lime/green is part of the active selection goal.
     */
    public static boolean isSlotCompleted(String itemName, int stackSize, boolean greenOrLimeGoal) {
        if (itemName == null || itemName.isEmpty() || stackSize <= 0) return true;
        if (greenOrLimeGoal) return false;
        String clean = stripFormatting(itemName).toLowerCase(Locale.ROOT);
        if (clean.contains("already activated") || clean.contains("completed")) return true;
        boolean isGreenOrLime = clean.contains("lime") || clean.contains("green");
        boolean isPane = clean.contains("pane") || clean.contains("glass");
        return isGreenOrLime && isPane;
    }

    /**
     * Checks if an item matches the target color using exact colour-name match.
     * Prevents false collisions where "Blue" matches "Light Blue" or "Gray" matches "Light Gray".
     */
    public static boolean matchesColor(String itemName, String targetColor) {
        if (itemName == null || targetColor == null) return false;
        String clean = stripFormatting(itemName).toLowerCase(Locale.ROOT);
        String target = targetColor.toLowerCase(Locale.ROOT);

        // Exact colour-name match ("Blue" must not match "Light Blue")
        if (target.equals("blue") && clean.contains("light blue")) return false;
        if (target.equals("gray") && clean.contains("light gray")) return false;
        if (target.equals("silver") && clean.contains("light gray")) return true;
        if (target.equals("light gray") && clean.contains("silver")) return true;

        return java.util.regex.Pattern.compile("\\b" + java.util.regex.Pattern.quote(target) + "\\b").matcher(clean).find();
    }

    /**
     * Finds the current target number (the lowest uncompleted number from 1 to 14)
     * by inspecting container slots.
     */
    public static int findClickInOrderTarget(java.util.Collection<SlotInfo> slots, int totalSlots) {
        if (slots == null || slots.isEmpty()) return -1;
        int chestSlotLimit = totalSlots > 36 ? totalSlots - 36 : totalSlots;
        int min = 15;
        for (SlotInfo info : slots) {
            if (info == null || info.stackSize() <= 0) continue;
            if (info.slotIndex() >= 0 && (chestSlotLimit <= 0 || info.slotIndex() < chestSlotLimit)) {
                if (info.stackSize() >= 1 && info.stackSize() <= 14 && !isSlotCompleted(info.itemName(), info.stackSize(), false)) {
                    if (info.stackSize() < min) {
                        min = info.stackSize();
                    }
                }
            }
        }
        return min <= 14 ? min : -1;
    }

    /**
     * Finds the current target number (the lowest uncompleted number from 1 to 14)
     * by querying a slot supplier across container slots.
     */
    public static int findClickInOrderTarget(int totalSlots, java.util.function.IntFunction<SlotInfo> slotSupplier) {
        if (slotSupplier == null || totalSlots <= 0) return -1;
        int chestSlotLimit = totalSlots > 36 ? totalSlots - 36 : totalSlots;
        int min = 15;
        for (int i = 0; i < chestSlotLimit; i++) {
            SlotInfo info = slotSupplier.apply(i);
            if (info == null || info.stackSize() <= 0) continue;
            if (info.stackSize() >= 1 && info.stackSize() <= 14 && !isSlotCompleted(info.itemName(), info.stackSize(), false)) {
                if (info.stackSize() < min) {
                    min = info.stackSize();
                }
            }
        }
        return min <= 14 ? min : -1;
    }

    /**
     * Evaluates whether a given container slot should be visually highlighted.
     * Returns the ARGB color to overlay, or 0 for none.
     */
    public static int getSlotHighlight(String containerTitle, int slotIndex, int stackSize, String itemName, int totalSlots) {
        return getSlotHighlight(containerTitle, slotIndex, stackSize, itemName, totalSlots, -1);
    }

    /**
     * Evaluates whether a given container slot should be visually highlighted with an explicit click order target.
     * Returns the ARGB color to overlay, or 0 for none.
     */
    public static int getSlotHighlight(String containerTitle, int slotIndex, int stackSize, String itemName, int totalSlots, int clickInOrderTarget) {
        if (!isActive() || containerTitle == null) return 0;
        // Terminals are contained in upper chest slots; player inventory is always the bottom 36 slots
        int chestSlotLimit = totalSlots > 36 ? totalSlots - 36 : totalSlots;
        if (slotIndex < 0 || slotIndex >= chestSlotLimit) return 0;
        if (stackSize <= 0 || itemName == null || itemName.isEmpty()) return 0;

        TerminalType type = detectType(containerTitle);
        switch (type) {
            case CLICK_IN_ORDER -> {
                // In click order, numbers 1-14 must be clicked in ascending sequence.
                // Target slot has stackSize matching lowest active number.
                if (isSlotCompleted(itemName, stackSize, false)) return 0;
                int target = clickInOrderTarget > 0 ? clickInOrderTarget : 1;
                if (stackSize == target) {
                    return COLOR_TARGET;
                }
            }
            case SELECT_COLOR -> {
                String targetColor = extractTargetColor(containerTitle);
                boolean greenGoal = targetColor != null && (targetColor.contains("lime") || targetColor.contains("green"));
                if (isSlotCompleted(itemName, stackSize, greenGoal)) return 0;
                if (targetColor != null && matchesColor(itemName, targetColor)) {
                    return COLOR_TARGET;
                }
            }
            case STARTS_WITH_LETTER -> {
                char target = extractTargetLetter(containerTitle);
                boolean greenGoal = target == 'L' || target == 'G';
                if (isSlotCompleted(itemName, stackSize, greenGoal)) return 0;
                if (target != '\0') {
                    String clean = stripFormatting(itemName).trim();
                    if (!clean.isEmpty() && Character.toUpperCase(clean.charAt(0)) == target) {
                        return COLOR_TARGET;
                    }
                }
            }
            case CORRECT_PANES -> {
                String clean = stripFormatting(itemName).toLowerCase(Locale.ROOT);
                if (clean.contains("red") && clean.contains("pane")) {
                    return COLOR_PANES;
                }
            }
            default -> {}
        }
        return 0;
    }

    public static String stripFormatting(String text) {
        if (text == null) return "";
        return text.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }
}
