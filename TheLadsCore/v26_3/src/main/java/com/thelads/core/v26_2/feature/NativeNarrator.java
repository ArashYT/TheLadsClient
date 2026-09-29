package com.thelads.core.v26_2.feature;

import com.mojang.text2speech.Narrator;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import org.slf4j.LoggerFactory;

/** Defers the platform speech library until narration is allowed; never changes vanilla preferences. */
public final class NativeNarrator {
    private static LazyNarrator current;
    private static boolean probed;
    private NativeNarrator() {}

    public static Narrator create() {
        current = new LazyNarrator(() -> NativeQualityOfLife.enabled("DisableNarrator"), Narrator::getNarrator);
        return current;
    }

    public static void tick() {
        if (current != null) current.synchronizePolicy();
        if (!probed && Boolean.getBoolean("thelads.verifyIntegrations")
            && Minecraft.getInstance().gui.screen() instanceof TitleScreen) {
            probed = true;
            probe();
        }
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

        @Override public synchronized void say(String text, boolean interrupt, float volume) {
            Narrator voice = permitted();
            if (voice != null) voice.say(text, interrupt, volume);
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

    /** Fake speech transport: tests lifecycle without speaking or loading a platform library. */
    private static void probe() {
        int[] passed = {0};
        try {
            boolean[] blocked = {true}; int[] calls = new int[4];
            var fake = new Narrator() {
                public void say(String text, boolean interrupt, float volume) { calls[1]++; }
                public void clear() { calls[2]++; }
                public void destroy() { calls[3]++; }
            };
            var lazy = new LazyNarrator(() -> blocked[0], () -> { calls[0]++; return fake; });
            check(calls[0] == 0, "construction does not load platform library", passed);
            check(!lazy.active(), "disabled narrator reports inactive", passed);
            lazy.say("probe", false, 1); lazy.clear();
            check(calls[0] == 0 && calls[1] == 0 && calls[2] == 0, "disabled speech and clear do not initialize", passed);
            blocked[0] = false;
            check(lazy.active() && calls[0] == 1, "reenabling initializes lazily", passed);
            lazy.say("probe", true, .5f);
            check(calls[1] == 1 && calls[0] == 1, "enabled speech uses the one delegate", passed);
            blocked[0] = true; lazy.synchronizePolicy(); lazy.synchronizePolicy();
            check(calls[2] == 1, "disabling clears queued speech exactly once", passed);
            lazy.say("probe", true, 1);
            check(calls[1] == 1 && !lazy.active(), "disabled existing delegate stays silent", passed);
            blocked[0] = false;
            check(lazy.active() && calls[0] == 1, "second enable reuses delegate", passed);
            lazy.destroy(); lazy.destroy();
            check(calls[3] == 1, "shutdown destroys exactly once", passed);
            check(!lazy.active() && calls[0] == 1, "shutdown cannot resurrect delegate", passed);
            var unused = new LazyNarrator(() -> true, () -> { throw new AssertionError("unwanted platform load"); });
            unused.destroy(); check(!unused.active(), "never-used shutdown stays lazy", passed);
            var field = net.minecraft.client.GameNarrator.class.getDeclaredField("narrator"); field.setAccessible(true);
            check(field.get(Minecraft.getInstance().getNarrator()) == current && current != null,
                "actual transformed GameNarrator owns lazy wrapper", passed);
            if (NativeQualityOfLife.enabled("DisableNarrator"))
                check(current.delegate == null, "disabled real client never loaded speech library", passed);
            LoggerFactory.getLogger("TheLadsCore").info("Lads narrator probe END: {} checks passed, 0 failed; mock speech and actual constructor", passed[0]);
        } catch (Throwable failure) {
            LoggerFactory.getLogger("TheLadsCore").error("Lads narrator probe FAILED after {} checks", passed[0], failure);
        }
    }
    private static void check(boolean valid, String message, int[] passed) {
        if (!valid) throw new IllegalStateException(message);
        passed[0]++;
    }
}
