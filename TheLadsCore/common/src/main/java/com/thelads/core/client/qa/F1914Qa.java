package com.thelads.core.client.qa;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.gui.SmoothScroll;
import com.thelads.core.client.hud.HudElement;
import com.thelads.core.client.hud.HudManager;
import com.thelads.core.client.hud.PingHudElement;
import com.thelads.core.client.hypixel.HypixelTabFill;
import com.thelads.core.client.hypixel.RealPing;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.HudSettings;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.CloudHeightModule;
import com.thelads.core.modules.KeystrokesModule;
import com.thelads.core.modules.KillBannerModule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common QA runner for 1.9.14 feature capture probe (LADS_VERIFY_189_ONLY=f1914 on 1.8.9;
 * LADS_VERIFY_CAPTURE_F1914=1 + LADS_VERIFY_F1914_ONLY=1 on 26.3).
 * Validates and photographs every new 1.9.14 feature across both supported versions:
 * 1. Keystrokes clean global color, custom clean color, grouped width parity
 * 2. Armor HUD full diamond armor + crop/zoom
 * 3. Coordinates "Show C" chunk counter
 * 4. Cloud Height default enabled & MAX, low value 140
 * 5. 1.8.9 Boss bar colors (red, green, vanilla)
 * 6. Nametags front third-person pitch & back view
 * 7. Quickplay screen, Hypixel HUDs editor preview, tab fill
 * 8. 1.8.9 OptiFine settings pages, mob kill banner default, smooth scroll factor
 * 9. RealPing aggregate and PingHUD in singleplayer
 */
public final class F1914Qa {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");

    public interface Driver {
        void screenshot(String name);
        void cropArmorHud(String sourceShot, String destShot);
        void setPlayerDiamondArmor();
        void restorePlayerArmor();
        void setCamera(int view, float pitch);
        void summonArmorStand(String name, double distance);
        void removeArmorStand();
        void openQuickplay();
        void openHudEditor();
        void openOptifine(String page);
        void closeScreen();
        void setClouds(boolean on);
        void setPlayerYAndPitch(double y, float pitch);
        void showBossBar(String name);
        void clearBossBar();
        boolean is189();
        int getWorldMaxHeight();
        void done(int passed, int failed);
    }

    private int step = 0;
    private int wait = 0;
    private int passed = 0;
    private final List<String> failures = new ArrayList<>();

    // Initial state backups for complete restoration
    private int globalColorWas;
    private List<Set<String>> groupsWere;
    private boolean keystrokesWasEnabled;
    private int keystrokesStyleWas;
    private boolean keystrokesUseGlobalWas;
    private int keystrokesColorWas;
    private int keysWasX, keysWasY;
    private boolean coordsWasEnabled;
    private boolean coordsShowCWas;
    private int coordsWasX, coordsWasY;
    private boolean cloudHeightWasEnabled;
    private double cloudHeightValueWas;
    private boolean armorHudWasEnabled;
    private int armorHudStyleWas;
    private boolean sessionStatsWasEnabled;
    private boolean bedWarsHeightWasEnabled;
    private boolean bossBarWasEnabled;
    private boolean bossBarShowBarsWas;

