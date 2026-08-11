package com.youfuns.webserver.hotloading;

import com.youfuns.webserver.WebServer;
import com.youfuns.webserver.interfaces.DynamicExchangeHandler;
import com.youfuns.webserver.interfaces.ExchangeHandler;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;

public class HotUtils {
    private static boolean isUsed = false;

    private static final List<String> BANNED_PATTERNS = List.of(
            "(?i)addShutdownHook",
            "(?i)Runtime\\.getRuntime",
            "(?i)Runtime\\.exec",
            "(?i)ProcessBuilder",
            "\\.\\./",
            "System\\.(?!in|out)",
            "Class\\.forName",
            "(?i)while\\s*\\(\\s*true\\s*\\)",
            "sun\\s+\\.misc\\s+\\.Unsafe",
            "javax\\s*\\.\\s*script\\s*\\.\\s*ScriptEngine",
            "java\\s*\\.\\s*lang\\s*\\.\\s*ClassLoader",
            "java\\s*\\.\\s*lang\\s*\\.\\s*reflect",
            "java\\s*\\.\\s*nio\\s*\\.\\s*file\\s*\\.\\s*Paths",
            "java\\s*\\.\\s*nio\\s*\\.\\s*file\\s*\\.\\s*Files",
            "java\\s*\\.\\s*io\\s*\\.\\s*File",
            "java\\s*\\.\\s*net\\s*\\.\\s*URL"
    );

    public static Result loadEndpoint(WebServer webServer, String filePath, String classFolder, String endpoint) {
        if (!isUsed) throw new UnsupportedOperationException("Dynamic endpoint loading is not enabled.");
        String className = extractClassName(filePath);
        if (className == null) {
            return new Result(false, (short) -1, "File is an invalid Java file");
        }

        try {
            String content = java.nio.file.Files.readString(
                    Paths.get(filePath));
            if (validate(content) != null) {
                return new Result(false, (short) -9, validate(content));
            }
        } catch (IOException e) {
            return new Result(false, (short) -1, "File loading failed — encountered IOException: " + e.getMessage());
        }

        Result compiled = DynamicCompiler.compile(filePath, classFolder);
        if (!compiled.success()) {
            return compiled;
        }

        Class<?> handlerClass;
        try {
            handlerClass = DynamicClassLoader.loadClass(classFolder, className);
        } catch (Exception e) {
            return new Result(false, (short) -5, "Encountered " + e.getClass().getSimpleName() + " while loading class: " + e.getMessage());
        }
        if (handlerClass == null) {
            return new Result(false, (short) -6, "Loaded class is null");
        }

        boolean isDynamicExchangeHandler = false;

        if (!ExchangeHandler.class.isAssignableFrom(handlerClass)) {
            if (!DynamicExchangeHandler.class.isAssignableFrom(handlerClass)) {
                return new Result(false, (short) -7, "The submitted class does not implement ExchangeHandler or DynamicExchangeHandler");
            } else {
                isDynamicExchangeHandler = true;
            }
        }

        if (!isDynamicExchangeHandler) {
            ExchangeHandler handler;

            try {
                handler = (ExchangeHandler) handlerClass.getDeclaredConstructor().newInstance();
            } catch (NoSuchMethodException | java.lang.InstantiationException | java.lang.IllegalAccessException |
                     java.lang.reflect.InvocationTargetException | ClassCastException e) {
                e.printStackTrace();
                return new Result(false, (short) -8, e.getClass().getSimpleName() + " while loading class: " + e.getMessage());
            }

            webServer.endpoint(endpoint, handler);
        } else {
            DynamicExchangeHandler handler;

            try {
                handler = (DynamicExchangeHandler) handlerClass.getDeclaredConstructor().newInstance();
            } catch (NoSuchMethodException | java.lang.InstantiationException | java.lang.IllegalAccessException |
                     java.lang.reflect.InvocationTargetException | ClassCastException e) {
                e.printStackTrace();
                return new Result(false, (short) -8, e.getClass().getSimpleName() + " while loading class: " + e.getMessage());
            }

            webServer.dynamicEndpoint(endpoint, handler);
        }

        return new Result(true, (short) 0, "Class " + className + " at endpoint " + endpoint + " was registered successfully.");
    }

    private static boolean isValidJavaClassName(String name) {
        if (name == null || name.isEmpty()) return false;

        // Must start with a letter, underscore, or dollar sign
        if (!Character.isJavaIdentifierStart(name.charAt(0))) {
            return false;
        }

        // All characters must be valid Java identifier parts
        for (int i = 1; i < name.length(); i++) {
            if (!Character.isJavaIdentifierPart(name.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    private static String extractClassName(String filePath) {
        String fileName = Paths.get(filePath).getFileName().toString();

        // 3. Check extension (must be .java)
        if (!fileName.endsWith(".java")) {
            return "invalid";
        }

        // 4. Extract base name (remove .java)
        String baseName = fileName.substring(0, fileName.length() - 5);

        // 5. Validate it's a valid Java class name
        if (!isValidJavaClassName(baseName)) {
            return null;
        }

        return baseName;
    }

    private static String validate(String sourceCode) {
        for (String pattern : BANNED_PATTERNS) {
            if (Pattern.compile(pattern).matcher(sourceCode).find()) {
                return "Banned construct found: " + pattern.replace("\\", "");
            }
        }
        return null;
    }

    public static void enableHotLoadingUnsafe(boolean enable) {
        isUsed = enable;
    }
}
