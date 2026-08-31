package com.youfuns.webserver.servers;

import com.youfuns.logger.SimpleLogger;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public interface WebServerInterface<Server, InternalExchange, InternalHandler> {
    // Note: Should have no-arg constructor

    Server createServer(InetSocketAddress address, int backlog);

    void createContext(Server server, String endpoint, InternalHandler handler);

    void removeContext(Server server, String endpoint);

    // If true, create/removeContext can be called. If not, only createContext will be called ONCE on "/".
    // If false, you should, upon any subsequent createContext calls or calls not to "/", or removeContext calls, throw UnsupportedOperationException.
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
