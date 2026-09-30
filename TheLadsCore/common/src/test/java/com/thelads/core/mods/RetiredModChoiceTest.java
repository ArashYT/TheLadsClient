package com.thelads.core.mods;

import com.thelads.core.config.ConfigManager;
import com.thelads.core.config.Module;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class RetiredModChoiceTest {
    @TempDir Path dir;
    @AfterEach void release() { ConfigManager.setTestConfigFile(null); }

    private Module module() {
        ConfigManager.setTestConfigFile(dir.resolve("thelads_config.json").toFile());
        var module = new Module("Autohide", "test");
        module.setEnabled(true);
        return module;
    }

    @Test void aJarThePlayerTurnedOffLeavesTheModuleOffOnce() throws Exception {
        new ModStateStore(dir).setRequested(Map.of("autohidehud", false), Map.of("autohidehud", "WfEV6RRi"));
        var module = module();
        assertTrue(RetiredModChoice.adopt(dir, "autohidehud", "WfEV6RRi", module));
        assertFalse(module.isEnabled());
        assertTrue(Files.exists(dir.resolve("thelads_config.json")), "the adopted state is saved");
        module.setEnabled(true); // the player turns the Lads module back on
        assertFalse(RetiredModChoice.adopt(dir, "autohidehud", "WfEV6RRi", module));
        assertTrue(module.isEnabled(), "a later Lads menu choice is never overridden");
    }

    @Test void noSavedChoiceOrAnEnabledJarKeepsTheDefault() throws Exception {
        var module = module();
        assertFalse(RetiredModChoice.adopt(dir, "autohidehud", "WfEV6RRi", module));
        assertTrue(module.isEnabled());
        Path other = Files.createDirectory(dir.resolve("other"));
        new ModStateStore(other).setRequested(Map.of("autohidehud", true), Map.of());
        assertFalse(RetiredModChoice.adopt(other, "autohidehud", "WfEV6RRi", module));
        assertTrue(module.isEnabled());
        assertTrue(Files.exists(other.resolve(".lads-adopted-autohidehud")));
    }
}
