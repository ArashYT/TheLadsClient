package com.thelads.core.modules;

import com.thelads.core.client.bridge.LadsGameBridge;
import com.thelads.core.client.rewind.InstantClipBuffer;
import com.thelads.core.config.ActionOption;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.Module;
import com.thelads.core.config.SliderOption;

import java.io.File;

/**
 * Instant clip / rewind buffer module: in-memory network packet ring buffer that saves the last
 * 30-60s of gameplay into a standard ReplayMod .mcpr replay file when triggered.
 */
public final class InstantClipModule extends Module {
    public static final String NAME = InstantClipBuffer.NAME;

    public final SliderOption duration = addOption(new SliderOption(InstantClipBuffer.DURATION_OPTION, 30, 5, 120, 5).suffix("s"));
    public final SliderOption memoryCap = addOption(new SliderOption(InstantClipBuffer.MEMORY_CAP_OPTION, 64, 1, 512, 1).suffix(" MB"));
    public final BoolOption notify = addOption(new BoolOption("Chat Notification", true));
    public final ActionOption saveAction = addOption(new ActionOption("Save Clip Now", "Save"));

    public InstantClipModule() {
        super(NAME, "Circular in-memory network packet buffer that saves the last 30-60 seconds into a replay file when triggered.");
        setEnabled(false); // Default OFF per TASK-R2b
        saveAction.setAction(() -> {
            LadsGameBridge bridge = LadsGameBridge.get();
            String server = bridge.getServerAddress();
            File dir = new File("replay_recordings");
            InstantClipBuffer.triggerAsyncSave(dir, server, "1.8.9", 47);
        });
    }
}
