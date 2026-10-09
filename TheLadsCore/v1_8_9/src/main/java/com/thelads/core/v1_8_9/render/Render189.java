package com.thelads.core.v1_8_9.render;

import com.thelads.core.perf.BuiltInRender;
import org.apache.logging.log4j.LogManager;

/** Lads Flerovium and Kerria remakes on 1.8.9 (Forge, with or without OptiFine): built in, no setting; see BuiltInRender. */
public final class Render189 {
    private Render189() {}

    /** Mod init: logs what runs this session (the mixins were already chosen by RenderMixinPlugin189). */
    public static void init() {
        String items = BuiltInRender.standDownReason(BuiltInRender.FLEROVIUM), animations = BuiltInRender.standDownReason(BuiltInRender.KERRIA);
        LogManager.getLogger("TheLadsCore").info("Lads render: Flerovium item culling {}, Kerria texture animation {}",
            items == null ? "on" : "off (" + items + ")", animations == null ? "on" : "off (" + animations + ")");
    }
}
