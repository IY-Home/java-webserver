package com.youfuns.logger;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class OutputLogger implements SimpleLogger {
    private final java.io.PrintStream console;
    private volatile Level logLevel;
    private volatile boolean outputOn;

    private boolean showTimestamp;
    private boolean showClass;
    private boolean showLevelPrefix;
    private boolean showThrowableStackTrace;
    private boolean getStackCaller;
    private String prefix;
    private String throwablePrefix;

    private DateTimeFormatter dateTimeFormatter;

    public OutputLogger(java.io.PrintStream console) {
        if (console != null) { this.console = console; } else { this.console = System.out; }
        this.outputOn = true;
        this.logLevel = Level.INFO;
        this.showLevelPrefix = true;
        this.showTimestamp = true;
        this.showClass = true;
        this.prefix = "> ";
        this.throwablePrefix = "\n[EXCEPTION]: ";
        this.showThrowableStackTrace = true;
        this.getStackCaller = false;
        this.dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    }

    public OutputLogger() {
        this(System.out);
    }

    @Override
    public Level getLogLevel() {
        return logLevel;
    }
    @Override
    public void setLogLevel(Level logLevel) {
        if (logLevel != null) this.logLevel = logLevel;
    }

    public boolean isOutputOn() {
        return outputOn;
    }
    public OutputLogger setOutputOn(boolean outputOn) {
        this.outputOn = outputOn;
        return this;
    }
    public OutputLogger toggleOutput() {
        this.outputOn = !this.outputOn;
        return this;
    }

    public OutputLogger setShowLevel(boolean show) {
        this.showLevelPrefix = show;
        return this;
    }

    public OutputLogger setShowTimestamp(boolean show) {
        this.showTimestamp = show;
        return this;
    }

    public OutputLogger setShowClass(boolean show) {
        this.showClass = show;
        return this;
    }

    public OutputLogger setShowThrowableStackTrace(boolean show) {
        this.showThrowableStackTrace = show;
        return this;
    }

    public OutputLogger setPrefix(String prefix) {
        if (prefix != null) this.prefix = prefix;
        return this;
    }

    public OutputLogger setThrowablePrefix(String throwablePrefix) {
        if (throwablePrefix != null) this.throwablePrefix = throwablePrefix;
        return this;
    }

    public OutputLogger setGetStackCaller(boolean getStackCaller) {
        this.getStackCaller = getStackCaller;
        return this;
    }

    public OutputLogger setDateTimeFormatter(DateTimeFormatter dateTimeFormatter) {
        this.dateTimeFormatter = dateTimeFormatter;
        return this;
    }

    /**
     * Log a debug message.
     *
     * @param clazz The class associated with this log entry. May be null,
     *              in which case "Anonymous" will be shown in the prefix.
     * @param message The log message
     */

    @Override
    public void log(Class<?> clazz, String message, Level level) {
        if (message != null && outputOn && Level.aboveLevel(level, logLevel)) {
            console.println(getPrefix(clazz, level) + message);
        }
    }

    @Override
    public void log(Class<?> clazz, String message, Level level, Throwable t) {
        if (message != null && t != null && outputOn && Level.aboveLevel(level, logLevel)) {
            String stackTrace = t.getStackTrace().length == 0
                    ? t.toString()
                    : t.toString() + "\n" + Arrays.stream(t.getStackTrace())
                    .map(element -> "    at " + element.toString())
                    .collect(Collectors.joining("\n"));
            console.println(getPrefix(clazz, level) + message + throwablePrefix + showIf(stackTrace, t.getClass().getSimpleName() + ": '" + t.getMessage() + "'", showThrowableStackTrace));
        }
    }

    public void log(String message, Level level) {
        if (message != null && outputOn && Level.aboveLevel(level, logLevel)) {
            console.println(getPrefix(null, level) + message);
        }
    }

    public void log(Class<?> clazz, Supplier<String> message, Level level) {
        if (message != null && outputOn && Level.aboveLevel(level, logLevel)) {
            console.println(getPrefix(clazz, level) + message.get());
        }
    }

    private String getPrefix(Class<?> clazz, Level level) {
        String className;
        if (!getStackCaller) {
            className = showIf(((clazz == null || clazz.getCanonicalName() == null) ? "Anonymous" : clazz.getCanonicalName()) + ", ", showClass);
        } else {
            java.lang.StackTraceElement caller = Thread.currentThread().getStackTrace()[3];
            className = showIf(caller.getClassName() + "." + caller.getMethodName() + "(" + caller.getFileName() + ":" + caller.getLineNumber() + "), ", showClass);
        }
        LocalDateTime now = LocalDateTime.now();
        String timestamp = showIf(now.format(dateTimeFormatter) + ", ", showTimestamp);
        String levelPrefix = showIf(level.name(), showLevelPrefix);
        boolean showMetaPrefix = (showTimestamp || showClass || showLevelPrefix);
        String leftQuote = showIf("[", showMetaPrefix);
        String rightQuote = showIf("] ", showMetaPrefix);
        return prefix + leftQuote + timestamp + className + levelPrefix + rightQuote;
    }

    public void flush() {
        console.flush();
    }

    public static String showIf(String string, boolean show) {
        return show ? string : "";
    }
    public static String showIf(String string, String elseString, boolean show) {
        return show ? string : elseString;
    }

    public void debug(Class<?> clazz, String message) {
        log(clazz, message, Level.DEBUG);
    }

    public void info(Class<?> clazz, String message) {
        log(clazz, message, Level.INFO);
    }

    public void warn(Class<?> clazz, String message) {
        log(clazz, message, Level.WARN);
    }

    public void error(Class<?> clazz, String message) {
        log(clazz, message, Level.ERROR);
    }
}