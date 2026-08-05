package com.youfuns.webserver;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.youfuns.logger.DummyLogger;
import com.youfuns.logger.SimpleLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebServer {
    private final HttpServer server;

    private SimpleLogger logger;

    private final Map<String, InternalDynamicHandler> dynamicHandlers;

    private final InternalHomeHandler homeHandler;

    private final InternalHandlerWrapper.HooksAndTails hooksAndTails;

    private ExceptionHandler exceptionHandler;

    public WebServer(int port) {
        this(port, new DummyLogger());
    }

    public WebServer(int port, SimpleLogger logger) {
        this.logger = logger;

        if (port < 0 || port > 65535) {
            logger.log(WebServer.class, "Invalid port number: " + port, SimpleLogger.Level.ERROR);
            throw new IllegalArgumentException(String.format("Invalid port number: %d", port));
        }

        this(new InetSocketAddress(port), logger);
    }

    public WebServer(InetSocketAddress address) {
        this(address, new DummyLogger());
    }

    public WebServer(InetSocketAddress address, SimpleLogger logger) {
        this.logger = logger;

        logger.log(WebServer.class, "Starting HttpServer...", SimpleLogger.Level.INFO);

        try {
            this.server = HttpServer.create(address, 0);
        } catch (IOException e) {
            logger.log(WebServer.class, "HttpServer start failed. Encountered " + e.getClass().getSimpleName() + ": " + e.getMessage(), SimpleLogger.Level.ERROR);
            throw new RuntimeException("Failed to create HttpServer", e);
        }

        logger.log(WebServer.class, "Created HttpServer at port " + address.getPort(), SimpleLogger.Level.INFO);

        this.hooksAndTails = new InternalHandlerWrapper.HooksAndTails();

        this.exceptionHandler = (exchange, exception) -> { exception.printStackTrace(); };

        dynamicHandlers = new HashMap<>();
        homeHandler = new InternalHomeHandler();
        createContextSafe("/", new InternalHandlerWrapper(homeHandler, hooksAndTails, () -> exceptionHandler, logger));
    }

    public WebServer endpoint(String endpoint, String method, ExchangeHandler action) {
        if (endpoint.isEmpty() || endpoint.equals("/")) {
            homeHandler.setRoot(method, action);
            logger.log(WebServer.class, "Created endpoint: " + endpoint, SimpleLogger.Level.INFO);
            return this;
        }
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        boolean alreadyExists = dynamicHandlers.containsKey(endpoint);
        dynamicHandlers.putIfAbsent(endpoint, new InternalDynamicHandler().setOnNotFound(homeHandler.getNotFound()));
        InternalDynamicHandler dynamicHandler = dynamicHandlers.get(endpoint);
        dynamicHandler.addPath(endpoint, method, action);
        if (!alreadyExists) createContextSafe(endpoint, new InternalHandlerWrapper(dynamicHandler, hooksAndTails, () -> exceptionHandler, logger));
        logger.log(WebServer.class, "Created endpoint: " + endpoint, SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer endpoint(String endpoint, ExchangeHandler action) {
        return endpoint(endpoint, "DEFAULT", action);
    }


    public WebServer removeEndpoint(String endpoint) {
        server.removeContext(endpoint);
        logger.log(WebServer.class, "Removed endpoint: " + endpoint, SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer dynamicEndpoint(String template, String method, DynamicExchangeHandler action) {
        int index = template.indexOf('$');
        String endpoint = index == -1 ? template : template.substring(0, index);
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        boolean alreadyExists = dynamicHandlers.containsKey(endpoint);
        dynamicHandlers.putIfAbsent(endpoint, new InternalDynamicHandler().setOnNotFound(homeHandler.getNotFound()));
        InternalDynamicHandler dynamicHandler = dynamicHandlers.get(endpoint);
        dynamicHandler.addPath(template, method, action);
        if (!alreadyExists) createContextSafe(endpoint, new InternalHandlerWrapper(dynamicHandler, hooksAndTails, () -> exceptionHandler, logger));
        logger.log(WebServer.class, "Created dynamic endpoint: " + template, SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer dynamicEndpoint(String template, DynamicExchangeHandler action) {
        return dynamicEndpoint(template, "DEFAULT", action);
    }

    public WebServer on(String endpoint, String method, ExchangeHandler action) {
        return endpoint(endpoint, method, action);
    }

    public WebServer on(String template, String method, DynamicExchangeHandler action) {
        return dynamicEndpoint(template, method, action);
    }

    public WebServer on(String endpoint, ExchangeHandler action) {
        return on(endpoint, "DEFAULT", action);
    }

    public WebServer on(String template, DynamicExchangeHandler action) {
        return on(template, "DEFAULT", action);
    }

    public WebServer on(String endpoint, String method, String response) {
        return on(endpoint, method, exchange -> exchange.sendResponse(response));
    }

    public WebServer on(String endpoint, String response) {
        return on(endpoint, "DEFAULT", response);
    }

    public WebServer onNotFound(ExchangeHandler action) {
        homeHandler.setNotFound(action);
        for (InternalDynamicHandler dynamicHandler : dynamicHandlers.values()) {
            dynamicHandler.setOnNotFound(action);
        }
        logger.log(WebServer.class, "Created not found endpoint", SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer serveStatic(String path, String directory) {
        return serveStatic(path, directory, false, null);
    }

    public WebServer serveStatic(String path, String directory, boolean directoryListing, String indexFile) {
        // Ensure path starts with /
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        // Ensure path doesn't end with /
        if (normalizedPath.endsWith("/") && normalizedPath.length() > 1) {
            normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
        }

        // Expand user home (~) if present
        String expandedDir = directory.replace("~", System.getProperty("user.home"));

        StaticFileHandler handler = new StaticFileHandler(expandedDir, normalizedPath, logger, directoryListing, indexFile);
        createContextSafe(normalizedPath, handler);

        // Also serve the root of the static path
        if (!normalizedPath.equals("/")) {
            String rootPath = normalizedPath + "/";
            createContextSafe(rootPath, handler);
        }

        logger.log(WebServer.class, "Serving static files from " + directory + " at " + normalizedPath, SimpleLogger.Level.INFO);
        return this;
    }


    public WebServer serveFile(String path, String filePath) {
        createContextSafe(path, new InternalHandlerWrapper(serveFileHandler(filePath), hooksAndTails, () -> exceptionHandler, logger));

        logger.log(WebServer.class, "Serving file " + filePath + " at " + path, SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer onNotFoundServe(String filePath) {
        homeHandler.setNotFound(serveFileHandler(filePath));
        for (InternalDynamicHandler dynamicHandler : dynamicHandlers.values()) {
            dynamicHandler.setOnNotFound(serveFileHandler(filePath));
        }
        logger.log(WebServer.class, "Serving file " + filePath + " on not found", SimpleLogger.Level.ERROR);
        return this;
    }

    public WebServer hook(HookHandler action) {
        hooksAndTails.addHook(action);
        logger.log(WebServer.class, "Set request hook", SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer removeHook(HookHandler action) {
        hooksAndTails.removeHook(action);
        logger.log(WebServer.class, "Removed request hook", SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer removeAllHooks() {
        hooksAndTails.clearHooks();
        logger.log(WebServer.class, "Removed all hooks", SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer tail(HookHandler action) {
        hooksAndTails.addTail(action);
        logger.log(WebServer.class, "Set request tail", SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer removeTail(HookHandler action) {
        hooksAndTails.removeTail(action);
        logger.log(WebServer.class, "Removed request tail", SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer removeAllTails() {
        hooksAndTails.clearTails();
        logger.log(WebServer.class, "Removed all tails", SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer limitUploadSize(int fileSize) {
        Exchange.setFileUploadLimit(fileSize);
        return this;
    }

    public WebServer onException(ExceptionHandler exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
        return this;
    }

    public WebServer start() {
        logger.log(WebServer.class, "Starting HttpServer...", SimpleLogger.Level.INFO);

        // Virtual threads for better scalability
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(executor);

        server.start();
        logger.log(WebServer.class, "Started HttpServer", SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer stop() {
        logger.log(WebServer.class, "Stopping HttpServer...", SimpleLogger.Level.INFO);
        server.stop(0);
        logger.log(WebServer.class, "Stopped HttpServer", SimpleLogger.Level.INFO);
        return this;
    }

    private void createContextSafe(String endpoint, HttpHandler handler) {
        try {
            server.createContext(endpoint, handler);
        } catch (IllegalArgumentException e) {
            logger.log(WebServer.class, "Endpoint already exists: " + endpoint + ", removing and recreating", SimpleLogger.Level.WARN);
            server.removeContext(endpoint);
            server.createContext(endpoint, handler);
        }

    }

    private ExchangeHandler serveFileHandler(String filePath) {
        String expandedPath = filePath.replace("~", System.getProperty("user.home"));
        Path file = Paths.get(expandedPath);
        if (!Files.exists(file) || Files.isDirectory(file)) {
            logger.log(WebServer.class, "File does not exist: " + filePath, SimpleLogger.Level.ERROR);
            throw new IllegalArgumentException("File does not exist: " + filePath);
        }
        return req -> {
            try {
                String mimeType = URLConnection.getFileNameMap().getContentTypeFor(file.toString());
                if (mimeType == null) mimeType = "application/octet-stream";

                req.addResponseHeader("Content-Type", mimeType);
                req.addResponseHeader("Content-Length", String.valueOf(Files.size(file)));
                req.addResponseHeader("Cache-Control", "max-age=3600");

                req.getUnderlyingHttpExchange().sendResponseHeaders(200, Files.size(file));
                try (OutputStream os = req.getUnderlyingHttpExchange().getResponseBody();
                     InputStream is = Files.newInputStream(file)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                }
            } catch (IOException e) {
                logger.log(WebServer.class, "Failed to serve file: " + e.getMessage(),  SimpleLogger.Level.ERROR);
            }};
    }
}