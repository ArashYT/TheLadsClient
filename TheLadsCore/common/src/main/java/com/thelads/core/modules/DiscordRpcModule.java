package com.thelads.core.modules;

import com.thelads.core.client.discord.DiscordPresence;
import com.thelads.core.client.discord.DiscordRpcService;
import com.thelads.core.client.discord.IpcDiscordTransport;
import com.thelads.core.config.BoolOption;
import com.thelads.core.config.DropdownOption;
import com.thelads.core.config.Module;

/** Discord Rich Presence: what you are doing in the game (every screen), through the Discord desktop app on this PC. */
public class DiscordRpcModule extends Module {
    /** The Lads Discord application: Discord shows its name and icon. */
    public static final String APPLICATION_ID = "1556191716925640744";
    private static final long STARTED = System.currentTimeMillis() / 1000;
    public final DropdownOption detailLevel = addOption(new DropdownOption("Detail Level", 0, "Full", "Simple", "Minimal"));
    public final BoolOption shareServerAddress = addOption(new BoolOption("Share server address", true));
    public final BoolOption shareWorldName = addOption(new BoolOption("Share world name", false));
    public final BoolOption showDimension = addOption(new BoolOption("Show dimension", false));
    public final BoolOption showElapsed = addOption(new BoolOption("Show elapsed time", true));
    private DiscordRpcService service;

    public DiscordRpcModule() {
        super("DiscordRPC", "Shows what you are doing on your Discord profile: the menu you are in, singleplayer or the server you play on. "
            + "Server addresses are shared only as host names, never IPs.");
        setEnabled(true);
    }

    public DiscordPresence.Privacy privacy() {
        return new DiscordPresence.Privacy(shareServerAddress.get(), shareWorldName.get(), showDimension.get(), showElapsed.get(), detailLevel.getIndex());
    }

    /** Game thread, every client tick: what the version adapter sampled. The IPC itself runs on the service's own thread. */
    public void publish(DiscordPresence.Place place, String version, String server, String world, String dimension, Object screen) {
        if (service == null) {
            if (!isEnabled()) return;
            service = new DiscordRpcService(qa() ? QaTransport::new : IpcDiscordTransport::new);
        }
        DiscordPresence.Privacy privacy = privacy();
        String label = screen == null ? null : DiscordPresence.screen(screen.getClass());
        service.submit(new DiscordRpcService.Desired(isEnabled(), APPLICATION_ID, privacy,
            DiscordPresence.from(new DiscordPresence.Game(place, version, server, world, dimension, label, STARTED), privacy)));
    }

    /** Harness runs (the Verification launcher sets one of these) never touch the real Discord app: presence goes to the log instead. */
    private static boolean qa() {
        return Boolean.getBoolean("thelads.verifyAutoWorld") || System.getProperty("thelads.qaScript") != null;
    }

    private static final class QaTransport implements DiscordRpcService.Transport {
        private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("TheLadsCore/Discord");
        @Override public void initialize(String applicationId, DiscordRpcService.Events events) { LOGGER.info("Lads discord QA: connected (no real Discord, app {})", applicationId); events.ready(); }
        @Override public void callbacks() {}
        @Override public void update(DiscordPresence presence) { LOGGER.info("Lads discord QA: presence details='{}' state='{}'", presence.details(), presence.state()); }
        @Override public void clear() { LOGGER.info("Lads discord QA: presence cleared"); }
        @Override public void shutdown() {}
    }
}
