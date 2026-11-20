package com.thelads.core.v1_8_9.ladsapi.ui;

import com.thelads.core.client.ladsapi.minigame.Hooks;

/** Input cap: 1.8.9 only has keyboard and mouse. The device reads as UNKNOWN until real input was seen, then KBM. */
final class InputDevice189 implements Hooks.InputSource {
    private static volatile boolean seen;

    static void stamp() {
        seen = true;
    }

    @Override
    public Hooks.Device device() {
        return seen ? Hooks.Device.KBM : Hooks.Device.UNKNOWN;
    }

    @Override
    public Hooks.Pad pad() {
        return Hooks.Pad.GENERIC;
    }
}
