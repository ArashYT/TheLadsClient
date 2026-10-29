package xaero.common;

import xaero.common.minimap.MinimapProcessor;

public class XaeroMinimapSession {
    private static XaeroMinimapSession current;
    private final MinimapProcessor minimapProcessor = new MinimapProcessor();

    public static XaeroMinimapSession getCurrentSession() {
        return current;
    }

    public static void setCurrentSession(XaeroMinimapSession session) {
        current = session;
    }

    public MinimapProcessor getMinimapProcessor() {
        return minimapProcessor;
    }
}
