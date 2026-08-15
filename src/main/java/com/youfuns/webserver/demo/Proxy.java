package com.youfuns.webserver.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youfuns.logger.LoggerManager;
import com.youfuns.webserver.WebServer;
import com.youfuns.webserver.interfaces.Exchange;
import com.youfuns.webserver.servers.WebServerType;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Proxy {
    private static final String TARGET_URL = "http://localhost:9370"; // Do not add trailing slash at end
    private static final int PROXY_PORT = 5050;
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        var proxy = WebServer.builder().server(WebServerType.SUN_NET_HTTPSERVER).port(new InetSocketAddress(PROXY_PORT)).logger(LoggerManager.INSTANCE.getLogger()).build();

        // Use head to log ALL requests
        proxy.head(exchange -> {
            String method = exchange.getHttpMethod();
            String path = exchange.getRequestPath();

            LoggerManager.quickLog(Proxy.class, "=== [" + method + "] " + path + " ===");

            // Log headers
            Map<String, List<String>> headers = exchange.getRequestHeaderMap();
            if (headers != null && !headers.isEmpty()) {
                LoggerManager.quickLog(Proxy.class, "Headers:");
                for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                    LoggerManager.quickLog(Proxy.class,
                            "  " + entry.getKey() + ": " + String.join(", ", entry.getValue()));
                }
            }

            // Log body if present
            String body = exchange.getRequestBody();
            if (body != null && !body.isEmpty()) {
                try {
                    Object json = mapper.readValue(body, Object.class);
                    LoggerManager.quickLog(Proxy.class,
                            mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json));
                } catch (Exception e) {
                    LoggerManager.quickLog(Proxy.class, "Raw Body: " + body);
                }
            }

            return true; // Continue to handler
        });

        proxy.on("/$", (params, exchange) -> {
            String path = params[0]; // This is everything after the first /
            String method = exchange.getHttpMethod();
            String query = exchange.getQueryString();

            // Reconstruct the full path
            String fullPath = "/" + path + (query != null && !query.isEmpty() ? "?" + query : "");

            LoggerManager.quickLog(Proxy.class, "  Forwarding: " + method + " " + fullPath);

            String response = forwardRequest(method, fullPath, exchange);
            exchange.sendResponse(response);
        });

        proxy.start();
        LoggerManager.quickLog(Proxy.class,
                "Proxy running on http://localhost:" + PROXY_PORT +
                        " -> Target on " + TARGET_URL);
    }

    private static String forwardRequest(String method, String path, Exchange exchange) {
        try {
            URL url = new URL(TARGET_URL + path);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);

            // Copy all headers
            Map<String, List<String>> allHeaders = exchange.getRequestHeaderMap();
            if (allHeaders != null) {
                for (Map.Entry<String, List<String>> entry : allHeaders.entrySet()) {
                    String key = entry.getKey();
                    for (String value : entry.getValue()) {
                        conn.setRequestProperty(key, value);
                    }
                }
            }

            // Add proxy identification header
            conn.setRequestProperty("X-Forwarded-By", "JavaProxy");

            // Send body
            String body = exchange.getRequestBody();
            if (body != null && !body.isEmpty()) {
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                }
            }

            // Get response
            int statusCode = conn.getResponseCode();

            LoggerManager.quickLog(Proxy.class, " Response status: " + statusCode);

            // Copy response headers back
            conn.getHeaderFields().forEach((key, values) -> {
                if (key != null && !values.isEmpty()) {
                    for (String value : values) {
                        exchange.addResponseHeader(key, value);
                    }
                }
            });

            // Read response (handle errors too)
            try (InputStream is = statusCode < 400 ? conn.getInputStream() : conn.getErrorStream()) {
                if (is != null) {
                    String response = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                            .lines()
                            .collect(Collectors.joining("\n"));

                    LoggerManager.quickLog(Proxy.class, " Response length: " + response.length() + " bytes");
                    return response;
                }
                return "";
            }

        } catch (Exception e) {
            LoggerManager.quickLog(Proxy.class, " Error forwarding to target: " + e.getMessage());
            e.printStackTrace();
            return "{\"error\": \"Proxy error: " + e.getMessage() + "\"}";
        }
    }
}