package com.thelads.core.v1_8_9.feature;

import java.lang.reflect.Field;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AsyncAppender;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

/**
 * Async Logger on 1.8.9 (log4j 2.0-beta9): the root logger's console and latest.log appenders run on one background thread.
 * A full queue makes the caller wait, never drops a line. beta9 stops appenders in no fixed order on exit, so its shutdown
 * hook is replaced by one that first drains the queue, then stops log4j as before (normal exit and crash alike).
 */
public final class AsyncLogging189 {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-1.8.9");

    private AsyncLogging189() {}

    static void install() {
        try {
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            Configuration config = context.getConfiguration();
            LoggerConfig root = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
            List<AppenderRef> refs = root.getAppenderRefs();
            if (refs.isEmpty()) return;
            AsyncAppender async = AsyncAppender.createAppender(refs.toArray(new AppenderRef[0]), null, "true", "4096", "LadsAsync",
                "false", null, config, "true");
            async.start();
            // Added before the originals go: a line logged in between is written twice rather than lost.
            root.addAppender(async, null, null);
            for (AppenderRef ref : refs) root.removeAppender(ref.getRef());
            context.updateLoggers();
            Field hookField = LoggerContext.class.getDeclaredField("shutdownThread");
            hookField.setAccessible(true);
            Thread log4jHook = (Thread) hookField.get(context);
            if (log4jHook != null) Runtime.getRuntime().removeShutdownHook(log4jHook);
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    async.stop();
                } finally {
                    if (log4jHook != null) log4jHook.run();
                }
            }, "Lads log flush"));
            StringBuilder names = new StringBuilder();
            for (AppenderRef ref : refs) names.append(names.length() == 0 ? "" : ", ").append(ref.getRef());
            LOGGER.info("Async logging: [" + names + "] written by a background thread");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOGGER.warn("Async logging unavailable; the log stays synchronous", e);
        }
    }
}
