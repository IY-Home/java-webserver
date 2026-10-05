package com.youfuns.logger;

public enum LoggerManager {

    INSTANCE(new OutputLogger(System.out));

    private SimpleLogger logger;
    private final Class<? extends SimpleLogger> loggerType;

    LoggerManager(SimpleLogger logger) {
        this.logger = logger;
        logger.setLogLevel(SimpleLogger.Level.DEBUG);
        loggerType = logger.getClass();
    }

    public SimpleLogger getLogger() {
        return logger;
    }

    public <T extends SimpleLogger> T getTypedLogger(Class<T> expectedType) {
        if (expectedType.isInstance(logger)) {
            return expectedType.cast(logger);
        }
        throw new IllegalStateException("Logger is not of type " + expectedType.getName());
    }

    public void setLogger(SimpleLogger newLogger) {
        if (newLogger == null) {
            throw new IllegalArgumentException("Logger cannot be null");
        }
        this.logger = newLogger;
    }

    // Convenience functions for debug logging
    public static void quickLog(Object caller, String message) {
        INSTANCE.getLogger().log(caller.getClass(), message, SimpleLogger.Level.DEBUG);
    }
    public static void quickLog(Class<?> clazz, String message) {
        INSTANCE.getLogger().log(clazz, message, SimpleLogger.Level.DEBUG);
    }
    public static void quickLog(Object caller, String message, SimpleLogger.Level level) {
        INSTANCE.getLogger().log(caller.getClass(), message, level);
    }
    public static void quickLog(Class<?> clazz, String message, SimpleLogger.Level level) {
        INSTANCE.getLogger().log(clazz, message, level);
    }
    public static void quickLog(String message) {
        INSTANCE.getLogger().log(null, message, SimpleLogger.Level.DEBUG);
    }
    public static void quickLog(Object caller, Throwable t) {
        INSTANCE.getLogger().log(caller.getClass(), t.getClass().getSimpleName() + ": " + t.getMessage(), SimpleLogger.Level.WARN, t);
    }
    public static void quickLog(Class<?> clazz, Throwable t) {
        INSTANCE.getLogger().log(clazz, t.getClass().getSimpleName() + ": " + t.getMessage(), SimpleLogger.Level.WARN, t);
    }
    public static void quickLog(Throwable t) {
        INSTANCE.getLogger().log(null, t.getClass().getSimpleName() + ": " + t.getMessage(), SimpleLogger.Level.WARN, t);
    }
}
