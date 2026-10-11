package com.thelads.core.client.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LaunchTokenTest {
    private static String line(String s) {
        return LaunchToken.readLine(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void readsFirstLineOnly() {
        assertEquals("eyJ.abc-_x", line("eyJ.abc-_x\r\nsecond\n"));
        assertEquals("tok", line("tok"));
        assertEquals("", line(""));
    }

    @Test
    void realTokensPassThrough() {
        assertEquals("0", LaunchToken.resolve("0"));
        assertEquals("eyJ.real", LaunchToken.resolve("eyJ.real"));
    }
}
