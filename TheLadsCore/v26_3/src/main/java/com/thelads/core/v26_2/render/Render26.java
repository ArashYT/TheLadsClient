package com.thelads.core.v26_2.render;

import com.thelads.core.perf.BuiltInRender;
import org.slf4j.LoggerFactory;

/** Lads Flerovium and Kerria remakes on 26.3: built in, no setting; see BuiltInRender. */
public final class Render26 {
    private Render26() {}

    /** Client initializer: logs what runs this session (the mixins were already chosen by RenderMixinPlugin26). */
    public static void init() {
        String items = BuiltInRender.standDownReason(BuiltInRender.FLEROVIUM), animations = BuiltInRender.standDownReason(BuiltInRender.KERRIA);
        LoggerFactory.getLogger("TheLadsCore").info("Lads render: Flerovium item culling {}, Kerria texture animation {}",
            items == null ? "on" : "off (" + items + ")", animations == null ? "on" : "off (" + animations + ")");
        if (Boolean.getBoolean("thelads.verifyAutoWorld"))
            net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(RenderProbe26::tick);
    }
}
