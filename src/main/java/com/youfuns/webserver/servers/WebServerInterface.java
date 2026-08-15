package com.youfuns.webserver.servers;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public interface WebServerInterface<Server, InternalExchange, InternalHandler> {
    Server createServer(InetSocketAddress address, int backlog);

    void createContext(Server server, String endpoint, InternalHandler handler);

    void removeContext(Server server, String endpoint);

    InternalHandler createInternalHandler(Consumer<InternalExchange> handler);

    void setExecutor(Server server, ExecutorService executor);

    void start(Server server);

    void stop(Server server, int delay);

    ExchangeHandlerInterface<InternalExchange> getExchangeHandlerAdapters();
}
