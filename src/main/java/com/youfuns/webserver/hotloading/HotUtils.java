package com.youfuns.webserver.hotloading;

import com.youfuns.webserver.WebServer;
import com.youfuns.webserver.interfaces.DynamicExchangeHandler;
import com.youfuns.webserver.interfaces.ExchangeHandler;

import java.nio.file.Paths;

public class HotUtils {
    private static boolean isUsed = false;

    public static Result loadEndpoint(WebServer webServer, String filePath, String classFolder, String endpoint) {
        if (!isUsed) throw new UnsupportedOperationException("Dynamic endpoint loading is not enabled.");
        String className = extractClassName(filePath);
        if (className == null) {
            return new Result(false, (short) -1, "File is an invalid Java file");
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

        return new Result(true, (short) 1, "Class " + className + " at endpoint " + endpoint + " was registered successfully.");
    }

    public static String extractClassName(String filePath) {
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

    public static void enableHotLoadingUnsafe(boolean enable) {
        isUsed = enable;
    }
}
