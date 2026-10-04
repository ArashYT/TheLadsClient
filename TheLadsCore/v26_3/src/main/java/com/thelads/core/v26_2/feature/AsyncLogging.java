package com.thelads.core.v26_2.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AsyncAppender;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.impl.LocationAware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Async Logger, remade: the game's console and latest.log appenders run on one background thread, so no game thread waits
 * on their I/O. Formats, levels, filters and log rotation are the original appenders' own. A full queue makes the caller
 * wait (Log4j writes directly when the background thread itself logs), so nothing is dropped; on exit, normal or crash,
 * Log4j stops async appenders first, draining the queue into the files.
 */
public final class AsyncLogging {
    private static final Logger LOGGER = LoggerFactory.getLogger("TheLadsCore");

    private AsyncLogging() {}

    public static void install() {
        if (FabricLoader.getInstance().isModLoaded("asynclogger")) return;
        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            Configuration config = context.getConfiguration();
            LoggerConfig root = config.getRootLogger();
            List<AppenderRef> moved = new ArrayList<>();
            for (AppenderRef ref : root.getAppenderRefs()) {
                Appender appender = config.getAppender(ref.getRef());
                // A layout printing the caller's file and line needs it taken on the calling thread: that appender stays as it is.
                if (appender != null && !(appender.getLayout() instanceof LocationAware layout && layout.requiresLocation())) moved.add(ref);
            }
            if (moved.isEmpty()) return;
            AsyncAppender async = AsyncAppender.newBuilder().setName("LadsAsync").setConfiguration(config)
                .setAppenderRefs(moved.toArray(AppenderRef[]::new)).setBlocking(true).setBufferSize(4096).setShutdownTimeout(10_000).build();
            async.start();
            config.addAppender(async);
            // Added before the originals go: a line logged in between is written twice rather than lost.
            root.addAppender(async, null, null);
            for (AppenderRef ref : moved) root.removeAppender(ref.getRef());
            context.updateLoggers();
            if (!config.isShutdownHookEnabled())
                Runtime.getRuntime().addShutdownHook(new Thread(() -> async.stop(10, TimeUnit.SECONDS), "Lads log flush"));
            LOGGER.info("Async logging: {} written by a background thread", moved.stream().map(AppenderRef::getRef).toList());
        } catch (RuntimeException | LinkageError e) {
            LOGGER.warn("Async logging unavailable; the log stays synchronous", e);
        }
    }
}
