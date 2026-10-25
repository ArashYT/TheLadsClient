package com.thelads.core.modules;

import com.thelads.core.config.Module;

/**
 * Link Preview module (default OFF).
 * Gated by user opt-in: displays image previews on link confirmation screens
 * only when the user explicitly clicks the "Load preview" button.
 */
public class LinkPreviewModule extends Module {
    public static final String NAME = "Link Preview";

    public LinkPreviewModule() {
        super(NAME, "Show image previews on link confirmation screens after clicking Load preview.");
        setEnabled(false);
    }
}
