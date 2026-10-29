package com.thelads.core.client.ladsapi;

import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.SliderOption;
import com.thelads.core.client.hud.ToastAction;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LadsApiUiTest {

    @BeforeEach
    @AfterEach
    void resetToastState() {
        LadsApiUi.policyCleared();
    }

    @Test
    void formatPolicyToastTitleSingularAndPlural() {
        assertEquals("Lads SMP limits 1 Lads setting", LadsApiUi.formatPolicyToastTitle("Lads SMP", 1));
        assertEquals("Lads SMP limits 3 Lads settings", LadsApiUi.formatPolicyToastTitle("Lads SMP", 3));
        assertEquals("Lads SMP", LadsApiUi.formatPolicyToastTitle("Lads SMP", 0));
        assertEquals("This server limits 2 Lads settings", LadsApiUi.formatPolicyToastTitle(null, 2));
        assertEquals("This server limits 2 Lads settings", LadsApiUi.formatPolicyToastTitle("", 2));
        assertEquals("Lads SMP unlocks 1 Lads setting", LadsApiUi.formatPolicyToastTitle("Lads SMP", 0, 1));
        assertEquals("Lads SMP unlocks 2 Lads settings", LadsApiUi.formatPolicyToastTitle("Lads SMP", 0, 2));
        assertEquals("Lads SMP limits 3 Lads settings and unlocks 1", LadsApiUi.formatPolicyToastTitle("Lads SMP", 3, 1));
        assertEquals("Lads SMP limits 1 Lads setting and unlocks 2", LadsApiUi.formatPolicyToastTitle("Lads SMP", 1, 2));
    }

    @Test
    void formatOptionTooltipForcedValues() {
        // Forced slider
        OptionRule forcedSlider = new OptionRule(null, 10.0, Double.NaN, Double.NaN, null, null, null, false);
        SliderOption slider = new SliderOption("Brightness", 50, 0, 100, 1).percent();
        assertEquals("Set by Lads SMP", LadsApiUi.formatOptionTooltip("Lads SMP", forcedSlider, slider));

        // Forced bool
        OptionRule forcedBool = new OptionRule(true, Double.NaN, Double.NaN, Double.NaN, null, null, null, false);
        BoolOption boolOpt = new BoolOption("Toggle", false);
        assertEquals("Set by Lads SMP", LadsApiUi.formatOptionTooltip("Lads SMP", forcedBool, boolOpt));

        // Forced choice
        OptionRule forcedChoice = new OptionRule(null, Double.NaN, Double.NaN, Double.NaN, "Fast", null, null, false);
        DropdownOption dropdown = new DropdownOption("Mode", 0, "Fast", "Fancy");
        assertEquals("Set by Lads SMP", LadsApiUi.formatOptionTooltip("Lads SMP", forcedChoice, dropdown));

        // Forced color
        OptionRule forcedColor = new OptionRule(null, Double.NaN, Double.NaN, Double.NaN, null, null, 0xFF00FF00, false);
        assertEquals("Set by Lads SMP", LadsApiUi.formatOptionTooltip("Lads SMP", forcedColor, null));
    }

    @Test
    void formatOptionTooltipSliderLimits() {
        SliderOption percentSlider = new SliderOption("Opacity", 50, 0, 100, 1).percent();
        OptionRule maxOnly = new OptionRule(null, Double.NaN, Double.NaN, 10.0, null, null, null, false);
        assertEquals("Limited by Lads SMP: max 10%", LadsApiUi.formatOptionTooltip("Lads SMP", maxOnly, percentSlider));

        SliderOption regularSlider = new SliderOption("Distance", 4, 1, 16, 1);
        OptionRule minOnly = new OptionRule(null, Double.NaN, 2.0, Double.NaN, null, null, null, false);
        assertEquals("Limited by Lads SMP: min 2", LadsApiUi.formatOptionTooltip("Lads SMP", minOnly, regularSlider));

        OptionRule minAndMax = new OptionRule(null, Double.NaN, 2.0, 8.0, null, null, null, false);
        assertEquals("Limited by Lads SMP: min 2, max 8", LadsApiUi.formatOptionTooltip("Lads SMP", minAndMax, regularSlider));

        // Fallback to "server" when serverName is empty
        assertEquals("Limited by server: max 10%", LadsApiUi.formatOptionTooltip("", maxOnly, percentSlider));
    }

    @Test
    void formatOptionTooltipDropdownChoices() {
        DropdownOption dropdown = new DropdownOption("Style", 0, "A", "B", "C");
        OptionRule choicesRule = new OptionRule(null, Double.NaN, Double.NaN, Double.NaN, null, new String[]{"A", "B"}, null, false);
        assertEquals("Limited by Lads SMP: Only: A, B", LadsApiUi.formatOptionTooltip("Lads SMP", choicesRule, dropdown));
    }

    @Test
    void formatOptionTooltipNullOrLockOnly() {
        assertNull(null);
        assertEquals("", LadsApiUi.formatOptionTooltip("Lads SMP", null, null));

        OptionRule lockOnly = new OptionRule(null, Double.NaN, Double.NaN, Double.NaN, null, null, null, true);
        assertEquals("Set by Lads SMP", LadsApiUi.formatOptionTooltip("Lads SMP", lockOnly, null));
    }

    @Test
    void formatModuleStatusRules() {
        assertEquals("Blocked by Lads SMP", LadsApiUi.formatModuleStatus("Lads SMP", ModuleState.BLOCK, false));
        assertEquals("Blocked by Lads SMP", LadsApiUi.formatModuleStatus("Lads SMP", ModuleState.BLOCK, true));
        assertEquals("Turned on by Lads SMP", LadsApiUi.formatModuleStatus("Lads SMP", ModuleState.ON, false));
        assertNull(LadsApiUi.formatModuleStatus("Lads SMP", ModuleState.ON, true), "When refused, ON status is suppressed");
        assertNull(LadsApiUi.formatModuleStatus("Lads SMP", ModuleState.ALLOW, false));
        assertNull(LadsApiUi.formatModuleStatus("Lads SMP", ModuleState.INHERIT, false));
        assertEquals("Blocked by server", LadsApiUi.formatModuleStatus(null, ModuleState.BLOCK, false));
    }

    @Test
    void policyAppliedAndClearedLifecycle() {
        assertFalse(LadsApiUi.isToastActive());
        assertNull(LadsApiUi.activeToastTitle());
        assertNull(LadsApiUi.activeToastNotice());

        LadsApiUi.policyApplied("Lads SMP", 3, "No cheating permitted");
        assertTrue(LadsApiUi.isToastActive());
        assertEquals("Lads SMP limits 3 Lads settings", LadsApiUi.activeToastTitle());
        assertEquals("No cheating permitted", LadsApiUi.activeToastNotice());

        LadsApiUi.policyCleared();
        assertFalse(LadsApiUi.isToastActive());
        assertNull(LadsApiUi.activeToastTitle());
        assertNull(LadsApiUi.activeToastNotice());

        // Single line toast without notice
        LadsApiUi.policyApplied("Lads SMP", 1, "");
        assertTrue(LadsApiUi.isToastActive());
        assertEquals("Lads SMP limits 1 Lads setting", LadsApiUi.activeToastTitle());
        assertNull(LadsApiUi.activeToastNotice());

        // Zero changes and empty notice shows nothing
        LadsApiUi.policyApplied("Lads SMP", 0, "   ");
        assertFalse(LadsApiUi.isToastActive());

        // Unlocked modules show toast even without limits or notice
        LadsApiUi.policyApplied("Lads SMP", 0, 1, "", java.util.Collections.emptyList());
        assertTrue(LadsApiUi.isToastActive());
        assertEquals("Lads SMP unlocks 1 Lads setting", LadsApiUi.activeToastTitle());
    }

    @Test
    void testPolicyAppliedWithActions() {
        ToastAction action = ToastAction.runCommand("Discord", "/discord");
        LadsApiUi.policyApplied("Lads SMP", 0, "Welcome!", List.of(action));
        assertTrue(LadsApiUi.isToastActive());
        assertEquals(1, LadsApiUi.activeToastActions().size());
        assertEquals("Discord", LadsApiUi.activeToastActions().get(0).getLabel());
    }

    @Test
    void testShowNotification() {
        ToastAction action = ToastAction.openUrl("Store", "https://store.thelads.net");
        LadsApiUi.showNotification("Announcement", "Big sale!", List.of(action));
        assertTrue(LadsApiUi.isToastActive());
        assertEquals("Announcement", LadsApiUi.activeToastTitle());
        assertEquals("Big sale!", LadsApiUi.activeToastNotice());
        assertEquals(1, LadsApiUi.activeToastActions().size());
    }
}
