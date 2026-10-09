package com.thelads.core.v1_20_1.render;

import com.thelads.core.perf.BuiltInRender;
import org.slf4j.LoggerFactory;

/** Lads Flerovium and Kerria remakes on 1.20.1 (Fabric, and Forge through Connector): built in, no setting; see BuiltInRender. */
public final class Render1201 {
    private Render1201() {}

    /** Client initializer: logs what runs this session (the mixins were already chosen by RenderMixinPlugin1201). */
    public static void init() {
        String items = BuiltInRender.standDownReason(BuiltInRender.FLEROVIUM), animations = BuiltInRender.standDownReason(BuiltInRender.KERRIA);
        LoggerFactory.getLogger("TheLadsCore").info("Lads render: Flerovium item culling {}, Kerria texture animation {}",
            items == null ? "on" : "off (" + items + ")", animations == null ? "on" : "off (" + animations + ")");
    }
}
