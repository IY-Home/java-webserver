package com.youfuns.logger;

/**
 * Simple logging interface for any class.
 * Can be implemented by any logging framework or custom logger.
 */
public interface SimpleLogger {

    enum Level {
        DEBUG,
        INFO,
        WARN,
        ERROR;

        public static boolean aboveLevel(Level first, Level second) {
            return first.ordinal() >= second.ordinal();
        }
    }

    void log(Class<?> clazz, String message, Level level);
    void log(Class<?> clazz, String message, Level level, Throwable t);
    Level getLogLevel();
    void setLogLevel(Level logLevel);
}