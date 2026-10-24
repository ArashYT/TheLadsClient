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
        "red", "green", "blue", "yellow", "orange", "purple", "pink", "cyan",
        "lime", "gray", "white", "black", "magenta", "brown", "light blue", "silver"
    };

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
        if (itemName == null || itemName.isEmpty() || stackSize <= 0) return true;
        String lower = itemName.toLowerCase(Locale.ROOT);
        return lower.contains("lime") || lower.contains("green stained glass");
    }

    /**
     * Evaluates whether a given container slot should be visually highlighted.
     * Returns the ARGB color to overlay, or 0 for none.
     */
    public static int getSlotHighlight(String containerTitle, int slotIndex, int stackSize, String itemName, int totalSlots) {
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
                if (isSlotCompleted(itemName, stackSize)) return 0;
                // Minimum valid terminal number is 1
                if (stackSize >= 1 && stackSize <= 14) {
                    return COLOR_TARGET;
                }
            }
            case SELECT_COLOR -> {
                if (isSlotCompleted(itemName, stackSize)) return 0;
                String targetColor = extractTargetColor(containerTitle);
                if (targetColor != null) {
                    String clean = stripFormatting(itemName).toLowerCase(Locale.ROOT);
                    if (clean.contains(targetColor)) {
                        return COLOR_TARGET;
                    }
                }
            }
            case STARTS_WITH_LETTER -> {
                if (isSlotCompleted(itemName, stackSize)) return 0;
                char target = extractTargetLetter(containerTitle);
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
