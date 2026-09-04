package com.youfuns.webserver.demo;

import com.youfuns.webserver.WebServer;

import java.util.Map;

public class Basic {
    public static void main(String[] args) {
        // Create server on port 8080
       WebServer.create(8080)

        // Simple text response
        .on("/hello", "Hello, World!")

        // GET endpoint with manual handler
        .on("/greet", "GET", exchange -> {
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
        })

        // Start the server
        .start();
    }
}