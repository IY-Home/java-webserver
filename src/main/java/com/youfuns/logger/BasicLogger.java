package com.youfuns.logger;

public class BasicLogger implements SimpleLogger {
    private Level level = Level.DEBUG;

    @Override
    public void log(Class<?> clazz, String message, Level level) {
        if (Level.aboveLevel(level, this.level)) System.out.println(level.name() + " " + clazz.getSimpleName() + ": " + message);
    }

    @Override
    public void log(Class<?> clazz, String message, Level level, Throwable t) {
        log(clazz, message, level);
        if (Level.aboveLevel(level, this.level)) t.printStackTrace(System.err);
    }

    @Override
    public Level getLogLevel() {
        return level;
    }

    @Override
    public void setLogLevel(Level logLevel) {
        this.level = logLevel;
    }
}
