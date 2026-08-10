package com.youfuns.webserver.hotloading;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DynamicClassLoader {
    public static Class<?> loadClass(String classDir, String className) throws Exception {
        Path dir = Paths.get(classDir);
        URL classUrl = dir.toUri().toURL();

        try (URLClassLoader classLoader = URLClassLoader.newInstance(new URL[]{classUrl})) {
            return classLoader.loadClass(className);
        }
    }
}
