package com.thelads.core.v1_8_9.feature;

import static com.thelads.core.v1_8_9.feature.CoreProbe.after;
import static com.thelads.core.v1_8_9.feature.CoreProbe.check;
import static com.thelads.core.v1_8_9.feature.CoreProbe.click;
import static com.thelads.core.v1_8_9.feature.CoreProbe.screenshot;
import static com.thelads.core.v1_8_9.feature.CoreProbe.tap;

import com.thelads.core.v1_8_9.gui.ControlsScreen189;
import com.thelads.core.v1_8_9.gui.SmoothScroll189;
import com.thelads.core.v1_8_9.mixin.GuiSlotAccessor;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiLanguage;
import net.minecraft.client.gui.GuiOptions;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiSlot;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.lwjgl.input.Keyboard;

/**
 * QA only: 1.6.0's Controls search and smooth list scrolling, run by CoreProbe in its QA world after Probe151. Options -> Controls
 * (vanilla's button) opens ControlsScreen189; typed name, category: and key: searches, Show Conflicts and Show Unbound (with a
 * conflict and an unbound key made for it) and Reset All asking first. Then wheel notches go through LWJGL's mouse queue into
 * GuiSlot.handleMouseInput on the Controls list (GuiListExtended) and the Language list (GuiSlot), the scroll sampled every frame
 * (DrawScreenEvent.Post): it must glide over several frames to the notches' half rows, and stop when something else moves the
 * list. Key bindings are put back as found. Screenshots 160-*.png; scroll samples in lads-qa/160-scroll-samples.txt.
 */
final class Probe160s {
    static final List<CoreProbe.Step> STEPS = Arrays.<CoreProbe.Step>asList(Probe160s::options, Probe160s::openControls,
        Probe160s::controls, Probe160s::byName, Probe160s::byCategory, mc -> type("key:space"), Probe160s::byKey,
        Probe160s::makeConflict, Probe160s::conflicts, Probe160s::unbound,
        Probe160s::resetAsks, Probe160s::resetDone, mc -> arm(mc, "the Controls list (GuiListExtended)"), mc -> notches(-5),
        Probe160s::glidedControls, Probe160s::interrupt, Probe160s::interrupted, Probe160s::language,
        mc -> arm(mc, "the Language list (GuiSlot)"), mc -> notches(-3), Probe160s::glidedLanguage);
    private static int[] codesWere;
    private static ControlsScreen189 controls;
    private static Frames frames;
    private static float moved;
    private static final StringBuilder SAMPLES = new StringBuilder();

    private Probe160s() {}

    /** Every frame while sampling: the list's scroll, and its pointer kept inside it (GuiSlot reads the real cursor). */
    static final class Frames {
        final GuiSlot list;
        final List<Float> scroll = new ArrayList<>();
        Frames(GuiSlot list) { this.list = list; }

        @SubscribeEvent
        public void drawn(GuiScreenEvent.DrawScreenEvent.Post event) {
            scroll.add(((GuiSlotAccessor) list).ladsScroll());
            ((GuiSlotAccessor) list).ladsSetMouseX(list.left + list.width / 2);
            ((GuiSlotAccessor) list).ladsSetMouseY((list.top + list.bottom) / 2);
        }
    }

    private static boolean options(Minecraft mc) {
        KeyBinding[] keys = mc.gameSettings.keyBindings;
        codesWere = new int[keys.length];
        for (int i = 0; i < keys.length; i++) codesWere[i] = keys[i].getKeyCode();
        mc.displayGuiScreen(new GuiOptions(null, mc.gameSettings));
        return after(10);
    }

    private static boolean openControls(Minecraft mc) throws Exception {
        check(mc.currentScreen instanceof GuiOptions, "Options is open");
        GuiScreen options = mc.currentScreen;
        click(options.width / 2 + 80, options.height / 6 + 100); // GuiOptions' Controls... button (id 100)
        return after(10);
    }

    private static boolean controls(Minecraft mc) throws Exception {
        check(mc.currentScreen instanceof ControlsScreen189, "vanilla's Controls... button opens the Lads Controls with search (GuiOpenEvent)");
        controls = (ControlsScreen189) mc.currentScreen;
        check(controls.shownKeys().size() == mc.gameSettings.keyBindings.length && controls.search().isFocused()
            && controls.search().getText().isEmpty(), "it lists all " + mc.gameSettings.keyBindings.length + " key bindings, search focused");
        screenshot(mc, "160-controls");
        type("jump");
        return after(5);
    }

    private static boolean byName(Minecraft mc) throws Exception {
        Set<KeyBinding> expected = new LinkedHashSet<>();
        for (KeyBinding key : mc.gameSettings.keyBindings)
            if (I18n.format(key.getKeyDescription()).toLowerCase(Locale.ROOT).contains("jump")) expected.add(key);
        check("jump".equals(controls.search().getText()) && shown().equals(expected) && expected.contains(mc.gameSettings.keyBindJump),
            "typing 'jump' filters by name " + names(shown()));
        screenshot(mc, "160-controls-search-name");
        erase(4);
        type("category:movement");
        return after(5);
    }

