package com.thelads.core.client;

import java.awt.image.BufferedImage;
import java.net.InetAddress;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LinkPreviewTest {
    @Test
    void defaultDisabled() {
        assertFalse(LinkPreview.isEnabled(), "Link preview module must default to disabled");
    }

    @Test
    void urlExtraction() {
        assertNull(LinkPreview.extractUrl(null));
        assertNull(LinkPreview.extractUrl(""));
        assertNull(LinkPreview.extractUrl("hello world with no links"));
        assertEquals("https://example.com/image.png", LinkPreview.extractUrl("Look at this: https://example.com/image.png it is cool!"));
        assertNull(LinkPreview.extractUrl("link http://test.org/path?q=1#frag"), "http:// must not be extracted (https only)");
    }

    @Test
    void httpsOnlyEnforcement() {
        LinkPreview.Entry entry = LinkPreview.request("http://insecure.example.com/test.png", null);
        assertNotNull(entry);
        assertEquals(LinkPreview.Status.NOT_ALLOWED, entry.getStatus(), "Non-HTTPS requests must be NOT_ALLOWED");
    }

    @Test
    void privateAndLoopbackIpCheck() throws Exception {
        assertFalse(LinkPreview.isSafePublicAddress(InetAddress.getByName("127.0.0.1")), "127.0.0.1 is loopback");
        assertFalse(LinkPreview.isSafePublicAddress(InetAddress.getByName("localhost")), "localhost is loopback");
        assertFalse(LinkPreview.isSafePublicAddress(InetAddress.getByName("10.0.0.1")), "10.0.0.0/8 is private");
        assertFalse(LinkPreview.isSafePublicAddress(InetAddress.getByName("192.168.1.100")), "192.168.0.0/16 is private");
        assertFalse(LinkPreview.isSafePublicAddress(InetAddress.getByName("172.16.5.1")), "172.16.0.0/12 is private");
        assertFalse(LinkPreview.isSafePublicAddress(InetAddress.getByName("169.254.1.1")), "169.254.0.0/16 is link-local");
        assertFalse(LinkPreview.isSafePublicAddress(InetAddress.getByName("100.64.0.1")), "100.64.0.0/10 is CGNAT");
        assertFalse(LinkPreview.isSafePublicAddress(InetAddress.getByName("0.0.0.0")), "0.0.0.0 is any-local");
        assertFalse(LinkPreview.isSafePublicAddress(InetAddress.getByName("::1")), "::1 is IPv6 loopback");
        assertFalse(LinkPreview.isSafePublicAddress(InetAddress.getByName("64:ff9b::192.168.1.1")), "NAT64 wrapping private IPv4 must be blocked");
        assertFalse(LinkPreview.isSafePublicAddress(InetAddress.getByName("2002:c0a8:0101::")), "6to4 wrapping private IPv4 must be blocked");

        assertTrue(LinkPreview.isSafePublicAddress(InetAddress.getByName("8.8.8.8")), "8.8.8.8 is public");
        assertTrue(LinkPreview.isSafePublicAddress(InetAddress.getByName("1.1.1.1")), "1.1.1.1 is public");
        assertTrue(LinkPreview.isSafePublicAddress(InetAddress.getByName("64:ff9b::8.8.8.8")), "NAT64 wrapping public IPv4 is allowed");
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
