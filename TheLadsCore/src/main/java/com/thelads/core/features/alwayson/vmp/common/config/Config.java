package com.thelads.core.features.alwayson.vmp.common.config;

public class Config {
    public static final int TARGET_CHUNK_SEND_RATE = -1;
    public static final boolean USE_ASYNC_LOGGING = true;
    public static final boolean USE_OPTIMIZED_ENTITY_TRACKING = true;
    public static final boolean OPTIMIZED_ENTITY_TRACKING_USE_STAGING_AREA = true;
    public static final boolean USE_MULTIPLE_NETTY_EVENT_LOOPS = false;
    public static final boolean USE_ASYNC_PORTALS = true;
    public static final boolean USE_ASYNC_CHUNKS_ON_LOGIN = true;
    public static final boolean USE_ASYNC_CHUNKS_ON_SOME_COMMANDS = false;
    public static final boolean SHOW_ASYNC_LOADING_MESSAGES = true;
    public static final boolean SHOW_CHUNK_TRACKING_MESSAGES = true;

    public static void init() {
    }
}