    private static boolean byCategory(Minecraft mc) throws Exception {
        Set<KeyBinding> expected = new LinkedHashSet<>();
        for (KeyBinding key : mc.gameSettings.keyBindings) if (key.getKeyCategory().equals("key.categories.movement")) expected.add(key);
        check(shown().equals(expected) && expected.size() >= 7, "category:movement lists the movement keys " + names(shown()));
        screenshot(mc, "160-controls-search-category");
        erase("category:movement".length()); // LWJGL queues 50 key events: the next step types
        return after(2);
    }

    private static boolean byKey(Minecraft mc) throws Exception {
        Set<KeyBinding> expected = new LinkedHashSet<>();
        for (KeyBinding key : mc.gameSettings.keyBindings)
            if (GameSettings.getKeyDisplayString(key.getKeyCode()).toLowerCase(Locale.ROOT).contains("space")) expected.add(key);
        check(shown().equals(expected) && !expected.isEmpty(), "key:space lists what is bound to Space " + names(shown()));
        screenshot(mc, "160-controls-search-key");
        erase("key:space".length()); // before the click below: GuiScreen.handleInput reads the mouse queue first
        return after(2);
    }

    /** A conflict to find: Drop on Jump's key. */
    private static boolean makeConflict(Minecraft mc) throws Exception {
        check(controls.search().getText().isEmpty(), "the search is cleared");
        mc.gameSettings.setOptionKeyBinding(mc.gameSettings.keyBindDrop, mc.gameSettings.keyBindJump.getKeyCode());
        KeyBinding.resetKeyBindingArrayAndHash();
        press("Show Conflicts");
        return after(5);
    }

    private static boolean conflicts(Minecraft mc) throws Exception {
        boolean allConflict = true;
        for (KeyBinding key : shown()) {
            boolean other = false;
            for (KeyBinding next : mc.gameSettings.keyBindings) other |= next != key && next.getKeyCode() == key.getKeyCode();
            allConflict &= key.getKeyCode() != 0 && other;
        }
        check(controls.search().getText().isEmpty() && allConflict && shown().contains(mc.gameSettings.keyBindDrop)
            && shown().contains(mc.gameSettings.keyBindJump) && controls.button("Show All") != null,
            "Show Conflicts lists only keys sharing a binding, Drop and Jump among them " + names(shown()));
        screenshot(mc, "160-controls-conflicts");
        mc.gameSettings.setOptionKeyBinding(mc.gameSettings.keyBindDrop, 0);
        KeyBinding.resetKeyBindingArrayAndHash();
        press("Show Unbound");
        return after(5);
    }

    private static boolean unbound(Minecraft mc) throws Exception {
        boolean allUnbound = true;
        for (KeyBinding key : shown()) allUnbound &= key.getKeyCode() == 0;
        check(allUnbound && shown().contains(mc.gameSettings.keyBindDrop) && controls.button("Show Conflicts") != null,
            "Show Unbound lists only unbound keys, the unbound Drop among them " + names(shown()));
        screenshot(mc, "160-controls-unbound");
        press("Show All");
        press(I18n.format("controls.resetAll")); // enabled: Drop is off its default
        return after(5);
    }

    private static boolean resetAsks(Minecraft mc) throws Exception {
        check(controls.shownKeys().size() == mc.gameSettings.keyBindings.length && controls.button("Confirm?") != null
            && mc.gameSettings.keyBindDrop.getKeyCode() == 0, "Show All lists every key again; Reset All first asks (Confirm?) and changes nothing");
        screenshot(mc, "160-controls-reset-confirm");
        press("Confirm?");
        return after(5);
    }

    private static boolean resetDone(Minecraft mc) {
        boolean defaults = true;
        for (KeyBinding key : mc.gameSettings.keyBindings) defaults &= key.getKeyCode() == key.getKeyCodeDefault();
        check(defaults && controls.button(I18n.format("controls.resetAll")) != null, "Confirm? resets every key binding to its default");
        restoreKeys(mc);
        check(keysRestored(mc), "the QA key bindings are put back");
        return after(5);
    }

    private static boolean glidedControls(Minecraft mc) throws Exception {
        glided(mc, "the Controls list (GuiListExtended)", 5, "160-scroll-controls");
        // Another glide back up, interrupted by a direct scroll (as a drag or the scroll buttons move it).
        return notches(5) && after(1);
    }

    private static boolean interrupt(Minecraft mc) {
        GuiSlot list = frames.list;
        check(gliding(list), "five notches up start a glide back (" + ((GuiSlotAccessor) list).ladsScroll() + " one tick later)");
        list.scrollBy(-1);
        moved = ((GuiSlotAccessor) list).ladsScroll();
        return after(3);
    }

    private static boolean interrupted(Minecraft mc) {
        GuiSlot list = frames.list;
        float now = ((GuiSlotAccessor) list).ladsScroll();
        check(now == moved && now > 0 && !gliding(list), "a direct scroll during the glide stops it where it was moved (" + now + ")");
        return after(2);
    }

    private static boolean language(Minecraft mc) {
        mc.displayGuiScreen(new GuiLanguage(null, mc.gameSettings, mc.getLanguageManager()));
        return after(10);
    }

