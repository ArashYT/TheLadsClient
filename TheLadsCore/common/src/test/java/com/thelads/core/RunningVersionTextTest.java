package com.thelads.core;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** v26_3 builds from a copy of the 26.2 sources: text a 26.3 player sees must not say 26.2. */
class RunningVersionTextTest {
    @Test void twentySixThreeSourcesNeverSayTwentySixTwo() throws Exception {
        Path sources = Path.of("..").toAbsolutePath().normalize().resolve("v26_3/src/main/java/com/thelads/core/v26_2");
        for (String file : List.of("mixin/TitleScreenMixin.java", "gui/ExternalModSettings.java")) {
            String source = Files.readString(sources.resolve(file));
            assertFalse(source.contains("\"26.2\"") || source.contains("Minecraft 26.2"), file + " hard-codes 26.2");
        }
    }
}
