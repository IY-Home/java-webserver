package com.youfuns.webserver.servers;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.youfuns.logger.ConsoleLogger;
import com.youfuns.logger.SimpleLogger;
import com.youfuns.webserver.interfaces.Exchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;


/**
 * JDK built-in HttpServer adapter for the WebServer framework.
 * Implements WebServerInterface for com.sun.net.httpserver.
 */
public class NetHttpServer implements WebServerInterface<HttpServer, HttpExchange, HttpHandler> {

    private SimpleLogger logger;
    private final ExchangeHandlerInterface<HttpExchange> exchangeHandler;

    public NetHttpServer() {
        this.logger = new ConsoleLogger();
        this.exchangeHandler = new NetExchangeHandler();
    }

    public void setLogger(SimpleLogger logger) {
        this.logger = logger;
        this.exchangeHandler.setLogger(logger);
    }

    @Override
    public HttpServer createServer(InetSocketAddress address, int backlog) {
        try {
            HttpServer server = HttpServer.create(address, backlog);
            // Virtual threads for better scalability
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            server.setExecutor(executor);
            return server;
        } catch (IOException e) {
            logger.log(this.getClass(), "HttpServer creation failed: " + e.getMessage(), SimpleLogger.Level.ERROR);
            throw new RuntimeException("Failed to create HttpServer", e);
        }
    }

    @Override
    public void createContext(HttpServer server, String endpoint, HttpHandler handler) {
        try {
            server.createContext(endpoint, handler);
            logger.log(this.getClass(), "Created context: " + endpoint, SimpleLogger.Level.DEBUG);
        } catch (IllegalArgumentException e) {
            logger.log(this.getClass(), "Context already exists: " + endpoint + ", removing and recreating", SimpleLogger.Level.WARN);
            server.removeContext(endpoint);
            server.createContext(endpoint, handler);
        }
    }

    @Override
    public void removeContext(HttpServer server, String endpoint) {
        server.removeContext(endpoint);
        logger.log(this.getClass(), "Removed context: " + endpoint, SimpleLogger.Level.DEBUG);
    }

    @Override
    public boolean supportsContextMutationAfterStart() {
        return true;
    }

    @Override
    public HttpHandler createInternalHandler(Consumer<HttpExchange> handler) {
        return exchange -> {
            try {
                handler.accept(exchange);
            } catch (Exception e) {
                logger.log(this.getClass(), "Error in internal handler: " + e.getMessage(), SimpleLogger.Level.ERROR, e);
                // Try to send an error response if possible
                try {
                    String errorBody = "{\"error\": \"Internal Server Error\"}";
                    byte[] errorBytes = errorBody.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(500, errorBytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(errorBytes);
                    }
                } catch (IOException ex) {
                    // Ignore - can't do much if response fails
                }
            }
        };
    }

    @Override
    public void start(HttpServer server) {
        server.start();
        logger.log(this.getClass(), "HttpServer started", SimpleLogger.Level.INFO);
    }

    @Override
    public void stop(HttpServer server, int delay) {
        server.stop(delay);
        logger.log(this.getClass(), "HttpServer stopped", SimpleLogger.Level.INFO);
    }

    @Override
    public ExchangeHandlerInterface<HttpExchange> getExchangeHandlerAdapters() {
        return exchangeHandler;
    }

    // ============================================================
    // Inner class: NetExchangeHandler - bridges HttpExchange to the framework
    // ============================================================

    private static class NetExchangeHandler implements ExchangeHandlerInterface<HttpExchange> {

        private SimpleLogger logger;

        public NetExchangeHandler() {
            this.logger = new ConsoleLogger();
        }

        public void setLogger(SimpleLogger logger) {
            this.logger = logger;
        }

        @Override
        public Exchange<HttpExchange> createExchange(HttpExchange httpExchange) {
            // Use the NEW generic constructor that takes all the raw data
            return new Exchange<>(
                    httpExchange.getRequestMethod(),           // method
                    httpExchange.getRequestURI(),              // requestUri
                    httpExchange.getProtocol(),                // protocol
                    httpExchange.getRemoteAddress(),           // remoteAddress
                    httpExchange.getRequestHeaders(),          // requestHeaderMap
                    logger,                                    // logger
                    this.extractBody(httpExchange),
                    this,                                      // iExchangeHandler (this!)
                    httpExchange                               // exchange (the raw HttpExchange)
            );
        }

        @Override
        public void serveFile(HttpExchange exchange, int statusCode, Map<String, String> headers, Path file) throws IOException {
            if (!Files.exists(file) || Files.isDirectory(file)) {
                throw new IllegalArgumentException("File does not exist or is a directory: " + file);
            }

            // Apply headers
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                exchange.getResponseHeaders().set(entry.getKey(), entry.getValue());
            }

