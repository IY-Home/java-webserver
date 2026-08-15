package com.youfuns.webserver.demo;

import com.youfuns.logger.LoggerManager;
import com.youfuns.logger.SimpleLogger;
import com.youfuns.webserver.WebServer;
import com.youfuns.webserver.servers.WebServerType;

import java.net.InetSocketAddress;
import java.util.Map;

public class Basic {
    public static void main(String[] args) {
        // Create serverInterface on port 8080
        var server = WebServer.create(8080);

        LoggerManager.INSTANCE.getLogger().setLogLevel(SimpleLogger.Level.DEBUG);

        // Simple text response
        server.on("/hello", "Hello, World!");

        // GET endpoint with manual handler
        server.on("/greet", "GET", exchange -> {
            String name = exchange.getQueryParameter("name", "Stranger");
            exchange.sendResponse("Hello, " + name + "!");
        }).on("/api/status", exchange -> {
            exchange.sendJsonResponse(Map.of(
                    "status", "running",
                    "message", "Hello World!",
                    "timestamp", System.currentTimeMillis()
            ));
        }).on("/users/$", (params, exchange) -> {
            String userId = params[0];
            exchange.sendResponse("User ID: " + userId);
        }).onNotFound(exchange -> {
            exchange.sendResponse(404, "Custom 404, page not found: " + exchange.getRequestPath());
        });

        // Start the serverInterface
        server.start();
    }
}