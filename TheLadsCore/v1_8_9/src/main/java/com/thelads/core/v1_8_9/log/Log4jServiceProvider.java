package com.thelads.core.v1_8_9.log;

import org.apache.logging.log4j.LogManager;
import org.slf4j.ILoggerFactory;
import org.slf4j.IMarkerFactory;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.BasicMarkerFactory;
import org.slf4j.helpers.LegacyAbstractLogger;
import org.slf4j.helpers.MessageFormatter;
import org.slf4j.helpers.NOPMDCAdapter;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.SLF4JServiceProvider;

/**
 * Common logs through SLF4J; Minecraft 1.8.9 ships only log4j 2.0-beta9 (too old for log4j-slf4j2-impl). This provider
 * (found by ServiceLoader under the relocated SLF4J) sends each call to the log4j logger of the same name, so Core lines
 * land in latest.log with their level.
 */
public final class Log4jServiceProvider implements SLF4JServiceProvider {
    private final ILoggerFactory loggers = name -> new Log4jLogger(LogManager.getLogger(name));
    private final IMarkerFactory markers = new BasicMarkerFactory();
    private final MDCAdapter mdc = new NOPMDCAdapter();

    @Override public ILoggerFactory getLoggerFactory() { return loggers; }
    @Override public IMarkerFactory getMarkerFactory() { return markers; }
    @Override public MDCAdapter getMDCAdapter() { return mdc; }
    @Override public String getRequestedApiVersion() { return "2.0.99"; }
    @Override public void initialize() {}

    private static final class Log4jLogger extends LegacyAbstractLogger {
        private final org.apache.logging.log4j.Logger log;

        Log4jLogger(org.apache.logging.log4j.Logger log) {
            this.log = log;
            this.name = log.getName();
        }

        @Override public boolean isTraceEnabled() { return log.isTraceEnabled(); }
        @Override public boolean isDebugEnabled() { return log.isDebugEnabled(); }
        @Override public boolean isInfoEnabled() { return log.isInfoEnabled(); }
        @Override public boolean isWarnEnabled() { return log.isWarnEnabled(); }
        @Override public boolean isErrorEnabled() { return log.isErrorEnabled(); }
        @Override protected String getFullyQualifiedCallerName() { return null; }

        @Override
        protected void handleNormalizedLoggingCall(Level level, Marker marker, String pattern, Object[] arguments, Throwable throwable) {
            log.log(level(level), MessageFormatter.basicArrayFormat(pattern, arguments), throwable);
        }

        private static org.apache.logging.log4j.Level level(Level level) {
            switch (level) {
                case ERROR: return org.apache.logging.log4j.Level.ERROR;
                case WARN: return org.apache.logging.log4j.Level.WARN;
                case INFO: return org.apache.logging.log4j.Level.INFO;
                case DEBUG: return org.apache.logging.log4j.Level.DEBUG;
                default: return org.apache.logging.log4j.Level.TRACE;
            }
        }
    }
}
