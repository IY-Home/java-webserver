package com.youfuns.webserver.demo;

import com.youfuns.logger.LoggerManager;
import com.youfuns.webserver.TemplateEngine;
import com.youfuns.webserver.WebServer;

public class Main {
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
                .on("/setLogo", (exchange) -> {
                    LoggerManager.quickLog(exchange.getQueryParameters().get("path"));
                    try {
                        myServer.serveFile("/logo", exchange.getQueryParameter("path", "./logos/default.png"));
                    } catch (IllegalArgumentException e) {
                        exchange.sendBadRequestResponse("File was not found");
                    }
                    exchange.sendResponse("Updated successfully");
                })
                .hook(exchange -> {
                            LoggerManager.quickLog("Received " + exchange.getHttpMethod() + " to " + exchange.getRequestPath());
                            return true;
                        }
                ).start();
    }
}