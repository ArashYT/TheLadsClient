/*
 * Decompiled with CFR 0.152.
 */
package com.thelads.core.modules;

import com.thelads.core.config.Module;

public class PingViewModule
extends Module {
    public PingViewModule() {
        super("PingView", "Shows numeric ping in tab list");
    }

    public String getPingText(int ping) {
        if (ping < 0) {
            return "???";
        }
        return ping + "ms";
    }

    public int getPingColor(int ping) {
        if (ping < 0) {
            return -43691;
        }
        if (ping < 100) {
            return -11141291;
        }
        if (ping <= 200) {
            return -171;
        }
        return -43691;
    }
}

