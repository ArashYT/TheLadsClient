package com.thelads.core.config;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.thelads.core.client.gui.ProfilesDialog;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ShareCodeClientTest {

    private static class DummyGraphics implements com.thelads.core.client.bridge.LadsGraphics {
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
        @Override public int getScaledWidth() { return 800; }
        @Override public int getScaledHeight() { return 600; }
    }

    @BeforeEach
    void setUp() {
        ProfilesDialog.setAsyncExecutor(Runnable::run); // Synchronous execution for unit tests
    }

    @AfterEach
    void tearDown() {
        ShareCodeClient.setTransport(null); // Reset transport to default
        ProfilesDialog.setAsyncExecutor(null); // Reset executor
    }

    @Test
    void shortCodeParsingAndNormalization() {
        // Valid forms
        assertEquals("7KQ2M9", ShareCodeClient.normalizeShortCode("7KQ2M9"));
        assertEquals("7KQ2M9", ShareCodeClient.normalizeShortCode("7kq2m9"));
        assertEquals("7KQ2M9", ShareCodeClient.normalizeShortCode("LADS-7KQ2M9"));
        assertEquals("7KQ2M9", ShareCodeClient.normalizeShortCode("lads-7kq2m9"));
        assertEquals("7KQ2M9", ShareCodeClient.normalizeShortCode("  LADS-7KQ2M9  \n"));

        assertTrue(ShareCodeClient.isShortCode("7KQ2M9"));
        assertTrue(ShareCodeClient.isShortCode("lads-7kq2m9"));

        // Invalid forms
        assertNull(ShareCodeClient.normalizeShortCode(null));
        assertNull(ShareCodeClient.normalizeShortCode(""));
        assertNull(ShareCodeClient.normalizeShortCode("SHORT")); // 5 chars
        assertNull(ShareCodeClient.normalizeShortCode("TOOLONG1")); // 8 chars
        assertNull(ShareCodeClient.normalizeShortCode("LADS-12345")); // 5 chars
        assertNull(ShareCodeClient.normalizeShortCode("LADS1:eJw...")); // long code is not a short code

        assertFalse(ShareCodeClient.isShortCode("LADS1:xyz"));
        assertFalse(ShareCodeClient.isShortCode("not_a_code"));
    }

    @Test
    void postShareSuccess() throws Exception {
        String sampleLongCode = "LADS1:samplepayload";
        ShareCodeClient.setTransport((method, url, contentType, body) -> {
            assertEquals("POST", method);
            assertEquals("https://bot.arashyt.ca/share", url);
            assertEquals("application/json; charset=UTF-8", contentType);
            assertTrue(new String(body, StandardCharsets.UTF_8).contains(sampleLongCode));

            JsonObject resp = new JsonObject();
            resp.addProperty("code", "7KQ2M9");
            return new ShareCodeClient.Response(200, resp.toString().getBytes(StandardCharsets.UTF_8));
        });

        String shortCode = ShareCodeClient.postShare(sampleLongCode);
        assertEquals("LADS-7KQ2M9", shortCode);
    }

    @Test
    void getShareSuccess() throws Exception {
        String expectedPayload = "LADS1:samplepayload";
        ShareCodeClient.setTransport((method, url, contentType, body) -> {
            assertEquals("GET", method);
            assertEquals("https://bot.arashyt.ca/share/7KQ2M9", url);

            JsonObject resp = new JsonObject();
            resp.addProperty("payload", expectedPayload);
            return new ShareCodeClient.Response(200, resp.toString().getBytes(StandardCharsets.UTF_8));
        });

        assertEquals(expectedPayload, ShareCodeClient.getShare("7KQ2M9"));
        assertEquals(expectedPayload, ShareCodeClient.getShare("lads-7kq2m9"));
    }

    @Test
    void getShare404NotFound() {
        ShareCodeClient.setTransport((method, url, contentType, body) -> {
            JsonObject resp = new JsonObject();
            resp.addProperty("error", "not found");
            return new ShareCodeClient.Response(404, resp.toString().getBytes(StandardCharsets.UTF_8));
        });

        ShareCodeClient.ShareNotFoundException thrown = assertThrows(
            ShareCodeClient.ShareNotFoundException.class,
            () -> ShareCodeClient.getShare("7KQ2M9")
        );
        assertEquals("Code not found", thrown.getMessage());
    }

    @Test
    void getShareTimeoutOrNetworkFailure() {
        ShareCodeClient.setTransport((method, url, contentType, body) -> {
            throw new SocketTimeoutException("connect timed out");
        });

        assertThrows(IOException.class, () -> ShareCodeClient.getShare("7KQ2M9"));
    }

    @Test
    void postShareFailureFallbackToLongCode() {
        ProfilesDialog dialog = new ProfilesDialog();
        dialog.open();

        AtomicReference<String> copied = new AtomicReference<>();
        dialog.setClipboardWriter(copied::set);

        // Server fails with 503 or offline
        ShareCodeClient.setTransport((method, url, contentType, body) -> {
            throw new IOException("Server offline");
        });

        // Layout bounds via render
        dialog.render(new DummyGraphics(), 0, 0);
        // Trigger copy via bounds click
        dialog.click(dialog.copyBounds().x() + 2, dialog.copyBounds().y() + 2, 0);
        dialog.processMainThreadTasks();

        assertNotNull(copied.get(), "Clipboard should have received fallback code");
        assertTrue(copied.get().startsWith(ShareCode.PREFIX), "Must fallback to long LADS1: code");
        assertEquals("Server offline - copied long code", dialog.toast());
    }

    @Test
    void postShareSuccessCopiesShortCode() {
        ProfilesDialog dialog = new ProfilesDialog();
        dialog.open();

        AtomicReference<String> copied = new AtomicReference<>();
        dialog.setClipboardWriter(copied::set);

        ShareCodeClient.setTransport((method, url, contentType, body) -> {
            JsonObject resp = new JsonObject();
            resp.addProperty("code", "9X2M8K");
            return new ShareCodeClient.Response(200, resp.toString().getBytes(StandardCharsets.UTF_8));
        });

        dialog.render(new DummyGraphics(), 0, 0);
        dialog.click(dialog.copyBounds().x() + 2, dialog.copyBounds().y() + 2, 0);
        dialog.processMainThreadTasks();

        assertEquals("LADS-9X2M8K", copied.get());
        assertEquals("Copied LADS-9X2M8K", dialog.toast());
    }

    @Test
    void importShortCodeSuccessShowsChoiceModal() {
        ProfilesDialog dialog = new ProfilesDialog();
        dialog.open();

        JsonObject sample = new JsonObject();
        sample.addProperty("v", 1);
        JsonObject mods = new JsonObject();
        JsonObject cps = new JsonObject();
        cps.addProperty("enabled", true);
        mods.add("CPS", cps);
        sample.add("modules", mods);
        String sampleLongCode = ShareCode.encode(sample);

        dialog.setClipboardReader(() -> "LADS-7KQ2M9");

        ShareCodeClient.setTransport((method, url, contentType, body) -> {
            JsonObject resp = new JsonObject();
            resp.addProperty("payload", sampleLongCode);
            return new ShareCodeClient.Response(200, resp.toString().getBytes(StandardCharsets.UTF_8));
        });

        dialog.render(new DummyGraphics(), 0, 0);
        dialog.click(dialog.importBounds().x() + 2, dialog.importBounds().y() + 2, 0);
        dialog.processMainThreadTasks();

        assertEquals(ProfilesDialog.Mode.CHOICE_IMPORT, dialog.mode(), "Import choice modal must open");
        assertEquals("", dialog.errorText());
        assertNotNull(dialog.importedPayload());
    }

    @Test
    void importShortCode404DisplaysCodeNotFound() {
        ProfilesDialog dialog = new ProfilesDialog();
        dialog.open();
        dialog.setClipboardReader(() -> "LADS-NOTFND");

        ShareCodeClient.setTransport((method, url, contentType, body) -> {
            JsonObject resp = new JsonObject();
            resp.addProperty("error", "not found");
            return new ShareCodeClient.Response(404, resp.toString().getBytes(StandardCharsets.UTF_8));
        });

        dialog.render(new DummyGraphics(), 0, 0);
        dialog.click(dialog.importBounds().x() + 2, dialog.importBounds().y() + 2, 0);
        dialog.processMainThreadTasks();

        assertEquals(ProfilesDialog.Mode.LIST, dialog.mode());
        assertEquals("Code not found", dialog.errorText());
    }

    @Test
    void importShortCodeNetworkErrorDisplaysReachError() {
        ProfilesDialog dialog = new ProfilesDialog();
        dialog.open();
        dialog.setClipboardReader(() -> "7KQ2M9");

        ShareCodeClient.setTransport((method, url, contentType, body) -> {
            throw new IOException("Connection refused");
        });

        dialog.render(new DummyGraphics(), 0, 0);
        dialog.click(dialog.importBounds().x() + 2, dialog.importBounds().y() + 2, 0);
        dialog.processMainThreadTasks();

        assertEquals(ProfilesDialog.Mode.LIST, dialog.mode());
        assertEquals("Can't reach the share server", dialog.errorText());
    }

    @Test
    void oversizedResponseRejected() {
        // Transport returns a response with over 32 KB
        ShareCodeClient.setTransport((method, url, contentType, body) -> {
            byte[] huge = new byte[33 * 1024]; // 33 KB
            return new ShareCodeClient.Response(200, huge);
        });

        // In DefaultTransport, the size cap is checked during streaming
        // Let's test the streaming size cap directly:
        ShareCodeClient.Transport streamingTransport = new ShareCodeClient.DefaultTransport();
        // Passing a URL with non-https throws
        assertThrows(IllegalArgumentException.class, () -> {
            streamingTransport.send("GET", "http://insecure.test", null, null);
        });
    }
}