    private static boolean glidedLanguage(Minecraft mc) throws Exception {
        glided(mc, "the Language list (GuiSlot)", 3, "160-scroll-language");
        stop();
        mc.displayGuiScreen(null);
        return after(10);
    }

    /** Starts sampling the current screen's list (a frame keeps its pointer inside before any notch arrives). */
    private static boolean arm(Minecraft mc, String what) throws Exception {
        stopFrames();
        GuiSlot list = slot(mc.currentScreen);
        check(list != null && list.func_148135_f() >= 5 * list.getSlotHeight() / 2 && list.getAmountScrolled() == 0,
            what + " is at its top with room to scroll");
        frames = new Frames(list);
        MinecraftForge.EVENT_BUS.register(frames);
        return after(2);
    }

    /** Wheel notches on the sampled list (negative: down); the glide gets a second. */
    private static boolean notches(int notches) throws Exception {
        frames.scroll.clear();
        CoreProbe.wheel(frames.list.left + frames.list.width / 2, (frames.list.top + frames.list.bottom) / 2, notches);
        return after(20);
    }

    private static boolean gliding(GuiSlot list) { return SmoothScroll189.gliding((SmoothScroll189.Target) (Object) list); }

    /** The frames since the notches: rising one frame at a time through several in-between scrolls, ending at the notches' half rows. */
    private static void glided(Minecraft mc, String what, int notches, String shot) throws Exception {
        GuiSlot list = frames.list;
        float target = notches * (list.getSlotHeight() / 2);
        List<Float> scroll = frames.scroll;
        int between = 0;
        boolean rising = true;
        for (int i = 0; i < scroll.size(); i++) {
            if (scroll.get(i) > 0 && scroll.get(i) < target) between++;
            if (i > 0) rising &= scroll.get(i) >= scroll.get(i - 1);
        }
        String line = what + ": " + notches + " notches, target " + target + ", " + scroll.size() + " frames: " + scroll;
        LogManager.getLogger("TheLadsCore").info("Lads 1.8.9 core probe: {}", line);
        SAMPLES.append(line).append('\n');
        File file = new File(mc.mcDataDir, "lads-qa/160-scroll-samples.txt");
        Files.write(file.toPath(), SAMPLES.toString().getBytes(StandardCharsets.UTF_8));
        check(!scroll.isEmpty() && scroll.get(scroll.size() - 1) == target && rising && between >= 3 && !gliding(list),
            what + " glides to " + target + " over " + between + " in-between frames instead of jumping (samples in " + file.getName() + ")");
        screenshot(mc, shot);
    }

    /** The first list among a screen's fields (and its superclasses'), found by type: field names are obfuscated outside dev. */
    private static GuiSlot slot(GuiScreen screen) throws Exception {
        for (Class<?> type = screen.getClass(); type != GuiScreen.class; type = type.getSuperclass())
            for (Field field : type.getDeclaredFields())
                if (GuiSlot.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return (GuiSlot) field.get(screen);
                }
        return null;
    }

    private static Set<KeyBinding> shown() { return new LinkedHashSet<>(controls.shownKeys()); }

    private static List<String> names(Set<KeyBinding> keys) {
        List<String> names = new ArrayList<>();
        for (KeyBinding key : keys) names.add(I18n.format(key.getKeyDescription()));
        return names;
    }

    private static void press(String label) throws Exception {
        GuiButton button = controls.button(label);
        check(button != null && button.enabled, "the '" + label + "' button is there");
        click(button.xPosition + button.width / 2, button.yPosition + button.height / 2);
    }

    /** Typed keys through LWJGL's keyboard queue, each with its key code (a 0 code would let dispatchKeypresses read the character). */
    private static boolean type(String text) throws Exception {
        for (char c : text.toCharArray())
            tap(c == ':' ? Keyboard.KEY_SEMICOLON : Keyboard.getKeyIndex(String.valueOf(Character.toUpperCase(c))), c);
        return after(5);
    }

    private static void erase(int count) throws Exception {
        for (int i = 0; i < count; i++) tap(Keyboard.KEY_BACK, '\b');
    }

    private static void restoreKeys(Minecraft mc) {
        if (codesWere == null) return;
        KeyBinding[] keys = mc.gameSettings.keyBindings;
        for (int i = 0; i < keys.length && i < codesWere.length; i++) keys[i].setKeyCode(codesWere[i]);
        KeyBinding.resetKeyBindingArrayAndHash();
        mc.gameSettings.saveOptions();
    }

    private static boolean keysRestored(Minecraft mc) {
        KeyBinding[] keys = mc.gameSettings.keyBindings;
        for (int i = 0; i < keys.length; i++) if (keys[i].getKeyCode() != codesWere[i]) return false;
        return true;
    }

    private static void stopFrames() {
        if (frames != null) MinecraftForge.EVENT_BUS.unregister(frames);
    }

    /** The probe ended (or failed): no more sampling, key bindings as found. */
    static void stop() {
        stopFrames();
        frames = null;
        restoreKeys(Minecraft.getMinecraft());
        codesWere = null;
    }
}
