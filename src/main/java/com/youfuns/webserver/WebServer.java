package com.youfuns.webserver;

import com.youfuns.logger.ConsoleLogger;
import com.youfuns.logger.SimpleLogger;
import com.youfuns.webserver.interfaces.*;
import com.youfuns.webserver.servers.ExchangeHandlerInterface;
import com.youfuns.webserver.servers.WebServerInterface;
import com.youfuns.webserver.servers.WebServerType;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class WebServer<S, I, H> {
    protected final WebServerInterface<S, I, H> serverInterface;

    protected final S server;

    protected final ExchangeHandlerInterface<I> exchangeInterface;

    protected SimpleLogger logger;

    private final Map<String, InternalDynamicHandler<I>> dynamicHandlers;

    private final InternalHomeHandler<I> homeHandler;

    private final HeadsAndTails<I> headsAndTails;

    private ExceptionHandler<I> exceptionHandler;

    private boolean started;

    private WebServer(WebServerInterface<S, I, H> serverInterface, InetSocketAddress address, SimpleLogger logger) {
        if (!Builder.isCurrentlyBuilding()) {
            throw new SecurityException("WebServer must be initialized by builder (WebServer.builder()).");
        }

        this.logger = logger;

        this.serverInterface = serverInterface;
        this.homeHandler = new InternalHomeHandler<>(logger);

        if (serverInterface.supportsMultipleContexts()) {
            S result = serverInterface.createServer(address, 0);
            if (result == null) {
                this.server = serverInterface.createServer(address, 0, getInternalHandler(homeHandler));
            } else {
                this.server = result;
                serverInterface.createContext(server, "/", getInternalHandler(homeHandler));
            }
        } else {
            S result = serverInterface.createServer(address, 0, getInternalHandler(homeHandler));
            if (result == null) {
                this.server = serverInterface.createServer(address, 0);
                serverInterface.createContext(server, "/", getInternalHandler(homeHandler));
            } else {
                this.server = result;
            }
        }

        logger.log(WebServer.class, "Starting Web Server...", SimpleLogger.Level.INFO);

        logger.log(WebServer.class, "Created Web Server at port " + address.getPort(), SimpleLogger.Level.INFO);

        this.headsAndTails = new HeadsAndTails<>();

        this.exceptionHandler = (exchange, exception) -> { exception.printStackTrace(); };

        this.exchangeInterface = serverInterface.getExchangeHandlerAdapters();

        dynamicHandlers = new HashMap<>();
    }

    public static WebServer<?, ?, ?> create(int port) {
        return builder().port(port).build();
    }

    public static WebServer<?, ?, ?> create(int port, SimpleLogger logger) {
        return builder().port(port).logger(logger).build();
    }

    public WebServer<S, I, H> endpoint(String endpoint, String[] method, ExchangeHandler<I> action) {
        checkContextAdditionAfterStart();
        if (endpoint.isEmpty() || endpoint.equals("/")) {
            for (String iMethod : method) homeHandler.setRoot(iMethod, action);
            logger.log(WebServer.class, "Created endpoint: " + endpoint, SimpleLogger.Level.INFO);
            return this;
        }
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        if (serverInterface.supportsMultipleContexts()) {
            boolean alreadyExists = dynamicHandlers.containsKey(endpoint);
            dynamicHandlers.putIfAbsent(endpoint, new InternalDynamicHandler<I>().setOnNotFound(homeHandler.getNotFound()));
            InternalDynamicHandler<I> dynamicHandler = dynamicHandlers.get(endpoint);
            for (String iMethod : method) dynamicHandler.addPath(endpoint, iMethod, action);
            if (!alreadyExists)
                createContextSafe(endpoint, dynamicHandler);
        } else {
            for (String iMethod : method) homeHandler.getDynamicHandler().addPath(endpoint, iMethod, action);
        }
        logger.log(WebServer.class, "Created endpoint: " + endpoint, SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer<S, I, H> endpoint(String endpoint, String method, ExchangeHandler<I> action) {
        return endpoint(endpoint, new String[]{method}, action);
    }

    public WebServer<S, I, H> endpoint(String endpoint, ExchangeHandler<I> action) {
        return endpoint(endpoint, "DEFAULT", action);
    }


    public WebServer<S, I, H> removeEndpoint(String endpoint) {
        checkContextAdditionAfterStart();
        if (serverInterface.supportsMultipleContexts()) serverInterface.removeContext(server, endpoint);
        else homeHandler.getDynamicHandler().removePath(endpoint);
        logger.log(WebServer.class, "Removed endpoint: " + endpoint, SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer<S, I, H> dynamicEndpoint(String template, String[] method, DynamicExchangeHandler<I> action) {
        checkContextAdditionAfterStart();
        int index = template.indexOf('$');
        String endpoint = index == -1 ? template : template.substring(0, index);
        if (endpoint.endsWith("/") && !endpoint.equals("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        if (serverInterface.supportsMultipleContexts()) {
            boolean alreadyExists = dynamicHandlers.containsKey(endpoint);
            dynamicHandlers.putIfAbsent(endpoint, new InternalDynamicHandler<I>().setOnNotFound(homeHandler.getNotFound()));
            InternalDynamicHandler<I> dynamicHandler = dynamicHandlers.get(endpoint);
            for (String iMethod : method) dynamicHandler.addPath(template, iMethod, action);
            if (!alreadyExists)
                createContextSafe(endpoint, dynamicHandler);
        } else {
            for (String iMethod : method) homeHandler.getDynamicHandler().addPath(template, iMethod, action);
        }
        logger.log(WebServer.class, "Created dynamic endpoint: " + template, SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer<S, I, H> dynamicEndpoint(String template, String method, DynamicExchangeHandler<I> action) {
        return dynamicEndpoint(template, new String[]{method}, action);
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

    public WebServer<S, I, H> on(String endpoint, String[] methods, ExchangeHandler<I> action) {
        endpoint(endpoint, methods, action);
        return this;
    }

    public WebServer<S, I, H> on(String template, String[] methods, DynamicExchangeHandler<I> action) {
        dynamicEndpoint(template, methods, action);
        return this;
    }

    public WebServer<S, I, H> on(String endpoint, ExchangeHandler<I> action) {
        return on(endpoint, "DEFAULT", action);
    }

    public WebServer<S, I, H> on(String template, DynamicExchangeHandler<I> action) {
        return on(template, "DEFAULT", action);
    }

    public WebServer<S, I, H> on(String endpoint, String method, String response) {
        return on(endpoint, method, (ExchangeHandler<I>) exchange -> exchange.send(response));
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
            homeHandler.setDynamicRoot((p, e) -> handler.handle(e));
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
        headsAndTails.addHead("/", action);
        logger.log(WebServer.class, "Set request head", SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer<S, I, H> head(String template, HeadHandler<I> action) {
        headsAndTails.addHead(template, action);
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
        headsAndTails.addTail("/", action);
        logger.log(WebServer.class, "Set request tail", SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer<S, I, H> tail(String template, ExchangeHandler<I> action) {
        headsAndTails.addTail(template, action);
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
        serverInterface.start(server);
        started = true;
        logger.log(WebServer.class, "Started Web Server", SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer<S, I, H> stop() {
        logger.log(WebServer.class, "Stopping Web Server...", SimpleLogger.Level.INFO);
        serverInterface.stop(server, 0);
        started = false;
        logger.log(WebServer.class, "Stopped Web Server", SimpleLogger.Level.INFO);
        return this;
    }

    public WebServer<S, I, H> stop(int delay) {
        logger.log(WebServer.class, "Stopping Web Server...", SimpleLogger.Level.INFO);
        serverInterface.stop(server, delay);
        started = false;
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
                exchange.sendError(e.getMessage());
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

    public boolean started() {
        return started;
    }

    private void createContextSafe(String endpoint, ExchangeHandler<I> handler) {
        if (serverInterface.supportsMultipleContexts()) serverInterface.createContext(server, endpoint, getInternalHandler(handler));
        else homeHandler.getDynamicHandler().addPath(endpoint, handler);
    }

    private H getInternalHandler(ExchangeHandler<I> handler) {
        return serverInterface.createInternalHandler((I iExchange) -> {
            try (Exchange<I> exchange = exchangeInterface.createExchange(iExchange)) {
                exchangeInterface.handleExchange(exchange, headsAndTails, handler, exceptionHandler);
            }
        });
    }

    private void checkContextAdditionAfterStart() {
        if (started && !serverInterface.supportsContextMutationAfterStart()) {
            throw new UnsupportedOperationException("Context addition after server start is not supported by the web server " + serverInterface.getClass().getSimpleName());
        }
    }

    public S getInternalServer() {
        return server;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder implements Serializable, Cloneable {
        private InetSocketAddress serverAddress;
        private SimpleLogger logger;
        private WebServerInterface<?, ?, ?> serverInterface;
        private static final ThreadLocal<Boolean> buildingFlag = ThreadLocal.withInitial(() -> false);

        private Builder() {
            this.serverAddress = null;
            this.logger = new ConsoleLogger();
            this.serverInterface = WebServerType.SUN_NET_HTTPSERVER.getServerInterface(logger);
        }

        static boolean isCurrentlyBuilding() {
            return buildingFlag.get();
        }

        public Builder port(int port) {
            this.serverAddress = new InetSocketAddress(port);
            return this;
        }

        public Builder port(String host, int port) {
            this.serverAddress = new InetSocketAddress(host, port);
            return this;
        }

        public Builder port(InetSocketAddress address) {
            this.serverAddress = address;
            return this;
        }

        public Builder logger(SimpleLogger logger) {
            this.logger = logger;
            this.serverInterface.setLogger(logger);
            return this;
        }

        public Builder server(WebServerInterface<?, ?, ?> serverInterface) {
            this.serverInterface = serverInterface;
            return this;
        }

        public Builder server(WebServerType serverType) {
            this.serverInterface = serverType.getServerInterface(logger);
            return this;
        }

        public WebServer<?, ?, ?> build() {
            if (serverAddress == null) {
                throw new IllegalStateException("Server port is not set");
            }
            buildingFlag.set(true);
            WebServer<?, ?, ?> server = new WebServer<>(serverInterface, serverAddress, logger);
            buildingFlag.set(false);
            return server;
        }

        @Override
        public Builder clone() {
            try {
                Builder clone = (Builder) super.clone();
                clone.serverAddress = new InetSocketAddress(serverAddress.getAddress(), serverAddress.getPort());
                clone.logger = logger;
                clone.serverInterface = serverInterface;
                return clone;
            } catch (CloneNotSupportedException e) {
                throw new AssertionError(e);
            }
        }
    }
}