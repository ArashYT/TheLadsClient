package com.thelads.core;

import com.thelads.core.client.LadsNetworkStatus;
import com.thelads.core.client.title.TitleNoir;
import com.thelads.core.client.title.TitleScreenTheme;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.ModuleManager;
import com.thelads.core.modules.TitleScreenModule;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TitleScreenThemeTest {
    @Test void nativeActionsRemainOnscreenAndNeverOverlapAcrossGuiScales() {
        for (int[] size : new int[][] {{320, 180}, {320, 240}, {480, 270}, {530, 300}, {640, 360}, {960, 540}, {1920, 1080}}) {
          for (int count : new int[] {2, 6, 9, 10, 12, 16}) {
            var layout = TitleScreenTheme.layout(size[0], size[1], count);
            assertEquals(count, layout.buttons().size());
            for (int i = 0; i < layout.buttons().size(); i++) {
                var a = layout.buttons().get(i);
                assertTrue(a.width() > 0 && a.height() >= 14);
                assertTrue(a.x() >= 0 && a.y() >= 0);
                assertTrue(a.x() + a.width() <= size[0]);
                assertTrue(a.y() + a.height() <= size[1] - 29, "Button " + i + " of " + count + " must remain above account footer at " + size[0] + "x" + size[1]);
                for (int j = i + 1; j < layout.buttons().size(); j++) {
                    var b = layout.buttons().get(j);
                    assertTrue(a.x() + a.width() <= b.x() || b.x() + b.width() <= a.x() || a.y() + a.height() <= b.y() || b.y() + b.height() <= a.y());
                }
            }
          }
        }
    }

    /** The title's own controls (menu, fullscreen top right, Switch after the account name) never overlap. */
    @Test void titleControlsStayApartFromSmallWindowsToLargeOnes() {
        for (int w = 240; w <= 1000; w += 20)
          for (int h = 180; h <= 560; h += 20)
            for (int count : new int[] {6, 7, 8}) {
                var layout = TitleScreenTheme.layout(w, h, count);
                var g = new LadsGraphicsTest.MockGraphics();
                var switchBox = TitleScreenTheme.renderBackground(g, layout, "Sixteen_Letters1", "26.2", false, 0, true);
                String at = count + " buttons at " + w + "x" + h;
                int buildLeft = w - 18 - g.textWidth(LadsVersion.clientName() + " (26.2)");
                assertTrue(switchBox.x() >= 27 && switchBox.x() + switchBox.width() <= buildLeft, "Switch between the name and the build " + at);
                var boxes = new java.util.ArrayList<>(layout.buttons());
                int fsWidth = TitleScreenTheme.fullscreenWidth(w, Math.max(g.textWidth(TitleScreenTheme.FULLSCREEN), g.textWidth(TitleScreenTheme.WINDOWED)));
                boxes.add(new TitleScreenTheme.Rect(TitleScreenTheme.fullscreenX(w, fsWidth), 6, fsWidth, 20));
                boxes.add(new TitleScreenTheme.Rect(TitleScreenTheme.languageX(w, fsWidth), 6, 20, 20));
                int toggleWidth = TitleScreenTheme.layoutToggleWidth(w, g.textWidth(TitleScreenTheme.LEGACY_LAYOUT));
                boxes.add(new TitleScreenTheme.Rect(TitleScreenTheme.languageX(w, fsWidth) - TitleScreenTheme.HEADER_GAP - toggleWidth, 6, toggleWidth, 20));
                boxes.add(switchBox);
                for (int i = 0; i < boxes.size(); i++) {
                    var a = boxes.get(i);
                    assertTrue(a.x() >= 0 && a.y() >= 0 && a.x() + a.width() <= w && a.y() + a.height() <= h, "box " + i + " on screen, " + at);
                    for (int j = i + 1; j < boxes.size(); j++) {
                        var b = boxes.get(j);
                        assertTrue(a.x() + a.width() <= b.x() || b.x() + b.width() <= a.x() || a.y() + a.height() <= b.y() || b.y() + b.height() <= a.y(),
                            "boxes " + i + " and " + j + " overlap, " + at);
                    }
                }
            }
        // Roomy: Switch is labelled; cramped: its icon alone.
        var g = new LadsGraphicsTest.MockGraphics();
        assertEquals(g.textWidth("Switch") + TitleScreenTheme.COMPACT_PADDING,
            TitleScreenTheme.renderBackground(g, TitleScreenTheme.layout(640, 360, 8), "LadsQA", "26.2", false, 0, true).width());
        assertEquals(16, TitleScreenTheme.renderBackground(g, TitleScreenTheme.layout(240, 180, 8), "LadsQA", "26.2", false, 0, true).width());
        assertNull(TitleScreenTheme.renderBackground(g, TitleScreenTheme.layout(640, 360, 8), "LadsQA", "26.2", false, 0, false));
    }

    @Test void pauseIconsSitBeforeTheirCentredLabel() {
        int icon = TitleScreenTheme.LABEL_ICON + TitleScreenTheme.LABEL_ICON_GAP;
        int left = TitleScreenTheme.iconLabelX(100, 200, 60);
        assertEquals(left - icon - 100, 300 - (left + 60), "icon and label centred together: equal margins");
        assertEquals(100 + 4 + icon, TitleScreenTheme.iconLabelX(100, 40, 60), "a label too long starts after the icon at the edge");
    }

    @Test void optionalNativeControlsAreRetainedInLayout() {
        assertEquals(0, TitleScreenTheme.layout(640, 360, 0).buttons().size());
        assertEquals(12, TitleScreenTheme.layout(640, 360, 12).buttons().size());
        assertEquals(16, TitleScreenTheme.layout(960, 540, 16).buttons().size());
    }

    /** The account's head is the one skin here: LadsGraphics.drawHead never waits for it (SkinProfilesTest). */
    @Test void renderingDrawsOnlyTheAccountsHead() {
        var g = new LadsGraphicsTest.MockGraphics();
        var layout = TitleScreenTheme.layout(640, 360, 9);
        TitleScreenTheme.renderBackground(g, layout, "LadsQA", "26.2", false, 1);
        var button = layout.buttons().getFirst();
        TitleScreenTheme.renderButton(g, button.x(), button.y(), button.width(), button.height(), "Singleplayer", "play", true, true, true, true, 1);
        assertEquals(java.util.List.of("head:LadsQA"), g.drawCalls.stream().filter(c -> c.startsWith("head:") || c.startsWith("blit:")).toList());
        assertEquals(g.drawCalls.stream().filter(c -> c.equals("pushPose")).count(), g.drawCalls.stream().filter(c -> c.equals("popPose")).count());
        assertTrue(g.drawCalls.stream().anyMatch(c -> c.contains("LadsQA")));
    }

    private static void assertApart(java.util.List<TitleScreenTheme.Rect> boxes, int w, int h, String at) {
        for (int i = 0; i < boxes.size(); i++) {
            var a = boxes.get(i);
            assertTrue(a.x() >= 0 && a.y() >= 0 && a.x() + a.width() <= w && a.y() + a.height() <= h, "box " + i + " on screen, " + at);
            for (int j = i + 1; j < boxes.size(); j++) {
                var b = boxes.get(j);
                assertTrue(a.x() + a.width() <= b.x() || b.x() + b.width() <= a.x() || a.y() + a.height() <= b.y() || b.y() + b.height() <= a.y(),
                    "boxes " + i + " and " + j + " overlap, " + at);
            }
        }
    }

    /** New layout: every action placed, nothing overlaps (with the header and the strip's Switch), the wordmark clear of the buttons. */
    @Test void newLayoutPlacesEveryActionApartAtAllSizes() {
        var g = new LadsGraphicsTest.MockGraphics();
        for (int w = 320; w <= 1000; w += 20)
          for (int h = 240; h <= 560; h += 20)
            for (int count : new int[] {2, 5, 8, 12, 13, 16, 20})
              for (int network : new int[] {-1, 2}) {
                String at = count + " buttons, network " + network + " at " + w + "x" + h;
                var l = TitleNoir.layout(w, h, count, network);
                assertEquals(count, l.buttons().size(), at);
                assertEquals(count, l.tiers().size(), at);
                var boxes = new java.util.ArrayList<>(l.buttons());
                for (var b : l.buttons()) {
                    assertTrue(b.height() >= 12 && b.width() >= 40, at);
                    assertTrue(b.x() + b.width() <= l.railWidth() && b.y() + b.height() <= l.stripTop(), "inside the slab, above the strip, " + at);
                }
                assertTrue(l.wordY() + TitleNoir.wordHeight(l.block()) + (l.tagline() ? 14 : 0) <= l.buttons().get(0).y(), "wordmark above the buttons, " + at);
                var box = TitleNoir.renderStrip(g, l, "Sixteen_Letters1", "1.20.1 Demo", true, "1234 online");
                boxes.add(box);
                int buildLeft = w - 6 - g.textWidth("1.20.1 Demo");
                assertTrue(box.x() + box.width() <= buildLeft - g.textWidth("1234 online") - 8, "Switch left of network and build, " + at);
                assertTrue(box.y() >= l.stripTop() && box.y() + box.height() <= h, "Switch inside the strip, " + at);
                int fsWidth = TitleScreenTheme.fullscreenWidth(w, 9 * 6);
                int languageX = TitleScreenTheme.languageX(w, fsWidth);
                int toggleWidth = TitleScreenTheme.layoutToggleWidth(w, g.textWidth(TitleScreenTheme.LEGACY_LAYOUT));
                boxes.add(new TitleScreenTheme.Rect(TitleScreenTheme.fullscreenX(w, fsWidth), 6, fsWidth, 20));
                boxes.add(new TitleScreenTheme.Rect(languageX, 6, 20, 20));
                boxes.add(new TitleScreenTheme.Rect(languageX - TitleScreenTheme.HEADER_GAP - toggleWidth, 6, toggleWidth, 20));
                assertApart(boxes, w, h, at);
              }
    }

    /** The logo keeps its shape and stays clear of the buttons and the header at every Title Scale, in both layouts; without pictures the old artwork draws. */
    @Test void theLogoStaysClearOfTheMenus() {
        for (int w = 320; w <= 1000; w += 20)
          for (int h = 240; h <= 560; h += 20)
            for (int count : new int[] {2, 8, 13, 20})
              for (float scale : new float[] {.6f, 1f, 1.6f}) {
                String at = count + " buttons, scale " + scale + " at " + w + "x" + h;
                var l = TitleNoir.layout(w, h, count, -1);
                var logo = TitleNoir.logoBox(l, scale);
                if (logo.width() >= 50) assertEquals(934 / 584d, (double) logo.width() / logo.height(), .05, at);
                assertTrue(logo.x() >= 0 && logo.y() >= 0 && logo.x() + logo.width() <= w, at);
                assertTrue(logo.y() + logo.height() <= l.buttons().get(0).y(), "above the buttons, " + at);
                if (scale <= 1) assertTrue(logo.x() + logo.width() <= l.railWidth(), "inside the slab, " + at);
                assertTrue(logo.x() + logo.width() <= Math.max(l.railWidth() - 10, w - 138), "clear of the header, " + at);
              }
        var noirLogos = new java.util.ArrayList<String>();
        var noirPictures = new LadsGraphicsTest.MockGraphics() {
            @Override public boolean drawImage(String source, int x, int y, int width, int height) {
                noirLogos.add(source);
                return true;
            }
        };
        TitleNoir.renderBackdrop(noirPictures, TitleNoir.layout(640, 360, 12, 2), "LadsQA", "26.3", true);
        assertEquals(java.util.List.of(TitleScreenTheme.LOGO), noirLogos, "New layout keeps two-line Blockbench logo");

        var images = new java.util.ArrayList<TitleScreenTheme.Rect>();
        var pictures = new LadsGraphicsTest.MockGraphics() {
            @Override public boolean drawImage(String source, int x, int y, int width, int height) {
                assertEquals(TitleScreenTheme.LEGACY_LOGO, source, "Legacy layout draws pre-1.13.3 one-line logo");
                images.add(new TitleScreenTheme.Rect(x, y, width, height));
                return true;
            }
        };
        for (int w = 240; w <= 1000; w += 20)
          for (int h = 180; h <= 560; h += 20) {
            var layout = TitleScreenTheme.layout(w, h, 9);
            TitleScreenTheme.renderBackground(pictures, layout, "LadsQA", "26.3", false, 0, true);
            var logo = images.get(images.size() - 1);
            String at = w + "x" + h;
            assertTrue(logo.x() >= 0 && logo.y() >= 0 && logo.x() + logo.width() <= w && logo.y() + logo.height() <= h - 29, at);
            for (var b : layout.buttons())
                assertTrue(logo.x() + logo.width() <= b.x() || logo.y() + logo.height() <= b.y(), "logo clear of the menu, " + at);
          }
        assertFalse(pictures.drawCalls.stream().anyMatch(c -> c.contains("LADS")));
        var noPictures = new LadsGraphicsTest.MockGraphics();
        TitleScreenTheme.renderBackground(noPictures, TitleScreenTheme.layout(640, 360, 9), "LadsQA", "26.3", false, 0, true);
        assertTrue(noPictures.drawCalls.stream().anyMatch(c -> c.contains("LADS")));
    }

    @Test void newLayoutRanksSingleplayerAndMultiplayerOverTheRest() {
        var l = TitleNoir.layout(640, 360, 13, 2);
        assertEquals(TitleNoir.Tier.PRIMARY, l.tiers().get(0));
        assertEquals(TitleNoir.Tier.HERO, l.tiers().get(1));
        assertEquals(TitleNoir.Tier.NETWORK, l.tiers().get(2));
        for (int i = 3; i < 13; i++) assertEquals(TitleNoir.Tier.LINK, l.tiers().get(i));
        assertTrue(l.buttons().get(0).height() > l.buttons().get(2).height() && l.buttons().get(2).height() > l.buttons().get(3).height());
        // A modpack: no network row, and the first text link takes its turn.
        var pack = TitleNoir.layout(640, 360, 12, -1);
        assertFalse(pack.tiers().contains(TitleNoir.Tier.NETWORK));
        // Demo: no Multiplayer, the network row follows the one loud button.
        assertEquals(TitleNoir.Tier.NETWORK, TitleNoir.layout(640, 360, 5, 1).tiers().get(1));
    }

    @Test void newLayoutPicksAWholeCanvasScale() {
        assertEquals(2, TitleNoir.windowPerCanvasPixel(854, 480));
        assertEquals(3, TitleNoir.windowPerCanvasPixel(1920, 1080));
        assertEquals(2, TitleNoir.windowPerCanvasPixel(1280, 720));
        assertEquals(4, TitleNoir.windowPerCanvasPixel(2560, 1440));
        assertEquals(1, TitleNoir.windowPerCanvasPixel(320, 240));
        assertTrue(TitleNoir.windowPerCanvasPixel(300, 200) < 1, "a tiny window scales the minimum canvas down");
    }

    @Test void layoutOptionDefaultsToNewTogglesAndSurvivesASaveAndLoad() {
        var module = ModuleManager.getInstance().getModule("TitleScreen");
        var option = (DropdownOption) module.getOption("Layout");
        int own = option.getOwnIndex();
        try {
            option.reset();
            assertFalse(TitleScreenModule.legacyLayout(), "the new layout is the default");
            assertEquals(TitleScreenTheme.LEGACY_LAYOUT, TitleScreenTheme.layoutToggleLabel());
            option.setIndex(1);
            assertTrue(TitleScreenModule.legacyLayout());
            assertEquals(TitleScreenTheme.NEW_LAYOUT, TitleScreenTheme.layoutToggleLabel());
            var saved = option.save();
            option.reset();
            option.load(saved);
            assertTrue(TitleScreenModule.legacyLayout(), "the saved choice comes back");
        } finally {
            option.setIndex(own);
        }
    }

    @Test void panoramaOptionDefaultsToOnAndSurvivesSaveAndLoad() {
        var module = ModuleManager.getInstance().getModule("TitleScreen");
        var option = (com.thelads.core.config.BoolOption) module.getOption(TitleScreenModule.PANORAMA);
        assertNotNull(option, "Panorama option must exist on TitleScreenModule");
        boolean own = option.getOwn();
        try {
            option.reset();
            assertTrue(TitleScreenModule.panorama(), "Panorama is on by default");
            option.set(false);
            assertFalse(TitleScreenModule.panorama());
            var saved = option.save();
            option.reset();
            option.load(saved);
            assertFalse(TitleScreenModule.panorama(), "Saved Panorama=off survives save and load");
        } finally {
            option.set(own);
        }
    }

    @Test void noirDrawsSimpleProceduralBackdropWhenPanoramaIsOff() {
        var module = ModuleManager.getInstance().getModule("TitleScreen");
        var option = (com.thelads.core.config.BoolOption) module.getOption(TitleScreenModule.PANORAMA);
        boolean own = option.getOwn();
        try {
            var layout = TitleNoir.layout(640, 360, 12, 2);
            option.set(true);
            var panoOn = new LadsGraphicsTest.MockGraphics();
            TitleNoir.renderBackdrop(panoOn, layout, "LadsQA", "26.3", true, 1.5);

            option.set(false);
            var panoOff = new LadsGraphicsTest.MockGraphics();
            TitleNoir.renderBackdrop(panoOff, layout, "LadsQA", "26.3", true, 1.5);

            assertTrue(panoOff.drawCalls.size() > panoOn.drawCalls.size() + 200,
                "Panorama=off draws the simple sky and mountain-ridge backdrop");
            assertTrue(panoOff.drawCalls.size() < 1500, "Simple backdrop stays bounded");
        } finally {
            option.set(own);
        }
    }

    @Test void noirBurgundyWaveMovesTopToBottomWithBoundedFills() {
        boolean before = TitleNoir.waveEnabled();
        try {
            var layout = TitleNoir.layout(640, 360, 12, 2);
            TitleNoir.setWaveEnabled(true);
            var t0 = new LadsGraphicsTest.MockGraphics();
            var t1 = new LadsGraphicsTest.MockGraphics();
            TitleNoir.renderBackdrop(t0, layout, "LadsQA", "26.3", true, 0.5);
            TitleNoir.renderBackdrop(t1, layout, "LadsQA", "26.3", true, 2.0);
            assertNotEquals(t0.drawCalls, t1.drawCalls, "Burgundy wave shifts position as elapsed seconds advance");

            TitleNoir.setWaveEnabled(false);
            var off0 = new LadsGraphicsTest.MockGraphics();
            var off1 = new LadsGraphicsTest.MockGraphics();
            TitleNoir.renderBackdrop(off0, layout, "LadsQA", "26.3", true, 0.5);
            TitleNoir.renderBackdrop(off1, layout, "LadsQA", "26.3", true, 2.0);
            assertEquals(off0.drawCalls, off1.drawCalls, "Disabling wave stops slab wave fills");
            assertTrue(t0.drawCalls.size() - off0.drawCalls.size() <= 64, "Wave uses at most 64 cheap g.fill bands");
        } finally {
            TitleNoir.setWaveEnabled(before);
        }
    }

    /** Outside the new layout's frame the title buttons draw the legacy way; inside, its buttons draw through it. */
    @Test void buttonsDrawTheNewWayOnlyDuringItsFrame() {
        var l = TitleNoir.layout(640, 360, 3, -1);
        var b = l.buttons().get(0);
        var legacy = new LadsGraphicsTest.MockGraphics();
        TitleScreenTheme.renderButton(legacy, b.x(), b.y(), b.width(), b.height(), "Singleplayer", "play", true, false, false, true, 0);
        assertTrue(legacy.drawCalls.contains("pushPose") && legacy.drawCalls.stream().anyMatch(c -> c.contains("Singleplayer")));
        var noir = new LadsGraphicsTest.MockGraphics();
        TitleNoir.beginFrame(l);
        try {
            TitleScreenTheme.renderButton(noir, b.x(), b.y(), b.width(), b.height(), "Singleplayer", "play", true, false, false, true, 0);
        } finally {
            TitleNoir.endFrame();
        }
        assertTrue(noir.drawCalls.stream().anyMatch(c -> c.contains("Singleplayer")));
        assertNotEquals(legacy.drawCalls, noir.drawCalls);
    }

    @Test void networkStatusReadsAServerListReply() throws Exception {
        try (var server = new java.net.ServerSocket(0)) {
            var replies = new Thread(() -> {
                try (var socket = server.accept()) {
                    socket.getInputStream().read(new byte[256]);
                    byte[] json = "{\"players\":{\"max\":100,\"online\":42}}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    var out = new java.io.DataOutputStream(socket.getOutputStream());
                    out.writeByte(json.length + 2);
                    out.writeByte(0);
                    out.writeByte(json.length);
                    out.write(json);
                    out.flush();
                } catch (java.io.IOException ignored) {}
            });
            replies.start();
            assertEquals(42, LadsNetworkStatus.query("127.0.0.1", server.getLocalPort(), 2000));
            replies.join(2000);
        }
    }

    @Test void networkStatusThrowsWhenNothingAnswers() throws Exception {
        int port;
        try (var server = new java.net.ServerSocket(0)) { port = server.getLocalPort(); }
        int closed = port;
        assertThrows(java.io.IOException.class, () -> LadsNetworkStatus.query("127.0.0.1", closed, 500));
    }

    @Test void backgroundWorkStaysBoundedAt4k() {
        var g = new LadsGraphicsTest.MockGraphics();
        TitleScreenTheme.renderBackground(g, TitleScreenTheme.layout(3840, 2160, 9), "LadsQA", "26.2", false, 1);
        assertTrue(g.drawCalls.size() < 1100, "Large windows must not create thousands of background fills");
    }

    @Test void noirNetworkStatusPillIsClickableAndClearOfSwitchAndBuild() {
        var g = new LadsGraphicsTest.MockGraphics();
        var l = TitleNoir.layout(640, 360, 12, 2);
        var switchBox = TitleNoir.renderStrip(g, l, "Arash", "26.3", true, "18 online");
        var pill = TitleNoir.lastNetworkPill();
        assertNotNull(pill);
        assertTrue(pill.x() >= switchBox.x() + switchBox.width(), "Network pill stays right of Switch");
        assertTrue(TitleNoir.clickNetworkPill(pill.x() + pill.width() / 2.0, pill.y() + pill.height() / 2.0));
        assertFalse(TitleNoir.clickNetworkPill(switchBox.x() + 2, switchBox.y() + 2));

        TitleNoir.renderStrip(g, l, "Arash", "26.3", true, null);
        assertNull(TitleNoir.lastNetworkPill());
        assertFalse(TitleNoir.clickNetworkPill(pill.x() + 2, pill.y() + 2));
    }

    @Test void settingsScreenKeepsImportClientAlongsideLadsNetworkButton() {
        com.thelads.core.client.gui.LadsSettingsScreen.requestOpenNetworkHub();
        var requested = new com.thelads.core.client.gui.LadsSettingsScreen();
        assertTrue(requested.networkHub().isOpen(), "requestOpenNetworkHub opens the hub modal on construction");
        requested.keyPressed(256, 0);
        assertFalse(requested.networkHub().isOpen(), "Esc closes the hub modal first");

        var screen = new com.thelads.core.client.gui.LadsSettingsScreen();
        var g = new LadsGraphicsTest.MockGraphics();
        screen.render(g, 0, 0);
        var importBtn = screen.controlBounds("client-import");
        var networkBtn = screen.controlBounds("lads-network");
        assertNotNull(importBtn, "Import from client button must remain visible");
        assertNotNull(networkBtn, "Lads Network button must render beside Import from client");
        assertTrue(importBtn.x() + importBtn.width() <= networkBtn.x(), "Import and Lads Network buttons must not overlap");

        screen.mouseClicked(networkBtn.x() + 4, networkBtn.y() + 4, 0);
        assertTrue(screen.networkHub().isOpen());
    }

    @Test void networkButtonDisplaysFullLabelAndServerIconWithoutTruncationInBothLayouts() {
        int serverCell = com.thelads.core.client.bridge.IconAtlas.cell("server");
        assertTrue(serverCell >= 0);
        var beforeSnap = LadsNetworkStatus.Snapshot.IDLE;
        try {
            for (var snap : new LadsNetworkStatus.Snapshot[] {
                LadsNetworkStatus.Snapshot.IDLE,
                new LadsNetworkStatus.Snapshot(true, false, 0, 100, 12L, "", System.currentTimeMillis()),
                new LadsNetworkStatus.Snapshot(true, false, 1234, 2000, 18L, "", System.currentTimeMillis())
            }) {
                LadsNetworkStatus.setCachedForTest(snap);
                // 1. Voxel Noir (default) layout across all supported canvas sizes
                for (int[] size : new int[][] {{320, 240}, {427, 240}, {480, 270}, {640, 360}, {960, 540}}) {
                    for (int count : new int[] {5, 7, 12, 13}) {
                        int netIdx = count == 5 ? 1 : 2;
                        var noir = TitleNoir.layout(size[0], size[1], count, netIdx);
                        var rect = noir.buttons().get(netIdx);
                        var icons = new java.util.ArrayList<Integer>();
                        var g = new LadsGraphicsTest.MockGraphics() {
                            @Override public boolean drawIcon(int cell, int x, int y, int s, int color) {
                                icons.add(cell);
                                return true;
                            }
                        };
                        TitleNoir.beginFrame(noir);
                        try {
                            TitleScreenTheme.renderButton(g, rect.x(), rect.y(), rect.width(), rect.height(),
                                com.thelads.core.client.LadsNetwork.TITLE_LABEL, "server", false, false, false, true, 0);
                        } finally {
                            TitleNoir.endFrame();
                        }
                        assertEquals(java.util.List.of(serverCell), icons, "Noir network button draws servers icon at " + size[0] + "x" + size[1]);
                        assertTrue(g.drawCalls.stream().anyMatch(c -> c.contains("The Lads Network") && !c.contains("...")),
                            "Noir network button shows full 'The Lads Network' without '...' at " + size[0] + "x" + size[1] + ": " + g.drawCalls);
                        assertFalse(g.drawCalls.stream().anyMatch(c -> c.contains("...")),
                            "Noir network button has zero ellipsis at " + size[0] + "x" + size[1] + ": " + g.drawCalls);
                    }
                }
                // 2. Legacy layout across all supported canvas sizes (including minimum 240x180 and 320x180)
                for (int[] size : new int[][] {{240, 180}, {320, 180}, {320, 240}, {480, 270}, {530, 300}, {640, 360}, {960, 540}}) {
                    for (int count : new int[] {5, 7, 12, 13, 16}) {
                        int netIdx = count == 5 ? 1 : 2;
                        var legacy = TitleScreenTheme.layout(size[0], size[1], count, netIdx);
                        assertEquals(count, legacy.buttons().size());
                        for (int i = 0; i < legacy.buttons().size(); i++) {
                            var a = legacy.buttons().get(i);
                            assertTrue(a.y() + a.height() <= size[1] - 29,
                                "Legacy button " + i + " of " + count + " stays above account footer at " + size[0] + "x" + size[1]);
                        }
                        assertApart(legacy.buttons(), size[0], size[1], "legacy " + count + " at " + size[0] + "x" + size[1]);
                        var rect = legacy.buttons().get(netIdx);
                        assertEquals(legacy.menuWidth(), rect.width(), "Legacy network button spans full menuWidth");
                        var icons = new java.util.ArrayList<Integer>();
                        var g = new LadsGraphicsTest.MockGraphics() {
                            @Override public boolean drawIcon(int cell, int x, int y, int s, int color) {
                                icons.add(cell);
                                return true;
                            }
                        };
                        TitleScreenTheme.renderButton(g, rect.x(), rect.y(), rect.width(), rect.height(),
                            com.thelads.core.client.LadsNetwork.TITLE_LABEL, "server", false, false, false, true, 0);
                        assertEquals(java.util.List.of(serverCell), icons, "Legacy network button draws servers icon at " + size[0] + "x" + size[1]);
                        assertTrue(g.drawCalls.stream().anyMatch(c -> c.contains("The Lads Network") && !c.contains("...")),
                            "Legacy network button shows full 'The Lads Network' without '...' at " + size[0] + "x" + size[1] + ": " + g.drawCalls);
                        assertFalse(g.drawCalls.stream().anyMatch(c -> c.contains("...")),
                            "Legacy network button has zero ellipsis at " + size[0] + "x" + size[1] + ": " + g.drawCalls);
                    }
                }
            }
        } finally {
            LadsNetworkStatus.setCachedForTest(beforeSnap);
        }
    }
}
