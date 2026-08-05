package com.youfuns.webserver.demo;

import com.youfuns.logger.LoggerManager;
import com.youfuns.webserver.Exchange;
import com.youfuns.webserver.TemplateEngine;
import com.youfuns.webserver.WebServer;

public class Main {
    // Testing the webserver
    public static void main(String[] args) {
        WebServer myServer = new WebServer(8080);
        myServer.on("/greet", exchange -> {
                    TemplateEngine engine = TemplateEngine.fromFile("./templates/index.html");
                    engine.replace("title", "Welcome");
                    engine.replace("welcome", "Hello there!");
                    engine.replace("user", "Alice");
                    exchange.addResponseHeader("Content-Type", "text/html");
                    exchange.sendResponse(engine.getTemplate());
                })
                .on("/api/setConfig", "POST", exchange -> {
                    if (!exchange.isMultipartRequest()) {
                        exchange.sendBadRequestResponse("Expected multipart/form-data");
                        return;
                    }

                    if (!exchange.hasFile("config")) {
                        exchange.sendBadRequestResponse("No logo uploaded");
                        return;
                    }

                    Exchange.UploadedFile file = exchange.getFile("config");

                    // Check file type
                    if (exchange.isJSON(file)) {
                        exchange.saveFileSafe(file, "./config", false); // Don't overwrite previous configs for debug
                        exchange.sendResponse("Uploaded: " + file.getFilename());
                        myServer.serveFile("/config", "./config");

                    } else {
                        exchange.sendBadRequestResponse("Only PNG and JPEG allowed");
                    }
                })
                .hook(exchange -> {
                    LoggerManager.quickLog("Received " + exchange.getHttpMethod() + " to " + exchange.getRequestPath());
                    return true;
                })
                .hook(exchange -> {
                    exchange.setAttribute("startTime", System.nanoTime());
                    return true;
                })
                .tail(exchange -> {
                    Long startTime = exchange.getAttribute("startTime", Long.class);
                    if (startTime != null) {
                        long duration = (System.nanoTime() - startTime) / 1_000_000; // milliseconds
                        LoggerManager.quickLog("Duration taken: " + duration);
                    }
                    return true;
                })
                .onException((exchange, exception) -> {
                    LoggerManager.quickLog("Caught " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
                    if (exception instanceof IllegalArgumentException) {
                        exchange.sendBadRequestResponse("Bad request: " + exception.getMessage());
                    } else {
                        exchange.sendErrorResponse("An error occurred.");
                    }
                }).start();
    }
}