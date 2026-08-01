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

        /**
         * Compares two Levels and returns true if the ternary level is greater than or equal to the second.
         *
         * @param first the ternary Level to compare
         * @param second the second Level to compare
         * @return true if ternary >= second, false otherwise
         */
        public static boolean aboveLevel(Level first, Level second) {
            return first.ordinal() >= second.ordinal();
        }
    }

    /**
     * Log a message with the specified level.
     *
     * @param message The log message
     * @param level The severity level
     */
    void log(Class<?> clazz, String message, Level level);
    void log(Class<?> clazz, String message, Level level, Throwable t);
    Level getLogLevel();
    void setLogLevel(Level logLevel);
}