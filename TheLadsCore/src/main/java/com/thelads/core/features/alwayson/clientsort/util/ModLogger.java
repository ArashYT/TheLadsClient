/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.logging.log4j.Level
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 *  org.apache.logging.log4j.util.StackLocatorUtil
 */
package com.thelads.core.features.alwayson.clientsort.util;

import com.thelads.core.features.alwayson.clientsort.ClientSort;
import com.thelads.core.features.alwayson.clientsort.ClientSortClient;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.util.StackLocatorUtil;

public class ModLogger {
    private final Logger logger;

    public ModLogger(Logger logger) {
        this.logger = logger;
    }

    public ModLogger(String name) {
        this(LogManager.getLogger((String)name));
    }

    private String edit(Level level, String message) {
        if (level == Level.DEBUG) {
            return String.format("[%s/%s]: %s", this.logger.getName(), StackLocatorUtil.getCallerClass((int)4).getSimpleName(), message);
        }
        return String.format("[%s]: %s", this.logger.getName(), message);
    }

    private void log(Level level, String message, Object ... args) {
        if (!this.logger.isEnabled(level)) {
            return;
        }
        this.logger.log(level, this.edit(level, message), args);
    }

    public void trace(String message, Object ... args) {
        this.log(Level.TRACE, message, args);
    }

    public void debug(String message, Object ... args) {
        this.log(Level.DEBUG, message, args);
    }

    public void info(String message, Object ... args) {
        this.log(Level.INFO, message, args);
    }

    public void warn(String message, Object ... args) {
        this.log(Level.WARN, message, args);
    }

    public void error(String message, Object ... args) {
        this.log(Level.ERROR, message, args);
    }

    public void fatal(String message, Object ... args) {
        this.log(Level.FATAL, message, args);
    }
}
