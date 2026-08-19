package com.youfuns.webserver.demo;

import com.youfuns.logger.LoggerManager;
import com.youfuns.webserver.TemplateEngine;
import com.youfuns.webserver.WebServer;

public class FileUploadTest {
    public static void main(String[] args) {
        var webServer = WebServer.builder().port("127.0.0.1", 8080).build();
        webServer
                .ensureExists("./fileUploadDemo/uploads")
                .on("/", exchange -> {
                    TemplateEngine templateEngine = TemplateEngine.fromFile("./fileUploadDemo/index.html")
                            .replace("title", "Welcome")
                            .replace("welcome", "Welcome, " + exchange.getQueryParameter("user", "Guest"))
                            .replace("user", exchange.getQueryParameter("user", "Guest"))
                            .replace("image", "./pic");
                    exchange.formatHTML();
                    exchange.sendResponse(templateEngine.getTemplate());
                })
                .on("/upload", "POST", exchange -> {
                    int result = exchange.getAndSaveAt("file", new String[]{"png", "jpg"}, file -> {
                        String filePath = exchange.saveFileSafe(file, "./fileUploadDemo/uploads", true);
                        webServer.serveFile("/pic", filePath);
                        exchange.redirect("/");
                    });

                    switch (result) {
                        case -1 -> exchange.sendBadRequestResponse("Not multipart");      // Client error
                        case -2 -> exchange.sendBadRequestResponse("File missing");       // Client error
                        case -3 -> exchange.sendBadRequestResponse("Invalid extension");  // Client error
                        case -4 -> exchange.sendBadRequestResponse("Invalid file name");  // Path traversal attempt
                        case 1 -> {
                        }
                    }
                })
                .limitUploadSize(10 * 1024 * 1024)
                .serveFile("/pic", "./fileUploadDemo/uploads/default.png")
                .head(exchange -> {
                    exchange.setAttribute("start_time", System.currentTimeMillis());
                    return true;
                })
                .tail(exchange -> {
                    LoggerManager.quickLog(FileUploadTest.class, "Request took " + (System.currentTimeMillis() - exchange.getAttribute("start_time", Long.class)) + " ms");
                })
                .onException((exchange, exception) -> {
                    exchange.sendErrorResponse(exception.getMessage());
                }).start();
    }
}