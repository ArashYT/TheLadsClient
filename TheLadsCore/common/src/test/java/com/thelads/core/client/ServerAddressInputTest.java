package com.thelads.core.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ServerAddressInputTest {
    @Test
    void acceptsHostsAndPorts() {
        assertEquals("play.example.com", ServerAddressInput.clean("  play.example.com\r\n"));
        assertEquals("localhost:25566", ServerAddressInput.clean("localhost:25566"));
        assertEquals("1.2.3.4", ServerAddressInput.clean("1.2.3.4"));
        assertEquals("[::1]:25565", ServerAddressInput.clean("[::1]:25565"));
    }

    @Test
    void rejectsNonAddresses() {
        assertNull(ServerAddressInput.clean(null));
        assertNull(ServerAddressInput.clean("   "));
        assertNull(ServerAddressInput.clean("two words"));
        assertNull(ServerAddressInput.clean("a.com\nb.com"));
        assertNull(ServerAddressInput.clean("host:99999"));
        assertNull(ServerAddressInput.clean("host:"));
        assertNull(ServerAddressInput.clean("a:1:2"));
        assertNull(ServerAddressInput.clean("-bad.com"));
        assertNull(ServerAddressInput.clean("bad..com"));
        assertNull(ServerAddressInput.clean("http://x.com"));
        StringBuilder tooLong = new StringBuilder();
        for (int i = 0; i < 256; i++) tooLong.append('a');
        assertNull(ServerAddressInput.clean(tooLong.toString()));
    }

    @Test
    void findsDuplicatesIgnoringCaseAndDefaultPort() {
        assertEquals(1, ServerAddressInput.indexOf(Arrays.asList("a.com", "Play.Example.COM"), "play.example.com"));
        assertEquals(0, ServerAddressInput.indexOf(Arrays.asList("host.net:25565"), "HOST.net"));
        assertEquals(-1, ServerAddressInput.indexOf(Arrays.asList("host.net:25566", null), "host.net"));
    }
}
