package com.thelads.core.client;

import com.thelads.core.LadsVersion;
import com.thelads.core.client.ladsapi.LadsApiClient;
import com.thelads.core.shared.ServerNames;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Formats and caches the OS window title across all Minecraft versions:
 * {@code The Lads Client - <MC version> - <client version> - <activity>}.
 */
public final class WindowTitle {
    public enum Menu {
        NONE,
        TITLE,
        WORLD_SELECT,
        SERVER_LIST,
        OTHER
    }

    private WindowTitle() {}

    public static String activity(boolean inWorld, boolean localWorld, String serverAddress, String ladsApiServerName, Menu menu) {
        if (inWorld) {
            if (localWorld) return "Singleplayer";
            if (LadsNetwork.isAddress(serverAddress)) {
                String named = ladsApiServerName == null ? "" : ladsApiServerName.trim();
                return named.isEmpty() ? LadsNetwork.NAME : named;
            }
            String known = ServerNames.resolveKnown(serverAddress);
            return known != null ? known : "Multiplayer";
        }
        if (menu == Menu.TITLE) return "Title Screen";
        if (menu == Menu.WORLD_SELECT) return "Singleplayer";
        if (menu == Menu.SERVER_LIST) return "Multiplayer";
        return "Menus";
    }

    public static String format(String mcVersion, String clientVersion, String activity) {
        return "The Lads Client - " + mcVersion + " - " + clientVersion + " - " + activity;
    }

    public static String compute(String mcVersion, boolean inWorld, boolean localWorld, String serverAddress, Menu menu) {
        return format(
            mcVersion,
            LadsVersion.VERSION,
            activity(inWorld, localWorld, serverAddress, LadsApiClient.serverName(), menu)
        );
    }

    /** Applies the OS window title only when the computed string actually changes. */
    public static final class Updater {
        private final String mcVersion;
        private final Consumer<String> sink;
        private String lastTitle;

        public Updater(String mcVersion, Consumer<String> sink) {
            this.mcVersion = Objects.requireNonNull(mcVersion, "mcVersion");
            this.sink = Objects.requireNonNull(sink, "sink");
        }

        public boolean update(boolean inWorld, boolean localWorld, String serverAddress, Menu menu) {
            return apply(compute(mcVersion, inWorld, localWorld, serverAddress, menu));
        }

        public boolean apply(String next) {
            if (next == null || next.equals(lastTitle)) return false;
            lastTitle = next;
            sink.accept(next);
            return true;
        }

        public String lastTitle() {
            return lastTitle;
        }
    }
}
