package com.thelads.core.client;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LinkPreviewTest {
    @Test
    void urlExtraction() {
        assertNull(LinkPreview.extractUrl(null));
        assertNull(LinkPreview.extractUrl(""));
        assertNull(LinkPreview.extractUrl("hello world with no links"));
        assertEquals("https://example.com/image.png", LinkPreview.extractUrl("Look at this: https://example.com/image.png it is cool!"));
        assertEquals("http://test.org/path?q=1#frag", LinkPreview.extractUrl("link http://test.org/path?q=1#frag"));
    }

    @Test
    void isLikelyImage() {
        assertTrue(LinkPreview.isLikelyImage("https://example.com/cat.png"));
        assertTrue(LinkPreview.isLikelyImage("https://example.com/photo.jpg?size=large"));
        assertTrue(LinkPreview.isLikelyImage("https://example.com/art.jpeg"));
        assertTrue(LinkPreview.isLikelyImage("https://example.com/anim.gif"));
        assertTrue(LinkPreview.isLikelyImage("https://example.com/pic.webp"));
        assertTrue(LinkPreview.isLikelyImage("https://i.imgur.com/abc1234"));
        assertTrue(LinkPreview.isLikelyImage("https://cdn.discordapp.com/attachments/123/456/test.png"));
        assertFalse(LinkPreview.isLikelyImage("https://example.com/about.html"));
        assertFalse(LinkPreview.isLikelyImage("https://github.com/project"));
        assertFalse(LinkPreview.isLikelyImage(null));
    }

    @Test
    void thumbnailScaling() {
        BufferedImage original = new BufferedImage(320, 200, BufferedImage.TYPE_INT_ARGB);
        BufferedImage thumb = LinkPreview.createThumbnail(original, LinkPreview.THUMB_MAX_WIDTH, LinkPreview.THUMB_MAX_HEIGHT);
        assertNotNull(thumb);
        assertTrue(thumb.getWidth() <= LinkPreview.THUMB_MAX_WIDTH, "width clamped");
        assertTrue(thumb.getHeight() <= LinkPreview.THUMB_MAX_HEIGHT, "height clamped");
        assertEquals(160, thumb.getWidth());
        assertEquals(100, thumb.getHeight());

        BufferedImage small = new BufferedImage(50, 40, BufferedImage.TYPE_INT_ARGB);
        BufferedImage same = LinkPreview.createThumbnail(small, LinkPreview.THUMB_MAX_WIDTH, LinkPreview.THUMB_MAX_HEIGHT);
        assertSame(small, same, "images smaller than max bounds are kept as-is");
    }

    @Test
    void nullAndEmptyRequests() {
        assertNull(LinkPreview.request(null, null));
        assertNull(LinkPreview.request("   ", null));
        assertNull(LinkPreview.getEntry("https://nonexistent-url-test.org"));
        assertFalse(LinkPreview.hasPreview("https://nonexistent-url-test.org"));
    }
}
