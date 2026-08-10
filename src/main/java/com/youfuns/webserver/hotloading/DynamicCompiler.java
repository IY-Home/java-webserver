package com.youfuns.webserver.hotloading;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.util.Arrays;

public class DynamicCompiler {
    public static Result compile(String sourcePath, String outputDir) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return new Result(false, (short) -2, "Java compiler not found. Please use a JDK, not a JRE.");
        }

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjects(sourcePath);

            // Options: include classpath, output directory
            Iterable<String> options = Arrays.asList(
                    "-d", outputDir,
                    "-cp", System.getProperty("java.class.path")
            );

            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    null,
                    options,
                    null,
                    compilationUnits
            );

            if (!task.call()) {
                return new Result(false, (short) -3, "Compilation failed.");
            } else {
                return new Result(true, (short) 1, "Compilation successful.");
            }
        } catch (IOException e) {
            e.printStackTrace();
            return new Result(false, (short) -4, "Compilation failed — encountered IOException: " + e.getMessage());
        }
    }
}