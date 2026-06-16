/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
 *  it.unimi.dsi.fastutil.objects.ObjectOpenHashSet
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 *  org.apache.logging.log4j.core.Appender
 *  org.apache.logging.log4j.core.LoggerContext
 *  org.apache.logging.log4j.core.appender.AsyncAppender
 *  org.apache.logging.log4j.core.appender.AsyncAppender$Builder
 *  org.apache.logging.log4j.core.async.AsyncLoggerContext
 *  org.apache.logging.log4j.core.config.AbstractConfiguration
 *  org.apache.logging.log4j.core.config.AppenderRef
 *  org.apache.logging.log4j.core.config.Configuration
 *  org.apache.logging.log4j.core.config.LoggerConfig
 *  org.apache.logging.log4j.spi.LoggerContext
 */
package com.thelads.core.features.alwayson.vmp.common.logging;

import com.thelads.core.features.alwayson.vmp.common.config.Config;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AsyncAppender;
import org.apache.logging.log4j.core.async.AsyncLoggerContext;
import org.apache.logging.log4j.core.config.AbstractConfiguration;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

public class AsyncAppenderBootstrap {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final ObjectOpenHashSet<String> appenderInCompatibilityMode = new ObjectOpenHashSet((Object[])new String[]{"SysOut"});

    public static void boot() {
        if (!Config.USE_ASYNC_LOGGING) {
            return;
        }
        try {
            LoggerContext ctx;
            org.apache.logging.log4j.spi.LoggerContext loggerContext = LogManager.getContext(false);
            if (loggerContext instanceof LoggerContext) {
                ctx = (LoggerContext) loggerContext;
                Configuration configurationObj = ctx.getConfiguration();
                if (configurationObj instanceof AbstractConfiguration) {
                    AbstractConfiguration configuration = (AbstractConfiguration) configurationObj;
                    if (ctx instanceof AsyncLoggerContext) {
                        LOGGER.info("Logger is already async, skipping init async appender");
                        return;
                    }
                    Object2ObjectOpenHashMap original = new Object2ObjectOpenHashMap(configuration.getAppenders());
                    Object2ObjectOpenHashMap newMap = new Object2ObjectOpenHashMap();
                    LoggerConfig config = ctx.getRootLogger().get();
                    for (AppenderRef appenderRef : config.getAppenderRefs()) {
                        AsyncAppender asyncAppender = new AsyncAppender.Builder().setAppenderRefs(new AppenderRef[]{appenderRef}).setName(appenderRef.getRef()).setConfiguration((Configuration)configuration).build();
                        asyncAppender.start();
                        config.removeAppender(appenderRef.getRef());
                        config.addAppender((Appender)asyncAppender, null, null);
                        newMap.put((Object)appenderRef.getRef(), (Object)asyncAppender);
                    }
                    ctx.updateLoggers();
                    for (AppenderRef appenderRef : config.getAppenderRefs()) {
                        for (LoggerConfig loggerConfig : configuration.getLoggers().values()) {
                            if (!loggerConfig.getAppenders().containsKey(appenderRef.getRef())) continue;
                            loggerConfig.removeAppender(appenderRef.getRef());
                            loggerConfig.addAppender((Appender)newMap.get((Object)appenderRef.getRef()), null, null);
                        }
                        if (!config.getAppenders().containsKey(appenderRef.getRef())) continue;
                        config.getAppenders().remove(appenderRef.getRef());
                        config.addAppender((Appender)newMap.get((Object)appenderRef.getRef()), null, null);
                    }
                    LOGGER.info("Successfully started async appender with {}", (Object)original.keySet());
                } else {
                    LOGGER.error("Unsupported logger settings for async appender");
                }
            } else {
                LOGGER.error("Unsupported logger settings for async appender");
            }
        }
        catch (Throwable t) {
            LOGGER.error("Error occurred while booting async appender", t);
        }
    }
}

