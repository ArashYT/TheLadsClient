package com.thelads.core.v1_20_1.hud.chat;

import com.mojang.text2speech.Narrator;
import com.thelads.core.v1_20_1.hud.Hud1201;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;

/**
 * DisableNarrator on 1.20.1: the platform speech library is only loaded when narration is allowed (NarratorMixin swaps it in for
 * GameNarrator's own); it never changes vanilla preferences.
 */
public final class NativeNarrator1201 {
    private static LazyNarrator current;
    private static boolean wasDisabled;

    private NativeNarrator1201() {}

    /** GameNarrator's speech transport. */
    public static Narrator create() {
        current = new LazyNarrator(() -> Hud1201.enabled("DisableNarrator"), Narrator::getNarrator);
        return current;
    }

    public static void tick() {
        if (current != null) current.synchronizePolicy();
        boolean disabled = Hud1201.enabled("DisableNarrator");
        if (disabled && !wasDisabled) Minecraft.getInstance().getNarrator().clear();
        wasDisabled = disabled;
    }

    static final class LazyNarrator implements Narrator {
        private final BooleanSupplier disabled;
        private final Supplier<Narrator> factory;
        private Narrator delegate;
        private boolean silenced;
        private boolean destroyed;

        LazyNarrator(BooleanSupplier disabled, Supplier<Narrator> factory) {
            this.disabled = disabled;
            this.factory = factory;
        }

        synchronized void synchronizePolicy() {
            boolean blocked = disabled.getAsBoolean();
            if (blocked && !silenced && delegate != null && !destroyed) delegate.clear();
            silenced = blocked;
        }

        private Narrator permitted() {
            synchronizePolicy();
            if (destroyed || silenced) return null;
            if (delegate == null) delegate = factory.get();
            return delegate;
        }

        @Override public synchronized void say(String text, boolean interrupt) {
            Narrator voice = permitted();
            if (voice != null) voice.say(text, interrupt);
        }

        @Override public synchronized boolean active() {
            Narrator voice = permitted();
            return voice != null && voice.active();
        }

        @Override public synchronized void clear() {
            if (!destroyed && delegate != null) delegate.clear();
        }

        @Override public synchronized void destroy() {
            if (!destroyed && delegate != null) delegate.destroy();
            destroyed = true;
        }
    }
}
