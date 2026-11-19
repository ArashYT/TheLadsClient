package com.thelads.ladsapi.api;

/** The input device a Lads Client player uses (PROTOCOL.md section 9), and the listener types for what clients send back. */
public final class LadsInput {
    private LadsInput() {}

    public enum Device { UNKNOWN, KBM, GAMEPAD }

    public enum Pad { UNKNOWN, XBOX, PLAYSTATION, SWITCH, GENERIC }

    /** Called when the client reports its device (after every hello, and when it changes). */
    @FunctionalInterface
    public interface Listener { void onInput(LadsPlayer player, Device device, Pad pad); }

    /** Called when a player presses or releases a server action (PROTOCOL.md section 20). */
    @FunctionalInterface
    public interface ActionListener { void onAction(LadsPlayer player, String id, boolean down); }

    /** Called with a dialog's answer: a button id, "dismiss", or "timeout" (made by the plugin when nobody answered). */
    @FunctionalInterface
    public interface DialogListener { void onDialog(LadsPlayer player, String dialogId, String choice); }
}
