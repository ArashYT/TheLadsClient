package com.thelads.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thelads.core.client.util.ClientPaths;
import com.thelads.core.client.util.SkinFetcher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class M3ChallengerTest {

    static class MockWidget {
        String name;
        int x, y, width, height;

        MockWidget(String name, int x, int y, int width, int height) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        boolean intersects(int ox, int oy, int ow, int oh) {
            return this.x < ox + ow && this.x + this.width > ox &&
                   this.y < oy + oh && this.y + this.height > oy;
        }

        int getOverlapArea(int ox, int oy, int ow, int oh) {
            int xOverlap = Math.max(0, Math.min(this.x + this.width, ox + ow) - Math.max(this.x, ox));
            int yOverlap = Math.max(0, Math.min(this.y + this.height, oy + oh) - Math.max(this.y, oy));
            return xOverlap * yOverlap;
        }
    }

    /**
     * Empirical Challenge: Current TitleScreenMixin implementation causes a 2600 sq px
     * collision between the Switch Account button and the Account Card on standard resolutions.
     */
    @Test
    @DisplayName("Adversarial Test: TitleScreen current button shifting causes Account Card collision")
    public void testTitleScreenCurrentImplementationCausesCollision() {
        int[] testHeights = {540, 360, 270}; // 1080p GUI Scale 2, 3, 4
        int screenWidth = 800;

        for (int screenHeight : testHeights) {
            List<MockWidget> children = new ArrayList<>();

            // Simulated vanilla widgets
            int optionsY = screenHeight / 4 + 120;
            children.add(new MockWidget("Singleplayer", screenWidth / 2 - 100, screenHeight / 4 + 48, 200, 20));
            children.add(new MockWidget("Multiplayer", screenWidth / 2 - 100, screenHeight / 4 + 72, 200, 20));
            children.add(new MockWidget("Realms", screenWidth / 2 - 100, screenHeight / 4 + 96, 200, 20));
            children.add(new MockWidget("Options", screenWidth / 2 - 100, optionsY, 98, 20));
            children.add(new MockWidget("Quit", screenWidth / 2 + 2, optionsY, 98, 20));
            children.add(new MockWidget("Copyright", screenWidth - 100, screenHeight - 20, 90, 10));

            // --- TitleScreenMixin current sequence ---
            int CARD_W = 130;
            int CARD_H = 46;
            int CARD_MARGIN = 10;

            // 1. Switch Account button added BEFORE the shift loop (TitleScreenMixin lines 37-43)
            int cardY = screenHeight - CARD_H - CARD_MARGIN - 22;
            MockWidget switchAccountBtn = new MockWidget("Switch Account", CARD_MARGIN, cardY, CARD_W, 20);
            children.add(switchAccountBtn);

            // 2. Locate optionsY
            int foundOptionsY = -1;
            for (MockWidget widget : children) {
                if ("Options".equalsIgnoreCase(widget.name)) {
                    foundOptionsY = widget.y;
                    break;
                }
            }
            assertEquals(optionsY, foundOptionsY);

            // 3. Shift loop in TitleScreenMixin (lines 68-75)
            for (MockWidget widget : children) {
                int y = widget.y;
                if (y >= foundOptionsY && y < screenHeight - 40) {
                    widget.y = y + 24;
                }
            }

            // 4. Rendered Account Card geometry (TitleScreenMixin line 90)
            int expectedCardX = CARD_MARGIN;
            int expectedCardY = screenHeight - CARD_H - CARD_MARGIN; // screenHeight - 56

            // Empirical verification:
            // Switch Account button Y moved from (screenHeight - 78) to (screenHeight - 54),
            // directly inside the card bounding box [screenHeight - 56, screenHeight - 10]!
            assertTrue(switchAccountBtn.intersects(expectedCardX, expectedCardY, CARD_W, CARD_H),
                "Switch Account button must intersect with Account Card under current flawed implementation");
            assertEquals(CARD_W * 20, switchAccountBtn.getOverlapArea(expectedCardX, expectedCardY, CARD_W, CARD_H),
                "Switch Account button is 100% swallowed inside the Account Card!");
        }
    }

    /**
     * Verification of the recommended fix:
     * When Switch Account button is added AFTER the shift loop, or when the shift loop only
     * targets the center vertical column (e.g. x >= width/2 - 120), zero collision occurs.
     */
    @Test
    @DisplayName("Verification: Fixed TitleScreen button shifting produces zero collisions")
    public void testTitleScreenFixedGeometryProducesZeroCollisions() {
        int[] testHeights = {540, 360, 270, 240};
        int screenWidth = 800;

        for (int screenHeight : testHeights) {
            List<MockWidget> children = new ArrayList<>();

            int optionsY = screenHeight / 4 + 120;
            MockWidget optionsBtn = new MockWidget("Options", screenWidth / 2 - 100, optionsY, 98, 20);
            MockWidget quitBtn = new MockWidget("Quit", screenWidth / 2 + 2, optionsY, 98, 20);
            children.add(optionsBtn);
            children.add(quitBtn);

            // Corrected sequence:
            // 1. Shift vanilla center buttons first (OR filter shift to center column)
            for (MockWidget widget : children) {
                int y = widget.y;
                if (y >= optionsY && y < screenHeight - 40 && Math.abs(widget.x - (screenWidth / 2)) < 150) {
                    widget.y = y + 24;
                }
            }

            // 2. Add Lads Settings at optionsY
            MockWidget ladsSettings = new MockWidget("Lads Settings", screenWidth / 2 - 100, optionsY, 200, 20);
            children.add(ladsSettings);

            // 3. Add Switch Account button AFTER the shift loop
            int CARD_W = 130;
            int CARD_H = 46;
            int CARD_MARGIN = 10;
            int cardY = screenHeight - CARD_H - CARD_MARGIN - 22;
            MockWidget switchAccountBtn = new MockWidget("Switch Account", CARD_MARGIN, cardY, CARD_W, 20);
            children.add(switchAccountBtn);

            // Account Card
            int cardX = CARD_MARGIN;
            int actualCardY = screenHeight - CARD_H - CARD_MARGIN;

            // Assertions:
            // 1. No collision between Switch Account button and Account Card
            assertFalse(switchAccountBtn.intersects(cardX, actualCardY, CARD_W, CARD_H),
                "Switch Account button must NOT collide with Account Card");

            // 2. 2px clean visual gap between button bottom and card top
            int buttonBottom = switchAccountBtn.y + switchAccountBtn.height;
            assertEquals(2, actualCardY - buttonBottom,
                "Clean 2px margin must exist between Switch Account button and Account Card");

            // 3. Lads Settings button is directly above Options
            assertEquals(optionsY, ladsSettings.y);
            assertEquals(optionsY + 24, optionsBtn.y);
            assertEquals(24, optionsBtn.y - ladsSettings.y,
                "Options button must be positioned 24px below Lads Settings");
        }
    }

    /**
     * Test SkinFetcher Tier 1: Local custom skin (%APPDATA%/.theladsclient/skin.png)
     */
    @Test
    @DisplayName("SkinFetcher: Tier 1 Custom Skin local resolution")
    public void testSkinFetcherTier1CustomSkin(@TempDir Path tempDir) throws Exception {
        ClientPaths.setBaseDir(tempDir);
        Path skinFile = ClientPaths.getSkinFile().toPath();
        Files.write(skinFile, new byte[]{1, 2, 3, 4});

        CompletableFuture<Path> future = SkinFetcher.getOrFetchSkin("any-uuid", "any-user");
        assertNotNull(future);
        Path result = future.get(2, TimeUnit.SECONDS);
        assertEquals(skinFile, result, "Should return local custom skin immediately");
    }

    /**
     * Test SkinFetcher Tier 2: Local skin disk cache
     */
    @Test
    @DisplayName("SkinFetcher: Tier 2 Disk Cache resolution")
    public void testSkinFetcherTier2DiskCache(@TempDir Path tempDir) throws Exception {
        ClientPaths.setBaseDir(tempDir);
        String uuid = "e7b8f9a0-1234-5678-9abc-def012345678";
        String cleanKey = uuid.replace("-", "").toLowerCase();
        Path cacheDir = ClientPaths.getSkinCacheDir();
        Path cachedSkin = cacheDir.resolve(cleanKey + ".png");
        Files.createDirectories(cacheDir);
        Files.write(cachedSkin, new byte[]{10, 20, 30});

        CompletableFuture<Path> future = SkinFetcher.getOrFetchSkin(uuid, "Steve");
        assertNotNull(future);
        Path result = future.get(2, TimeUnit.SECONDS);
        assertEquals(cachedSkin, result, "Should return cached skin from disk");
    }

    /**
     * Test SkinFetcher Tier 2 edge case: 0-byte corrupt cache file must not be returned
     */
    @Test
    @DisplayName("SkinFetcher: Corrupt 0-byte cache file is rejected")
    public void testSkinFetcherZeroByteCacheRejected(@TempDir Path tempDir) throws Exception {
        ClientPaths.setBaseDir(tempDir);
        String uuid = "ffffffff-ffff-ffff-ffff-ffffffffffff";
        String cleanKey = uuid.replace("-", "").toLowerCase();
        Path cacheDir = ClientPaths.getSkinCacheDir();
        Path cachedSkin = cacheDir.resolve(cleanKey + ".png");
        Files.createDirectories(cacheDir);
        Files.createFile(cachedSkin);

        CompletableFuture<Path> future = SkinFetcher.getOrFetchSkin(uuid, "NonExistentUserXYZ");
        assertNotNull(future);
        Path result = future.get(10, TimeUnit.SECONDS);
        assertNotEquals(cachedSkin, result, "0-byte cache file must never be accepted as a valid skin");
    }

    /**
     * Test SkinFetcher Offline Resilience: Network failure / timeout completes gracefully with null
     */
    @Test
    @DisplayName("SkinFetcher: Offline resilience and null safety on network failure")
    public void testSkinFetcherOfflineResilience(@TempDir Path tempDir) throws Exception {
        ClientPaths.setBaseDir(tempDir);

        // Test with null and blank inputs
        CompletableFuture<Path> nullFuture = SkinFetcher.getOrFetchSkin(null, null);
        assertNotNull(nullFuture);
        Path nullResult = nullFuture.get(5, TimeUnit.SECONDS);
        assertNull(nullResult, "Null uuid/username should safely return null without exception");

        // Test with unreachable/fake UUID in offline mode
        CompletableFuture<Path> fakeFuture = SkinFetcher.getOrFetchSkin("00000000-0000-0000-0000-000000000000", "OfflineGhostPlayer");
        assertNotNull(fakeFuture);
        Path fakeResult = fakeFuture.get(15, TimeUnit.SECONDS);
        assertNull(fakeResult, "Unresolvable player offline should safely return null");
    }

    /**
     * Test Mixin JSON configs: 1.21.11 injections fail visibly (defaultRequire = 1, since 1.3.5); the 26.2 main config keeps 0
     */
    @Test
    @DisplayName("Mixin Configuration: 1.21.11 requires its injections, 26.2 main config keeps defaultRequire = 0")
    public void testMixinDefaultRequire() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        Path v121Mixins = root.resolve("v1_21_11/src/main/resources/theladscore.v1_21_11.mixins.json");
        Path v26Mixins = root.resolve("v26_2/src/main/resources/theladscore.v26_2.mixins.json");

        assertTrue(Files.exists(v121Mixins), "1.21.11 mixin config must exist");
        assertTrue(Files.exists(v26Mixins), "26.2 mixin config must exist");

        JsonObject v121Json = JsonParser.parseString(Files.readString(v121Mixins)).getAsJsonObject();
        JsonObject v26Json = JsonParser.parseString(Files.readString(v26Mixins)).getAsJsonObject();

        assertTrue(v121Json.has("injectors") && v121Json.getAsJsonObject("injectors").has("defaultRequire"),
            "1.21.11 mixin config must define injectors.defaultRequire");
        assertEquals(1, v121Json.getAsJsonObject("injectors").get("defaultRequire").getAsInt(),
            "1.21.11 injectors.defaultRequire must be 1 so a failed injection is not silent");

        assertTrue(v26Json.has("injectors") && v26Json.getAsJsonObject("injectors").has("defaultRequire"),
            "26.2 mixin config must define injectors.defaultRequire");
        assertEquals(0, v26Json.getAsJsonObject("injectors").get("defaultRequire").getAsInt(),
            "26.2 injectors.defaultRequire must be 0");

        // Verify the TitleScreenMixin.java hooks in both versions are required rather than silently optional
        Path v121TitleMixin = root.resolve("v1_21_11/src/main/java/com/thelads/core/v1_21_11/mixin/TitleScreenMixin.java");
        Path v26TitleMixin = root.resolve("v26_2/src/main/java/com/thelads/core/v26_2/mixin/TitleScreenMixin.java");

        String v121Title = Files.readString(v121TitleMixin);
        assertTrue(v121Title.contains("require = 1") && !v121Title.contains("require = 0"), "v1_21_11 TitleScreenMixin must not opt out of defaultRequire");
        assertTrue(Files.readString(v26TitleMixin).contains("require = 1"), "26.x title replacement must fail visibly if its required hook no longer matches");
    }
}
