package com.thelads.core.client.hud;

import com.thelads.core.client.HypixelSafetyManager;
import com.thelads.core.client.bridge.DefaultGameBridge;
import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.bridge.LadsGraphics;
import com.thelads.core.config.Module;
import com.thelads.core.config.ModuleManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ServerRulesNoticeTest {
    private final List<String> sentCommands = new ArrayList<>();
    private final List<String> openedChat = new ArrayList<>();
    private final List<String> openedUrls = new ArrayList<>();

    private static class TestGameBridge extends DefaultGameBridge {
        private final List<String> commands;
        private final List<String> chat;
        private final List<String> urls;

        TestGameBridge(List<String> commands, List<String> chat, List<String> urls) {
            this.commands = commands;
            this.chat = chat;
            this.urls = urls;
        }

        @Override public boolean isIngame() { return true; }
        @Override public String getServerAddress() { return "play.thelads.net"; }

        @Override
        public void sendChatLine(String text) {
            commands.add(text);
        }

        @Override
        public void openChatWithText(String text) {
            chat.add(text);
        }

        @Override
        public void openUrl(String url) {
            urls.add(url);
        }
    }

    @BeforeEach
    void setUp() {
        sentCommands.clear();
        openedChat.clear();
        openedUrls.clear();
        LadsGameBridge.set(new TestGameBridge(sentCommands, openedChat, openedUrls));

        Module m = ModuleManager.getInstance().getModule("Server Notification Actions");
        if (m != null) m.setEnabled(true);
        HypixelSafetyManager.bypass("Server Notification Actions");
        ServerRulesNotice.clearPolicyToast();
    }

    @AfterEach
    void tearDown() {
        ServerRulesNotice.clearPolicyToast();
        ModuleManager.getInstance().resetToDefaults();
    }

    @Test
    void testToastActionTypesAndParsing() {
        ToastAction cmd = ToastAction.runCommand("Rules", "/rules");
        assertEquals("Rules", cmd.getLabel());
        assertEquals(ToastAction.Type.RUN_COMMAND, cmd.getType());
        assertEquals("/rules", cmd.getPayload());

        ToastAction suggest = ToastAction.suggestCommand("Help", "/help");
        assertEquals(ToastAction.Type.SUGGEST_COMMAND, suggest.getType());

        ToastAction url = ToastAction.openUrl("Store", "https://store.thelads.net");
        assertEquals(ToastAction.Type.OPEN_URL, url.getType());

        ToastAction parsedUrl = ToastAction.parse("Web", "url", "https://thelads.net");
        assertEquals(ToastAction.Type.OPEN_URL, parsedUrl.getType());

        ToastAction parsedCmd = ToastAction.parse("Discord", "command", "/discord");
        assertEquals(ToastAction.Type.RUN_COMMAND, parsedCmd.getType());

        ToastAction parsedSuggest = ToastAction.parse("Report", "suggest", "/report player");
        assertEquals(ToastAction.Type.SUGGEST_COMMAND, parsedSuggest.getType());
    }

    @Test
    void testSafeUrlValidation() {
        assertTrue(LadsGameBridge.isSafeUrl("https://store.thelads.net"));
        assertTrue(LadsGameBridge.isSafeUrl("http://example.com/test"));
        assertTrue(LadsGameBridge.isSafeUrl("https://discord.gg/thelads"));

        assertFalse(LadsGameBridge.isSafeUrl("file:///etc/passwd"));
        assertFalse(LadsGameBridge.isSafeUrl("javascript:alert(1)"));
        assertFalse(LadsGameBridge.isSafeUrl("cmd.exe"));
        assertFalse(LadsGameBridge.isSafeUrl(""));
        assertFalse(LadsGameBridge.isSafeUrl(null));
    }

    @Test
    void testToastActionExecution() {
        ToastAction cmd = ToastAction.runCommand("Rules", "rules");
        ServerRulesNotice.executeAction(cmd);
        assertEquals(1, sentCommands.size());
        assertEquals("/rules", sentCommands.get(0));

        ToastAction suggest = ToastAction.suggestCommand("Help", "/help me");
        ServerRulesNotice.executeAction(suggest);
        assertEquals(1, openedChat.size());
        assertEquals("/help me", openedChat.get(0));

        ToastAction url = ToastAction.openUrl("Store", "https://store.thelads.net");
        ServerRulesNotice.executeAction(url);
        assertEquals(1, openedUrls.size());
        assertEquals("https://store.thelads.net", openedUrls.get(0));

        // Unsafe URL rejected
        ToastAction unsafe = ToastAction.openUrl("Hack", "file:///C:/Windows");
        ServerRulesNotice.executeAction(unsafe);
        assertEquals(1, openedUrls.size(), "Unsafe URL must not be opened");
    }

    @Test
    void testActionHitTestingAndClicking() {
        ToastAction action1 = ToastAction.runCommand("Store", "/store");
        ToastAction action2 = ToastAction.openUrl("Discord", "https://discord.gg/thelads");

        ServerRulesNotice.showPolicyToast("Lads SMP Rules", "Click an action", List.of(action1, action2));
        assertTrue(ServerRulesNotice.isToastShowing());
        assertEquals(2, ServerRulesNotice.getToastActions().size());

        // Simulating render pass populating hitboxes
        ServerRulesNotice.render(new MockGraphics(800, 600));
        List<ServerRulesNotice.ActionHitbox> hitboxes = ServerRulesNotice.getActiveHitboxes();
        assertEquals(2, hitboxes.size());

        ServerRulesNotice.ActionHitbox btn1 = hitboxes.get(0);
        // Click inside btn1
        boolean clicked = ServerRulesNotice.mouseClicked(btn1.x + 2, btn1.y + 2, 0);
        assertTrue(clicked, "Click inside btn1 should be handled");
        assertEquals(1, sentCommands.size());
        assertEquals("/store", sentCommands.get(0));
        assertFalse(ServerRulesNotice.isToastShowing(), "Toast should be cleared after click");

        // Clicking outside returns false
        assertFalse(ServerRulesNotice.mouseClicked(10, 10, 0));
    }

    @Test
    void testGatingDisablesActions() {
        Module m = ModuleManager.getInstance().getModule("Server Notification Actions");
        m.setEnabled(false);

        ToastAction action1 = ToastAction.runCommand("Store", "/store");
        ServerRulesNotice.showPolicyToast("Lads SMP Rules", "Notice", List.of(action1));

        ServerRulesNotice.render(new MockGraphics(800, 600));
        assertEquals(0, ServerRulesNotice.getActiveHitboxes().size(), "Disabled actions must not produce hitboxes");

        assertFalse(ServerRulesNotice.mouseClicked(100, 100, 0));
    }

    private static class MockGraphics implements LadsGraphics {
        private final int width, height;

        MockGraphics(int width, int height) {
            this.width = width;
            this.height = height;
        }

        @Override public void fill(int minX, int minY, int maxX, int maxY, int color) {}
        @Override public void drawText(String text, int x, int y, int color, boolean shadow) {}
        @Override public void drawCenteredText(String text, int centerX, int y, int color, boolean shadow) {}
        @Override public int textWidth(String text) { return text != null ? text.length() * 6 : 0; }
        @Override public int fontHeight() { return 9; }
        @Override public void pushPose() {}
        @Override public void popPose() {}
        @Override public void translate(float x, float y) {}
        @Override public void scale(float sx, float sy) {}
        @Override public void enableScissor(int minX, int minY, int maxX, int maxY) {}
        @Override public void disableScissor() {}
        @Override public void blit(String texture, int x, int y, int u, int v, int width, int height) {}
        @Override public void drawHead(String username, String uuid, int x, int y, int size) {}
        @Override public int getScaledWidth() { return width; }
        @Override public int getScaledHeight() { return height; }
    }
}