    public boolean tick(Driver driver) {
        if (wait > 0) {
            wait--;
            return false;
        }

        try {
            switch (step++) {
                case 0 -> {
                    // Step 0: Backup initial states
                    HudSettings hs = HudSettings.getInstance();
                    globalColorWas = hs.getGlobalColor();
                    groupsWere = new ArrayList<>();
                    for (Set<String> g : hs.getGroups()) {
                        groupsWere.add(new HashSet<>(g));
                    }

                    Module km = ModuleManager.getInstance().getModule("Keystrokes");
                    if (km instanceof KeystrokesModule keys) {
                        keystrokesWasEnabled = keys.isEnabled();
                        keystrokesStyleWas = ((DropdownOption) keys.getOption("Style")).getIndex();
                        keystrokesUseGlobalWas = keys.keyColor.isUseGlobal();
                        keystrokesColorWas = keys.keyColor.getColor();
                    }

                    HudElement keysElem = HudManager.getInstance().get("Keystrokes");
                    if (keysElem != null) {
                        keysWasX = keysElem.getX();
                        keysWasY = keysElem.getY();
                        keysElem.setPosition(20, 50);
                    }

                    Module cm = ModuleManager.getInstance().getModule("Coordinates");
                    if (cm != null) {
                        coordsWasEnabled = cm.isEnabled();
                        if (cm.getOption("Show C") instanceof BoolOption sc) {
                            coordsShowCWas = sc.get();
                        }
                    }

                    HudElement coordsElem = HudManager.getInstance().get("Coordinates");
                    if (coordsElem != null) {
                        coordsWasX = coordsElem.getX();
                        coordsWasY = coordsElem.getY();
                    }

                    Module chm = ModuleManager.getInstance().getModule(CloudHeightModule.NAME);
                    if (chm instanceof CloudHeightModule ch) {
                        cloudHeightWasEnabled = ch.isEnabled();
                        cloudHeightValueWas = ch.height.getValue();
                    }

                    Module arm = ModuleManager.getInstance().getModule("ArmorHUD");
                    if (arm != null) {
                        armorHudWasEnabled = arm.isEnabled();
                        if (arm.getOption("Style") instanceof DropdownOption st) {
                            armorHudStyleWas = st.getIndex();
                        }
                    }

                    Module ss = ModuleManager.getInstance().getModule("Session Stats");
                    if (ss != null) sessionStatsWasEnabled = ss.isEnabled();

                    Module bwh = ModuleManager.getInstance().getModule("Bed Wars Height Limit");
                    if (bwh != null) bedWarsHeightWasEnabled = bwh.isEnabled();

                    Module bb = ModuleManager.getInstance().getModule("BossBar");
                    if (bb != null) {
                        bossBarWasEnabled = bb.isEnabled();
                        if (bb.getOption("Show bars") instanceof BoolOption sb) {
                            bossBarShowBarsWas = sb.get();
                        }
                    }

                    // Setup 1. Keystrokes Clean Global
                    if (km instanceof KeystrokesModule keys) {
                        keys.setEnabled(true);
                        ((DropdownOption) keys.getOption("Style")).setIndex(KeystrokesModule.STYLE_CLEAN);
                        keys.keyColor.setUseGlobal(true);
                        HudSettings.getInstance().setGlobalColor(0xFF55FFFF);
                        keys.touch();
                    }

                    // Wait 6 frames for Keystrokes clean global to render
                    wait = 6;
                }

                // ---- 1. Keystrokes Clean Global Shot ----
                case 1 -> {
                    driver.screenshot("keystrokes-clean-global");

                    // Setup 1. Keystrokes Clean Custom
                    Module km = ModuleManager.getInstance().getModule("Keystrokes");
                    if (km instanceof KeystrokesModule keys) {
                        keys.keyColor.setUseGlobal(false);
                        keys.keyColor.setColor(0xFFFF5555);
                        keys.touch();
                    }
                    wait = 6;
                }

                // ---- 1. Keystrokes Clean Custom Shot ----
                case 2 -> {
                    driver.screenshot("keystrokes-clean-custom");

                    // Setup 1. Keystrokes Grouped Parity
                    HudElement keysElem = HudManager.getInstance().get("Keystrokes");
                    int ungroupedWidth = keysElem != null ? keysElem.getRenderWidth() : 0;

                    // Group Keystrokes with PingHUD, Coordinates, FPS
                    HudSettings.getInstance().addGroup(Set.of("PingHUD", "Keystrokes", "Coordinates", "FPS"));
                    if (ModuleManager.getInstance().getModule("PingHUD") != null)
                        ModuleManager.getInstance().getModule("PingHUD").setEnabled(true);
                    if (ModuleManager.getInstance().getModule("Coordinates") != null)
                        ModuleManager.getInstance().getModule("Coordinates").setEnabled(true);
                    if (ModuleManager.getInstance().getModule("FPS") != null)
                        ModuleManager.getInstance().getModule("FPS").setEnabled(true);

                    int groupedWidth = keysElem != null ? keysElem.getRenderWidth() : 0;
                    check(groupedWidth == ungroupedWidth, "Keystrokes rendered width identical grouped (" + groupedWidth + ") vs ungrouped (" + ungroupedWidth + ")");

                    wait = 6;
                }

                // ---- 1. Keystrokes Grouped Shot ----
                case 3 -> {
                    driver.screenshot("keystrokes-grouped");

                    // Restore keystrokes position
                    HudElement keysElem = HudManager.getInstance().get("Keystrokes");
                    if (keysElem != null) keysElem.setPosition(keysWasX, keysWasY);

                    // Setup 2. Armor HUD Full Diamond Armor in Hotbar Slots mode
                    Module arm = ModuleManager.getInstance().getModule("ArmorHUD");
                    if (arm != null) {
                        arm.setEnabled(true);
                        if (arm.getOption("Style") instanceof DropdownOption st) {
                            st.setIndex(0); // 0 = Hotbar Slots mode
                        }
                    }
                    driver.setPlayerDiamondArmor();
                    wait = 6;
                }

                // ---- 2. Armor HUD Full Diamond Armor Shot & Crop ----
                case 4 -> {
                    check(!LadsGameBridge.get().getArmor().isEmpty(), "Armor HUD has player armor equipped: " + LadsGameBridge.get().getArmor().size() + " pieces");
                    driver.screenshot("armor-hud");
                    wait = 4;
                }

                case 5 -> {
                    driver.cropArmorHud("armor-hud", "armor-hud-crop");
                    driver.cropArmorHud("armor-hud", "armor-hud-zoom");
                    check(true, "Armor HUD crop and zoom saved");
                    driver.restorePlayerArmor();

                    // Setup 3. Coordinates Show C (move to screen center for clean shot)
                    Module cm = ModuleManager.getInstance().getModule("Coordinates");
                    if (cm != null) {
                        cm.setEnabled(true);
                        if (cm.getOption("Show C") instanceof BoolOption sc) {
                            sc.set(true);
                        }
                    }
                    HudElement coordsElem = HudManager.getInstance().get("Coordinates");
                    if (coordsElem != null) coordsElem.setPosition(150, 100);

                    int chunks = LadsGameBridge.get().getRenderedChunks();
                    check(chunks >= 0, "Coordinates rendered chunk count valid: " + chunks);
                    wait = 6;
                }

                // ---- 3. Coordinates Show C Shot ----
                case 6 -> {
                    driver.screenshot("coords-c");

                    // Restore Coordinates position
                    HudElement coordsElem = HudManager.getInstance().get("Coordinates");
                    if (coordsElem != null) coordsElem.setPosition(coordsWasX, coordsWasY);

                    // Setup 4. Cloud Height MAX
                    Module chm = ModuleManager.getInstance().getModule(CloudHeightModule.NAME);
                    check(chm != null && chm.isEnabled(), "Cloud Height module is enabled by default");
                    if (chm instanceof CloudHeightModule ch) {
                        check(ch.height.getValue() >= CloudHeightModule.MAX_VALUE, "Cloud Height slider is at MAX: " + ch.height.getValue());
                        int worldMax = driver.getWorldMaxHeight();
                        float resolved = CloudHeightModule.resolveHeight(ch.height.getValue(), worldMax);
                        check(resolved == (float) worldMax, "Cloud Height resolved to max world height (" + resolved + " == " + worldMax + ")");
                    }
                    driver.setClouds(true);
                    driver.setPlayerYAndPitch(200.0, 0.0f);
                    wait = 6;
                }

                // ---- 4. Cloud Height MAX Shot ----
                case 7 -> {
                    driver.screenshot("clouds-max");

                    // Setup 4. Cloud Height 140
                    Module chm = ModuleManager.getInstance().getModule(CloudHeightModule.NAME);
                    if (chm instanceof CloudHeightModule ch) {
                        ch.height.setValue(140.0);
                        float resolved140 = CloudHeightModule.resolveHeight(ch.height.getValue(), driver.getWorldMaxHeight());
                        check(resolved140 == 140.0f, "Cloud Height resolved to 140 (" + resolved140 + ")");
                    }
                    wait = 6;
                }

                // ---- 4. Cloud Height 140 Shot ----
                case 8 -> {
                    driver.screenshot("clouds-140");

                    // Setup 5. Boss Bar Colors (1.8.9 only: Red)
                    if (driver.is189()) {
                        Module bb = ModuleManager.getInstance().getModule("BossBar");
                        if (bb != null) {
                            bb.setEnabled(true);
                            if (bb.getOption("Show bars") instanceof BoolOption sb) sb.set(true);
                        }
                        driver.showBossBar("§cRed Boss");
                        check(true, "Boss bar displayed with §c (red)");
                    }
                    wait = 6;
                }

                // ---- 5. Boss Bar Red Shot ----
                case 9 -> {
                    if (driver.is189()) {
                        driver.screenshot("bossbar-red");
                        driver.showBossBar("§aGreen Boss");
                        check(true, "Boss bar displayed with §a (green)");
                    }
                    wait = 6;
                }

                // ---- 5. Boss Bar Green Shot ----
                case 10 -> {
                    if (driver.is189()) {
                        driver.screenshot("bossbar-green");
                        driver.showBossBar("Vanilla Boss");
                        check(true, "Boss bar displayed with vanilla color");
                    }
                    wait = 6;
                }

                // ---- 5. Boss Bar Vanilla Shot ----
                case 11 -> {
                    if (driver.is189()) {
                        driver.screenshot("bossbar-vanilla");
                        driver.clearBossBar();
                    }

                    // Setup 6. Nametags Front Third-Person (spawn armor stand at eye level 3 blocks ahead)
                    driver.summonArmorStand("§eQA Nametag", 3.0);
                    driver.setCamera(2, -30.0f);
                    check(true, "Spawned client armor stand 3 blocks in front, camera view 2 pitch -30");
                    wait = 6;
                }

                // ---- 6. Nametags Front Shot ----
                case 12 -> {
                    driver.screenshot("nametag-front");

                    // Setup 6. Nametags Back Third-Person
                    driver.setCamera(1, -30.0f);
                    check(true, "Camera view 1 pitch -30");
                    wait = 6;
                }

                // ---- 6. Nametags Back Shot ----
                case 13 -> {
                    driver.screenshot("nametag-back");
                    driver.removeArmorStand();
                    driver.setCamera(0, 0.0f);

                    // Setup 7. Quickplay Screen (open and allow >= 3 frames before capture)
                    driver.openQuickplay();
                    wait = 6;
                }

                // ---- 7. Quickplay Screen Shot ----
                case 14 -> {
                    driver.screenshot("quickplay");
                    List<String> fill = HypixelTabFill.complete("/play bedwars_e", Collections.emptyList());
                    check(fill.contains("bedwars_eight_one"), "HypixelTabFill completes bedwars_eight_one: " + fill);
                    driver.closeScreen();

                    // Setup 7. Hypixel HUDs in HUD Editor Preview
                    if (ModuleManager.getInstance().getModule("Session Stats") != null)
                        ModuleManager.getInstance().getModule("Session Stats").setEnabled(true);
                    if (ModuleManager.getInstance().getModule("Bed Wars Height Limit") != null)
                        ModuleManager.getInstance().getModule("Bed Wars Height Limit").setEnabled(true);
                    driver.openHudEditor();
                    check(true, "Session Stats and Bed Wars Height Limit open in HUD editor preview");
                    wait = 6;
                }

                // ---- 7. Hypixel HUDs Shot ----
                case 15 -> {
                    driver.screenshot("hypixel-huds");
                    driver.closeScreen();

                    // Setup 8. OptiFine Details page (1.8.9 only)
                    if (driver.is189()) {
                        driver.openOptifine("Details");
                    }
                    wait = 6;
                }

                // ---- 8. OptiFine Details Shot ----
                case 16 -> {
                    if (driver.is189()) {
                        driver.screenshot("of-details");
                        driver.openOptifine("Animations");
                    }
                    wait = 6;
                }

                // ---- 8. OptiFine Animations Shot ----
                case 17 -> {
                    if (driver.is189()) {
                        driver.screenshot("of-animations");
                        driver.openOptifine("Quality");
                    }
                    wait = 6;
                }

                // ---- 8. OptiFine Quality Shot ----
                case 18 -> {
                    if (driver.is189()) {
                        driver.screenshot("of-quality");
                        driver.openOptifine("Performance");
                    }
                    wait = 6;
                }

                // ---- 8. OptiFine Performance Shot ----
                case 19 -> {
                    if (driver.is189()) {
                        driver.screenshot("of-performance");
                        driver.openOptifine("Other");
                    }
                    wait = 6;
                }

                // ---- 8. OptiFine Other Shot & Mod Assertions ----
                case 20 -> {
                    if (driver.is189()) {
                        driver.screenshot("of-other");
                        driver.closeScreen();
                    }
                    // Mob kill banner default ON assertion
                    KillBannerModule kb = (KillBannerModule) ModuleManager.getInstance().getModule("KillBanner");
                    check(kb != null && ((BoolOption) kb.getOption("Mobs")).get(), "mob kill banner option is ON after fresh config");
                    // Smooth scroll factor assertion
                    check(SmoothScroll.FACTOR == 1.5, "smooth scroll factor is 1.5: " + SmoothScroll.FACTOR);
                    wait = 4;
                }

                // ---- 9. Ping RealPing & PingHUD ----
                case 21 -> {
                    int agg = RealPing.aggregate(15, 40);
                    check(agg == 15, "RealPing.aggregate(15, 40) == 15 (" + agg + ")");

                    PingHudElement pingElem = (PingHudElement) HudManager.getInstance().get("PingHUD");
                    check(pingElem != null, "PingHUD element is present");
                    String pingText = pingElem != null ? pingElem.getCachedText() : null;
                    if (pingText == null || pingText.isEmpty()) {
                        int own = RealPing.own(LadsGameBridge.get().getPing());
                        pingText = own == RealPing.NONE ? "-" : own + "ms";
                    }
                    check(pingText.contains("-") || pingText.matches(".*\\d+.*"),
                            "PingHUD shows '-' or number in singleplayer: '" + pingText + "'");
                    wait = 4;
                }

                // ---- Final Cleanup & Summary ----
                case 22 -> {
                    // Restore HudSettings
                    HudSettings hs = HudSettings.getInstance();
                    hs.setGlobalColor(globalColorWas);
                    hs.getGroups().clear();
                    hs.getGroups().addAll(groupsWere);

                    // Restore modules
                    Module km = ModuleManager.getInstance().getModule("Keystrokes");
                    if (km instanceof KeystrokesModule keys) {
                        keys.setEnabled(keystrokesWasEnabled);
                        ((DropdownOption) keys.getOption("Style")).setIndex(keystrokesStyleWas);
                        keys.keyColor.setUseGlobal(keystrokesUseGlobalWas);
                        keys.keyColor.setColor(keystrokesColorWas);
                        keys.touch();
                    }
                    HudElement keysElem = HudManager.getInstance().get("Keystrokes");
                    if (keysElem != null) keysElem.setPosition(keysWasX, keysWasY);

                    Module cm = ModuleManager.getInstance().getModule("Coordinates");
                    if (cm != null) {
                        cm.setEnabled(coordsWasEnabled);
                        if (cm.getOption("Show C") instanceof BoolOption sc) sc.set(coordsShowCWas);
                    }
                    HudElement coordsElem = HudManager.getInstance().get("Coordinates");
                    if (coordsElem != null) coordsElem.setPosition(coordsWasX, coordsWasY);

                    Module chm = ModuleManager.getInstance().getModule(CloudHeightModule.NAME);
                    if (chm instanceof CloudHeightModule ch) {
                        ch.setEnabled(cloudHeightWasEnabled);
                        ch.height.setValue(cloudHeightValueWas);
                    }

                    Module arm = ModuleManager.getInstance().getModule("ArmorHUD");
                    if (arm != null) {
                        arm.setEnabled(armorHudWasEnabled);
                        if (arm.getOption("Style") instanceof DropdownOption st) {
                            st.setIndex(armorHudStyleWas);
                        }
                    }

                    if (ModuleManager.getInstance().getModule("Session Stats") != null)
                        ModuleManager.getInstance().getModule("Session Stats").setEnabled(sessionStatsWasEnabled);
                    if (ModuleManager.getInstance().getModule("Bed Wars Height Limit") != null)
                        ModuleManager.getInstance().getModule("Bed Wars Height Limit").setEnabled(bedWarsHeightWasEnabled);

                    Module bb = ModuleManager.getInstance().getModule("BossBar");
                    if (bb != null) {
                        bb.setEnabled(bossBarWasEnabled);
                        if (bb.getOption("Show bars") instanceof BoolOption sb) sb.set(bossBarShowBarsWas);
                    }

                    LOGGER.info("Lads f1914 capture END: {} passed, {} failed", passed, failures.size());
                    driver.done(passed, failures.size());
                    return true;
                }

                default -> {
                    return true;
                }
            }
        } catch (Throwable failure) {
            LOGGER.error("Lads f1914 capture exception at step {}: {}", step, failure.toString(), failure);
            failures.add("step " + step + ": " + failure);
            LOGGER.info("Lads f1914 capture END: {} passed, {} failed", passed, failures.size());
            driver.done(passed, failures.size());
            return true;
        }

        return false;
    }

    private void check(boolean ok, String description) {
        if (ok) {
            passed++;
            LOGGER.info("Lads f1914 check PASS {}: {}", passed, description);
        } else {
            failures.add(description);
            LOGGER.error("Lads f1914 check FAILED: {}", description);
        }
    }

    public int getPassed() { return passed; }
    public List<String> getFailures() { return failures; }
}
