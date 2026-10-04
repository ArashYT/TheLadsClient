package com.thelads.core.v1_8_9.feature;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AsyncAppender;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

/**
 * Async Logger on 1.8.9 (log4j 2.0-beta9): the console and latest.log appenders run on one background thread. A full queue
 * makes the caller wait, never drops a line. LaunchClassLoader has no parent class loader, so log4j keeps a second context
 * for it: Minecraft and mods log through that one, LaunchWrapper and FML through the system loader's. Both roots feed the
 * same queue, which keeps their lines in order. beta9 stops appenders in no fixed order on exit, so both contexts' shutdown
 * hooks are replaced by one that first drains the queue, then stops log4j as before (normal exit and crash alike).
 */
public final class AsyncLogging189 {
    private static final Logger LOGGER = LogManager.getLogger("TheLadsCore-1.8.9");

    private AsyncLogging189() {}

    static void install() {
        try {
            LoggerContext game = (LoggerContext) LogManager.getContext(false);
            LoggerContext launch = (LoggerContext) LogManager.getContext(ClassLoader.getSystemClassLoader(), false);
            Configuration config = game.getConfiguration();
            List<AppenderRef> refs = new ArrayList<>(config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME).getAppenderRefs());
            if (refs.isEmpty()) return;
            AsyncAppender async = AsyncAppender.createAppender(refs.toArray(new AppenderRef[0]), null, "true", "4096", "LadsAsync",
                "false", null, config, "true");
            async.start();
            Field hookField = LoggerContext.class.getDeclaredField("shutdownThread");
            hookField.setAccessible(true);
            List<Thread> log4jHooks = new ArrayList<>();
            for (LoggerContext context : launch == game ? Collections.singletonList(game) : Arrays.asList(game, launch)) {
                LoggerConfig root = context.getConfiguration().getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
                List<AppenderRef> own = new ArrayList<>(root.getAppenderRefs());
                // Added before the originals go: a line logged in between is written twice rather than lost.
                root.addAppender(async, null, null);
                for (AppenderRef ref : own) root.removeAppender(ref.getRef());
                context.updateLoggers();
                Thread hook = (Thread) hookField.get(context);
                if (hook != null && Runtime.getRuntime().removeShutdownHook(hook)) log4jHooks.add(hook);
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    async.stop();
                } finally {
                    for (Thread hook : log4jHooks) hook.run();
                }
            }, "Lads log flush"));
            StringBuilder names = new StringBuilder();
            for (AppenderRef ref : refs) names.append(names.length() == 0 ? "" : ", ").append(ref.getRef());
            LOGGER.info("Async logging: [" + names + "] of " + (launch == game ? 1 : 2) + " log4j contexts written by a background thread");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOGGER.warn("Async logging unavailable; the log stays synchronous", e);
        }
    }
}
