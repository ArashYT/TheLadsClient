package com.thelads.core.client.hypixel.quickplay;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.config.Option;
import com.thelads.core.config.TextOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hypixel Quickplay module: opens game picker GUI, manages favorites, and provides "Play Again" / requeue keybind.
 */
public final class QuickplayModule {
    public static final String NAME = "Quickplay";
    public static final String FAVORITES = "Favorites";
    public static final int MAX_FAVORITES = 9;

    private static String lastPlayCommand = "";

    private QuickplayModule() {}

    public static Module module() {
        Module m = new Module(NAME, "Hypixel only. Game picker for quickly joining Hypixel minigames and lobbies via /play commands. Includes favorites and requeue keybind.");
        m.addOption(new TextOption(FAVORITES, "bedwars_eight_one,bedwars_eight_two,solo_normal,duels_classic_duel"));
        m.setEnabled(true);
        m.setCategory(Module.Category.HYPIXEL);
        return m;
    }

    public static Module getModule() {
        return ModuleManager.getInstance().getModule(NAME);
    }

    public static boolean isEnabled() {
        Module m = getModule();
        return m != null && m.isEnabled();
    }

    public static String getLastPlayCommand() {
        return lastPlayCommand;
    }

    public static void setLastPlayCommand(String command) {
        if (command != null && !command.trim().isEmpty()) {
            lastPlayCommand = command.trim();
        }
    }

    public static List<String> getFavorites() {
        Module m = getModule();
        if (m == null) return Collections.emptyList();
        Option o = m.getOption(FAVORITES);
        if (!(o instanceof TextOption t)) return Collections.emptyList();
        String val = t.getValue();
        if (val == null || val.trim().isEmpty()) return Collections.emptyList();
        String[] split = val.split(",");
        List<String> list = new ArrayList<>();
        for (String s : split) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty() && !list.contains(trimmed)) {
                list.add(trimmed);
                if (list.size() >= MAX_FAVORITES) break;
            }
        }
        return Collections.unmodifiableList(list);
    }

    public static void setFavorites(List<String> favs) {
        Module m = getModule();
        if (m == null) return;
        Option o = m.getOption(FAVORITES);
        if (o instanceof TextOption t) {
            List<String> limited = new ArrayList<>();
            for (String f : favs) {
                if (f != null && !f.trim().isEmpty() && !limited.contains(f.trim())) {
                    limited.add(f.trim());
                    if (limited.size() >= MAX_FAVORITES) break;
                }
            }
            t.setValue(String.join(",", limited));
            com.thelads.core.config.ConfigManager.saveLater();
        }
    }

    public static boolean isFavorite(String modeId) {
        if (modeId == null) return false;
        return getFavorites().contains(modeId);
    }

    public static void toggleFavorite(String modeId) {
        if (modeId == null) return;
        List<String> current = new ArrayList<>(getFavorites());
        if (current.contains(modeId)) {
            current.remove(modeId);
        } else {
            if (current.size() < MAX_FAVORITES) {
                current.add(modeId);
            } else {
                LadsGameBridge.get().showToast("Quickplay", "Maximum " + MAX_FAVORITES + " favorites allowed!");
                return;
            }
        }
        setFavorites(current);
    }

    public static void executeCommand(String command, Runnable onExecuted) {
        if (!HypixelSafetyManager.isHypixel()) {
            LadsGameBridge.get().showToast("Quickplay", "Hypixel only: not connected to Hypixel");
            return;
        }
        if (command == null || command.trim().isEmpty()) return;

        QuickplayRateLimiter.getInstance().tryExecute(
            () -> {
                setLastPlayCommand(command);
                LadsGameBridge.get().sendChatLine(command);
                if (onExecuted != null) onExecuted.run();
            },
            () -> {
                long remainingSec = (QuickplayRateLimiter.getInstance().getRemainingCooldownMs() + 999) / 1000;
                LadsGameBridge.get().showToast("Quickplay", "Please wait " + remainingSec + "s before queuing again!");
            }
        );
    }

    public static void triggerPlayAgain() {
        if (!HypixelSafetyManager.isHypixel()) {
            LadsGameBridge.get().showToast("Quickplay", "Hypixel only: not connected to Hypixel");
            return;
        }
        if (lastPlayCommand == null || lastPlayCommand.isEmpty()) {
            LadsGameBridge.get().showToast("Quickplay", "No previous /play command to replay!");
            return;
        }
        executeCommand(lastPlayCommand, null);
    }
}
