package com.thelads.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Anti-ban: Fabric announces every client global receiver to every server on join (minecraft:register / c:register). Lads
 * registers its client receivers per connection, once that server has shown it speaks the protocol (hypixel:ping before the first
 * Hypixel ping, Jade after the server registered its handshake), or reads payloads without a receiver (AppleSkin).
 */
class QuietChannelsTest {
    @Test void noClientGlobalReceivers() throws Exception {
        Path root = Path.of("..").toAbsolutePath().normalize();
        for (String module : List.of("v26_3", "v1_20_1")) {
            try (Stream<Path> files = Files.walk(root.resolve(module + "/src/main/java"))) {
                List<String> found = files.filter(file -> file.toString().endsWith(".java")).filter(file -> {
                    try {
                        String source = Files.readString(file);
                        return source.contains("ClientPlayNetworking.registerGlobalReceiver")
                            || source.contains("ClientConfigurationNetworking.registerGlobalReceiver");
                    } catch (Exception unreadable) {
                        throw new IllegalStateException(file.toString(), unreadable);
                    }
                }).map(file -> root.relativize(file).toString()).toList();
                assertEquals(List.of(), found, module + ": a client global receiver is announced to every server");
            }
        }
    }
}
