package com.youfuns.webserver.demo;

import com.youfuns.webserver.WebServer;

import java.util.Map;

public class Basic {
    public static void main(String[] args) {
        WebServer.create(8080)

        // Simple text response
        .on("/", "Hello, World!")

        // GET endpoint with manual handler
        .on("/greet", "GET", exchange -> {
            String name = exchange.getQueryParameter("name", "Stranger");
            exchange.send("Hello, " + name + "!");
        })

        // Sending JSON
        .on("/api/status", exchange -> exchange.sendJson(Map.of(
                "status", "running",
                "message", "Hello World!",
                "timestamp", System.currentTimeMillis()
        )))

        // Dynamic path
        .on("/users/?", (params, exchange) -> {
            String userId = params[0];
            exchange.send("User ID: " + userId);
        })

        // Default
        .onNotFound(exchange ->
                exchange.send(404, "Custom 404, page not found: " + exchange.getRequestPath()))

        // Start the server
        .start();
    }
}