package com.thelads.core.client.hypixel.quickplay;

import java.util.Objects;

public record QuickplayMode(
    String id,
    String name,
    String command,
    QuickplayCategory category,
    String iconKey
) {
    public QuickplayMode {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(iconKey, "iconKey");
    }
}
