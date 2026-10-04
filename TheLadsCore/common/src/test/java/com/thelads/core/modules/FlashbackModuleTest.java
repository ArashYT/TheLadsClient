package com.thelads.core.modules;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FlashbackModuleTest {
    @TempDir Path game;

    @Test void blankOrUnusableFolderKeepsFlashbacksOwn() throws Exception {
        assertNull(FlashbackModule.replayFolder(null, game));
        assertNull(FlashbackModule.replayFolder("   ", game));
        assertNull(FlashbackModule.replayFolder("\"\"", game));
        Files.writeString(game.resolve("a-file.txt"), "not a folder");
        assertNull(FlashbackModule.replayFolder("a-file.txt", game));
        assertNull(FlashbackModule.replayFolder("bad\0name", game));
    }

    @Test void folderIsAbsoluteAndMayNotExistYet() throws Exception {
        Path other = Files.createDirectories(game.resolve("drive/Replays"));
        assertEquals(other, FlashbackModule.replayFolder(" " + other + " ", game));
        assertEquals(other, FlashbackModule.replayFolder("\"" + other + "\"", game), "Explorer's Copy as path quotes");
        assertEquals(game.resolve("replays/new").toAbsolutePath(), FlashbackModule.replayFolder("replays/../replays/new", game));
    }

    @Test void onlyOpenH264MovesAndOnlyToAWorkingGpuEncoder() {
        var all = List.of("h264_amf", "h264_qsv", "h264_nvenc", "libopenh264");
        assertEquals("h264_nvenc", FlashbackModule.promotedEncoder("libopenh264", all));
        assertEquals("h264_qsv", FlashbackModule.promotedEncoder("libopenh264", List.of("h264_d3d12va", "h264_qsv", "libopenh264")));
        assertEquals("h264_amf", FlashbackModule.promotedEncoder("libopenh264", List.of("h264_amf", "libopenh264")));
        assertNull(FlashbackModule.promotedEncoder("libopenh264", List.of("h264_d3d12va", "h264_vulkan", "libopenh264")), "software-only machine");
        assertNull(FlashbackModule.promotedEncoder("h264_qsv", all), "a chosen encoder is kept");
        assertNull(FlashbackModule.promotedEncoder("png", all));
    }

    @Test void settingsOnlyEntryIsBuiltInWithoutASwitch() {
        com.thelads.core.config.ModuleSupport.registerSettingsOnly("FlashbackTestEntry");
        assertTrue(com.thelads.core.config.ModuleSupport.isBuiltIn("FlashbackTestEntry"));
        assertTrue(com.thelads.core.config.ModuleSupport.isSettingsOnly("FlashbackTestEntry"));
        assertFalse(com.thelads.core.config.ModuleSupport.isToggleable("FlashbackTestEntry"));
        com.thelads.core.config.ModuleSupport.registerUnavailable("FlashbackTestEntry", "Flashback is not installed.");
        assertFalse(com.thelads.core.config.ModuleSupport.isSettingsOnly("FlashbackTestEntry"));
    }

    @Test void pngLeavesOneCoreAndOtherEncodersKeepFfmpegsChoice() {
        assertEquals(23, FlashbackModule.encoderThreads("png", 24));
        assertEquals(1, FlashbackModule.encoderThreads("png", 1));
        assertEquals(0, FlashbackModule.encoderThreads("libopenh264", 24));
        assertEquals(0, FlashbackModule.encoderThreads("h264_nvenc", 24));
    }
}
