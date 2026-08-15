package com.youfuns.webserver;

import com.youfuns.logger.ConsoleLogger;
import com.youfuns.logger.SimpleLogger;
import com.youfuns.webserver.interfaces.*;
import com.youfuns.webserver.servers.ExchangeHandlerInterface;
import com.youfuns.webserver.servers.WebServerInterface;
import com.youfuns.webserver.servers.WebServerType;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebServer<S, I, H> {
    protected final WebServerInterface<S, I, H> serverInterface;

    protected final S server;

    protected final ExchangeHandlerInterface<I> exchangeInterface;

    protected SimpleLogger logger;

    private final Map<String, InternalDynamicHandler<I>> dynamicHandlers;

    private final InternalHomeHandler<I> homeHandler;

    private final HeadsAndTails<I> headsAndTails;

    private ExceptionHandler<I> exceptionHandler;

    public WebServer(WebServerInterface<S, I, H> serverInterface, int port) {
        this(serverInterface, port, new ConsoleLogger());
    }

    public WebServer(WebServerInterface<S, I, H> serverInterface, int port, SimpleLogger logger) {
        this.logger = logger;

        if (port < 0 || port > 65535) {
            logger.log(WebServer.class, "Invalid port number: " + port, SimpleLogger.Level.ERROR);
            throw new IllegalArgumentException(String.format("Invalid port number: %d", port));
        }

        this(serverInterface, new InetSocketAddress(port), logger);
    }

    public WebServer(WebServerInterface<S, I, H> serverInterface, InetSocketAddress address) {
        this(serverInterface, address, new ConsoleLogger());
    }

    public WebServer(WebServerInterface<S, I, H> serverInterface, InetSocketAddress address, SimpleLogger logger) {
        this.logger = logger;

        this.serverInterface = serverInterface;
        this.server = serverInterface.createServer(address, 0);

        logger.log(WebServer.class, "Starting Web Server...", SimpleLogger.Level.INFO);

        logger.log(WebServer.class, "Created Web Server at port " + address.getPort(), SimpleLogger.Level.INFO);

        this.headsAndTails = new HeadsAndTails<>();

        this.exceptionHandler = (exchange, exception) -> { exception.printStackTrace(); };

        this.exchangeInterface = serverInterface.getExchangeHandlerAdapters();

        dynamicHandlers = new HashMap<>();
        homeHandler = new InternalHomeHandler<>();
        serverInterface.createContext(server, "/", getInternalHandler(homeHandler));
    }

    @SuppressWarnings("unchecked")
    public static <S, I, H> WebServer<S, I, H> create(WebServerType serverType, InetSocketAddress address, SimpleLogger logger) {
        return new WebServer<S, I, H>((WebServerInterface<S, I, H>) serverType.getServerInterface(logger), address, logger);
    }

    public static <S, I, H> WebServer<S, I, H> create(WebServerType serverType, InetSocketAddress address) {
        return create(serverType, address, new ConsoleLogger());
    }

    public static <S, I, H> WebServer<S, I, H> create(WebServerType serverType, int address, SimpleLogger logger) {
        return create(serverType, new InetSocketAddress(address), logger);
    }

    public static <S, I, H> WebServer<S, I, H> create(WebServerType serverType, int address) {
        return create(serverType, new InetSocketAddress(address), new ConsoleLogger());
    }

    public static <S, I, H> WebServer<S, I, H> create(InetSocketAddress address, SimpleLogger logger) {
        return create(WebServerType.SUN_NET_HTTPSERVER, address, logger);
    }

    public static <S, I, H> WebServer<S, I, H> create(InetSocketAddress address) {
        return create(WebServerType.SUN_NET_HTTPSERVER, address, new ConsoleLogger());
    }

    public static <S, I, H> WebServer<S, I, H> create(int address, SimpleLogger logger) {
        return create(WebServerType.SUN_NET_HTTPSERVER, new InetSocketAddress(address), logger);
    }

    public static <S, I, H> WebServer<S, I, H> create(int address) {
        return create(WebServerType.SUN_NET_HTTPSERVER, new InetSocketAddress(address), new ConsoleLogger());
    }

    public WebServer<S, I, H> endpoint(String endpoint, String method, ExchangeHandler<I> action) {
        if (endpoint.isEmpty() || endpoint.equals("/")) {
            homeHandler.setRoot(method, action);
            logger.log(WebServer.class, "Created endpoint: " + endpoint, SimpleLogger.Level.INFO);
            return this;
        }
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        boolean alreadyExists = dynamicHandlers.containsKey(endpoint);
        dynamicHandlers.putIfAbsent(endpoint, new InternalDynamicHandler<I>().setOnNotFound(homeHandler.getNotFound()));
        InternalDynamicHandler<I> dynamicHandler = dynamicHandlers.get(endpoint);
        dynamicHandler.addPath(endpoint, method, action);
        if (!alreadyExists)
            createContextSafe(endpoint, dynamicHandler);
        logger.log(WebServer.class, "Created endpoint: " + endpoint, SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer<S, I, H> endpoint(String endpoint, ExchangeHandler<I> action) {
        return endpoint(endpoint, "DEFAULT", action);
    }


    public WebServer<S, I, H> removeEndpoint(String endpoint) {
        serverInterface.removeContext(server, endpoint);
        logger.log(WebServer.class, "Removed endpoint: " + endpoint, SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer<S, I, H> dynamicEndpoint(String template, String method, DynamicExchangeHandler<I> action) {
        int index = template.indexOf('$');
        String endpoint = index == -1 ? template : template.substring(0, index);
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        boolean alreadyExists = dynamicHandlers.containsKey(endpoint);
        dynamicHandlers.putIfAbsent(endpoint, new InternalDynamicHandler<I>().setOnNotFound(homeHandler.getNotFound()));
        InternalDynamicHandler<I> dynamicHandler = dynamicHandlers.get(endpoint);
        dynamicHandler.addPath(template, method, action);
        if (!alreadyExists)
            createContextSafe(endpoint, dynamicHandler);
        logger.log(WebServer.class, "Created dynamic endpoint: " + template, SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer<S, I, H> dynamicEndpoint(String template, DynamicExchangeHandler<I> action) {
        return dynamicEndpoint(template, "DEFAULT", action);
    }

    public WebServer<S, I, H> on(String endpoint, String method, ExchangeHandler<I> action) {
        return endpoint(endpoint, method, action);
    }

    public WebServer<S, I, H> on(String template, String method, DynamicExchangeHandler<I> action) {
        return dynamicEndpoint(template, method, action);
    }

    public WebServer<S, I, H> on(String endpoint, ExchangeHandler<I> action) {
        return on(endpoint, "DEFAULT", action);
    }

    public WebServer<S, I, H> on(String template, DynamicExchangeHandler<I> action) {
        return on(template, "DEFAULT", action);
    }

    public WebServer<S, I, H> on(String endpoint, String method, String response) {
        return on(endpoint, method, exchange -> exchange.sendResponse(response));
    }

    public WebServer<S, I, H> on(String endpoint, String response) {
        return on(endpoint, "DEFAULT", response);
    }

    public WebServer<S, I, H> onNotFound(ExchangeHandler<I> action) {
        homeHandler.setNotFound(action);
        for (InternalDynamicHandler<I> dynamicHandler : dynamicHandlers.values()) {
            dynamicHandler.setOnNotFound(action);
        }
        logger.log(WebServer.class, "Created not found endpoint", SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer<S, I, H> serveStatic(String path, String directory) {
        return serveStatic(path, directory, false, null);
    }

    public WebServer<S, I, H> serveStatic(String path, String directory, boolean directoryListing, String indexFile) {
        // Ensure path starts with /
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        // Ensure path doesn't end with /
        if (normalizedPath.endsWith("/") && normalizedPath.length() > 1) {
            normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
        }

        // Expand user home (~) if present
        String expandedDir = directory.replace("~", System.getProperty("user.home"));

        StaticFileHandler<I> handler = new StaticFileHandler<>(exchangeInterface, expandedDir, normalizedPath, logger, directoryListing, indexFile);

        // Also serve the root of the static path
        if (!normalizedPath.equals("/")) {
            createContextSafe(normalizedPath, handler);
            String rootPath = normalizedPath + "/";
            createContextSafe(rootPath, handler);
        }

        if (normalizedPath.equals("/")) {
            homeHandler.setRoot("DEFAULT", handler);
        }

        logger.log(WebServer.class, "Serving static files from " + directory + " at " + normalizedPath, SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer<S, I, H> serveFile(String path, String filePath) {
        if (path.isEmpty() || path.equals("/")) {
            homeHandler.setRoot("DEFAULT", exchange -> exchange.serveFile(filePath));
        } else {
            createContextSafe(path, exchange -> exchange.serveFile(filePath));
        }

        logger.log(WebServer.class, "Serving file " + filePath + " at " + path, SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer<S, I, H> serveFileResource(String path, String resourcePath) {
        if (path.isEmpty() || path.equals("/")) {
            homeHandler.setRoot("DEFAULT", serveFileResourceHandler(resourcePath));
        } else {
            createContextSafe(path, serveFileResourceHandler(resourcePath));
        }

        logger.log(WebServer.class, "Serving file " + resourcePath + " at " + path, SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer<S, I, H> onNotFoundServe(String filePath) {
        homeHandler.setNotFound(exchange -> exchange.serveFile(filePath));
        for (InternalDynamicHandler<I> dynamicHandler : dynamicHandlers.values()) {
            dynamicHandler.setOnNotFound(exchange -> exchange.serveFile(filePath));
        }
        logger.log(WebServer.class, "Serving file " + filePath + " on not found", SimpleLogger.Level.ERROR);
        return this;
    }

    public WebServer<S, I, H> head(HeadHandler<I> action) {
        headsAndTails.addHead(action);
        logger.log(WebServer.class, "Set request head", SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer<S, I, H> removeHead(HeadHandler<I> action) {
        headsAndTails.removeHead(action);
        logger.log(WebServer.class, "Removed request head", SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer<S, I, H> removeAllHeads() {
        headsAndTails.clearHeads();
        logger.log(WebServer.class, "Removed all heads", SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer<S, I, H> tail(ExchangeHandler<I> action) {
        headsAndTails.addTail(action);
        logger.log(WebServer.class, "Set request tail", SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer<S, I, H> removeTail(ExchangeHandler<I> action) {
        headsAndTails.removeTail(action);
        logger.log(WebServer.class, "Removed request tail", SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer<S, I, H> removeAllTails() {
        headsAndTails.clearTails();
        logger.log(WebServer.class, "Removed all tails", SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer<S, I, H> limitUploadSize(int fileSize) {
        Exchange.setFileUploadLimit(fileSize);
        return this;
    }

    public WebServer<S, I, H> onException(ExceptionHandler<I> exceptionHandler) {
        this.exceptionHandler = exceptionHandler;
        return this;
    }

    public WebServer<S, I, H> start() {
        logger.log(WebServer.class, "Starting Web Server...", SimpleLogger.Level.INFO);

        // Virtual threads for better scalability
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        serverInterface.setExecutor(server, executor);

        serverInterface.start(server);
        logger.log(WebServer.class, "Started Web Server", SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer<S, I, H> stop() {
        logger.log(WebServer.class, "Stopping Web Server...", SimpleLogger.Level.INFO);
        serverInterface.stop(server, 0);
        logger.log(WebServer.class, "Stopped Web Server", SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer<S, I, H> stop(int delay) {
        logger.log(WebServer.class, "Stopping Web Server...", SimpleLogger.Level.INFO);
        serverInterface.stop(server, delay);
        logger.log(WebServer.class, "Stopped Web Server", SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer<S, I, H> restart() {
        logger.log(WebServer.class, "Restarting Web Server...", SimpleLogger.Level.INFO);
        serverInterface.stop(server, 0);
        serverInterface.start(server);
        logger.log(WebServer.class, "Restarted Web Server", SimpleLogger.Level.INFO);
        return this;
    }

    private ExchangeHandler<I> serveFileResourceHandler(String resourcePath) {
        return exchange -> {
            try (InputStream is = getClass().getClassLoader()
                    .getResourceAsStream(resourcePath)) {
                if (is == null) {
                    logger.log(WebServer.class, "InputStream is null: " + resourcePath, SimpleLogger.Level.ERROR);
                    throw new IllegalArgumentException("File does not exist: " + resourcePath);
                }
                byte[] data = is.readAllBytes();

                String mimeType = URLConnection.getFileNameMap()
                        .getContentTypeFor(resourcePath);
                if (mimeType == null) mimeType = "application/octet-stream";

                exchange.addResponseHeader("Content-Type", mimeType);
                exchange.addResponseHeader("Content-Length", String.valueOf(data.length));
                exchange.serveFile(data);
            } catch (IOException e) {
                logger.log(WebServer.class, "Failed to serve file from resource: " + e.getMessage(), SimpleLogger.Level.ERROR);
                exchange.sendErrorResponse(e.getMessage());
            }
        };
    }

    public static void createIfNotExists(String directory) {
        try {
            Path dir = Paths.get(directory);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            throw new RuntimeException("The directory could not be created: " + directory, e);
        }
    }

    public WebServer<S, I, H> ensureExists(String directory) {
        try {
            Path dir = Paths.get(directory);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                logger.log(WebServer.class, "Created directory: " + directory, SimpleLogger.Level.DEBUG);
            } else {
                logger.log(WebServer.class, "Directory exists: " + directory, SimpleLogger.Level.DEBUG);
            }
        } catch (IOException e) {
            throw new RuntimeException("The directory could not be created: " + directory, e);
        }
        return this;
    }

    private void createContextSafe(String endpoint, ExchangeHandler<I> handler) {
        serverInterface.createContext(server, endpoint, getInternalHandler(handler));
    }

    private H getInternalHandler(ExchangeHandler<I> handler) {
        return serverInterface.createInternalHandler((I iExchange) -> {
                    exchangeInterface.handleExchange(exchangeInterface.createExchange(iExchange), headsAndTails, handler, exceptionHandler);
                }
        );
    }
}