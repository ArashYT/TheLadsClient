package com.thelads.core.client.ladsapi;

/**
 * LadsClientAPI: servers that run the LadsClientAPI jar can allow, block, force or limit Lads modules and options for the session.
 * Wire format: artifacts/ladsapi/PROTOCOL.md (frozen v1). The client never speaks first and never registers the channel.
 */
public final class LadsApi {
    /** The plugin channel, the same string on 1.8.9 (max 20 chars there), 1.20.1 and 26.3. */
    public static final String CHANNEL = "lads:api";
    public static final String NAMESPACE = "lads", PATH = "api";
    /** Protocol major version this client speaks. */
    public static final int PROTOCOL = 1;
    /** Biggest payload the client sends (1.8.9 servers reject over 32,767) and accepts. */
    public static final int MAX_OUT = 32_000, MAX_IN = 524_288;

    private LadsApi() {}
}
