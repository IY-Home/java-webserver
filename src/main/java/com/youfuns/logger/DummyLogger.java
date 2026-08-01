package com.youfuns.logger;

public class DummyLogger implements SimpleLogger {
    public void log(Class<?> clazz, String message, Level level) {};
    public void log(Class<?> clazz, String message, Level level, Throwable t) {};
    public Level getLogLevel() { return Level.INFO; };
    public void setLogLevel(Level logLevel) {};
}