            // Send headers
            long fileSize = Files.size(file);
            exchange.sendResponseHeaders(statusCode, fileSize);

            // Write file content
            try (OutputStream os = exchange.getResponseBody();
                 InputStream is = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
                os.flush();
            }
        }

        @Override
        public void serveFile(HttpExchange exchange, int statusCode, Map<String, String> headers, byte[] fileBytes) throws IOException {
            // Apply headers
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                exchange.getResponseHeaders().set(entry.getKey(), entry.getValue());
            }

            // Send headers
            exchange.sendResponseHeaders(statusCode, fileBytes.length);

            // Write content
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(fileBytes);
                os.flush();
            }
        }

        @Override
        public String extractBody(HttpExchange exchange) {
            // Check if multipart - don't read body for multipart requests
            String contentType = getHeaderCaseInsensitive(exchange, "Content-Type");
            boolean isMultipart = contentType != null && contentType.startsWith("multipart/form-data");

            if (isMultipart) {
                return "[multipart/form-data - stream preserved for parser]";
            }

            try (InputStream is = exchange.getRequestBody()) {
                byte[] bodyBytes = is.readAllBytes();
                return new String(bodyBytes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                logger.log(this.getClass(), "Failed to read request body: " + e.getMessage(), SimpleLogger.Level.WARN);
                return "";
            }
        }

        @Override
        public void sendResponse(HttpExchange exchange, int statusCode, Map<String, String> headers, String body) throws IOException {
            logger.log(this.getClass(), "Sending response headers...", SimpleLogger.Level.DEBUG);

            // Apply headers
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                exchange.getResponseHeaders().set(entry.getKey(), entry.getValue());
            }

            // Handle redirects (no body)
            if (statusCode >= 300 && statusCode < 400) {
                logger.log(this.getClass(), "Sending " + statusCode + " redirect...", SimpleLogger.Level.DEBUG);
                exchange.sendResponseHeaders(statusCode, -1);
                return;
            }

            logger.log(this.getClass(), "Sending response body: '" + (body.length() > 32 ? (body.substring(0, 32) + "...") : body) + "'", SimpleLogger.Level.DEBUG);
            // Normal response with body
            byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
                os.flush();
            }

            logger.log(this.getClass(), "Response sent.", SimpleLogger.Level.INFO);

        }

        @Override
        public boolean isMultipart(HttpExchange exchange) {
            String contentType = getHeaderCaseInsensitive(exchange, "Content-Type");
            return contentType != null && contentType.startsWith("multipart/form-data");
        }

        @Override
        public org.apache.commons.fileupload.RequestContext createFileUploadRequestContext(HttpExchange exchange) {
            return new ApacheHttpExchangeContext(exchange);
        }

        @Override
        public void closeExchange(HttpExchange exchange) {
            exchange.close();
        }

        // ============================================================
        // Helper: Case-insensitive header lookup
        // ============================================================

        private String getHeaderCaseInsensitive(HttpExchange exchange, String headerName) {
            for (Map.Entry<String, List<String>> entry : exchange.getRequestHeaders().entrySet()) {
                if (entry.getKey().equalsIgnoreCase(headerName)) {
                    List<String> values = entry.getValue();
                    return values != null && !values.isEmpty() ? values.get(0) : null;
                }
            }
            return null;
        }

        // ============================================================
        // Apache Commons FileUpload Context Bridge
        // ============================================================

        private static class ApacheHttpExchangeContext implements org.apache.commons.fileupload.RequestContext {
            private final HttpExchange exchange;

            public ApacheHttpExchangeContext(HttpExchange exchange) {
                this.exchange = exchange;
            }

            @Override
            public String getCharacterEncoding() {
                String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                if (contentType != null) {
                    for (String part : contentType.split(";")) {
                        String trimmed = part.trim();
                        if (trimmed.startsWith("charset=")) {
                            return trimmed.substring("charset=".length()).replace("\"", "");
                        }
                    }
                }
                return StandardCharsets.UTF_8.name();
            }

            @Override
            public String getContentType() {
                return exchange.getRequestHeaders().getFirst("Content-Type");
            }

            @Override
            public int getContentLength() {
                String length = exchange.getRequestHeaders().getFirst("Content-Length");
                if (length != null) {
                    try {
                        return Integer.parseInt(length);
                    } catch (NumberFormatException e) {
                        // Ignore
                    }
                }
                return -1;
            }

            @Override
            public InputStream getInputStream() throws IOException {
                // Returns the raw request body stream - critical for file uploads!
                return exchange.getRequestBody();
            }
        }
    }
}