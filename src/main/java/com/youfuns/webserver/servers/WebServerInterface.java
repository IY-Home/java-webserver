package com.youfuns.webserver.servers;

import com.youfuns.logger.SimpleLogger;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public interface WebServerInterface<Server, InternalExchange, InternalHandler> {
    // Note: Should have no-arg constructor

    Server createServer(InetSocketAddress address, int backlog);

    default Server createServer(InetSocketAddress address, int backlog, InternalHandler handler) {
        return null;
    };

    void createContext(Server server, String endpoint, InternalHandler handler);

    void removeContext(Server server, String endpoint);

    // If true, create/removeContext can be called, and the server will be initialized with createServer(address, backlog). You can return null on the createServer method with handler parameter.
    // If false, the framework will NOT execute createContext or removeContext, and you can make them throw exceptions or leave them empty.
    // Instead, the framework will call the `createServer` method with the 3rd handler parameter once upon startup only. Leave the createServer method with no handler parameter null.
    // If false, the framework will use a single handler for all request routing. Performance might be slightly affected with many paths.
    boolean supportsMultipleContexts();

    boolean supportsContextMutationAfterStart(); // Whether addition/removal of endpoints after start is possible

    InternalHandler createInternalHandler(Consumer<InternalExchange> handler);

    void start(Server server);

    void stop(Server server, int delay);

    ExchangeHandlerInterface<InternalExchange> getExchangeHandlerAdapters();

    // Called right after creation. Loggers are required by WebServerInterface instances to internally log server operations.
    void setLogger(SimpleLogger logger);
}
