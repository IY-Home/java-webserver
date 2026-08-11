package com.youfuns.webserver.demo;

import com.youfuns.webserver.WebServer;

import java.util.Map;

public class Main {
    public static void main(String[] args) {
        // Create server on port 8080
        WebServer server = new WebServer(8080);

        // === Hello World endpoints ===

        // Simple text response
        server.on("/hello", "Hello, World!");

        // GET endpoint with manual handler
        server.on("/greet", "GET", exchange -> {
            String name = exchange.getQueryParameter("name", "Stranger");
            exchange.sendResponse("Hello, " + name + "!");
        });

        // JSON response
        server.on("/api/status", exchange -> {
            exchange.sendJsonResponse(Map.of(
                    "status", "running",
                    "message", "Hello World!",
                    "timestamp", System.currentTimeMillis()
            ));
        });

        // Dynamic parameter (using $)
        server.on("/users/$", (params, exchange) -> {
            String userId = params[0];
            exchange.sendResponse("User ID: " + userId);
        });

        // 404 handler
        server.onNotFound(exchange -> {
            exchange.sendResponse(404, "Page not found: " + exchange.getRequestPath());
        });

        // Start the server
        server.start();
    }
}