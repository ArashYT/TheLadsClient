package com.thelads.core.v1_8_9.render;

import com.thelads.core.perf.BuiltInRender;
import org.apache.logging.log4j.LogManager;

/**
 * Lads Kerria remake on 1.8.9 (Forge, with or without OptiFine): built in, no setting; see BuiltInRender. The Flerovium item culling
 * is not used on 1.8.9: measured in the render capture (RenderProbe189), its item path is bound by one draw per model, so culling
 * half the quads saved nothing while reading the GL matrices for each model cost about as much.
 */
public final class Render189 {
    private Render189() {}

    /** Mod init: logs what runs this session (the mixins were already chosen by RenderMixinPlugin189). */
    public static void init() {
        String animations = BuiltInRender.standDownReason(BuiltInRender.KERRIA);
        LogManager.getLogger("TheLadsCore").info("Lads render: Kerria texture animation {}", animations == null ? "on" : "off (" + animations + ")");
    }
}
