package com.thelads.core.client.hud;

import java.util.Locale;
import java.util.Objects;

/**
 * An interactive action button on a server notification toast.
 * Can execute a client command, suggest a command in chat, open a safe external URL, or dismiss the toast.
 */
public final class ToastAction {
    public enum Type {
        RUN_COMMAND,
        SUGGEST_COMMAND,
        OPEN_URL,
        DISMISS
    }

    private final String label;
    private final Type type;
    private final String payload;

    public ToastAction(String label, Type type, String payload) {
        this.label = label != null ? label.trim() : "";
        this.type = type != null ? type : Type.DISMISS;
        this.payload = payload != null ? payload.trim() : "";
    }

    public static ToastAction runCommand(String label, String command) {
        return new ToastAction(label, Type.RUN_COMMAND, command);
    }

    public static ToastAction suggestCommand(String label, String command) {
        return new ToastAction(label, Type.SUGGEST_COMMAND, command);
    }

    public static ToastAction openUrl(String label, String url) {
        return new ToastAction(label, Type.OPEN_URL, url);
    }

    public static ToastAction dismiss(String label) {
        return new ToastAction(label, Type.DISMISS, "");
    }

    public static ToastAction parse(String label, String typeStr, String payload) {
        Type t = Type.DISMISS;
        if (typeStr != null) {
            String lower = typeStr.trim().toLowerCase(Locale.ROOT);
            if (lower.contains("url") || lower.contains("link")) {
                t = Type.OPEN_URL;
            } else if (lower.contains("suggest")) {
                t = Type.SUGGEST_COMMAND;
            } else if (lower.contains("command") || lower.contains("run")) {
                t = Type.RUN_COMMAND;
            } else if (lower.contains("dismiss") || lower.contains("close")) {
                t = Type.DISMISS;
            }
        }
        return new ToastAction(label, t, payload);
    }

    public String getLabel() {
        return label;
    }

    public Type getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ToastAction that)) return false;
        return Objects.equals(label, that.label) && type == that.type && Objects.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, type, payload);
    }

    @Override
    public String toString() {
        return "ToastAction[label=" + label + ", type=" + type + ", payload=" + payload + "]";
    }
}
