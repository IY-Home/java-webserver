package com.youfuns.webserver.servers;

import com.youfuns.logger.SimpleLogger;

import java.util.function.Function;

public enum WebServerType {
    SUN_NET_HTTPSERVER(NetHttpServer::new),
    UNDERTOW_SERVER(null);

    private final Function<SimpleLogger, WebServerInterface<?, ?, ?>> serverInterfaceSupplier;

    WebServerType(Function<SimpleLogger, WebServerInterface<?, ?, ?>> serverInterfaceSupplier) {
        this.serverInterfaceSupplier = serverInterfaceSupplier;
    }

    public WebServerInterface<?, ?, ?> getServerInterface(SimpleLogger logger) {
        return serverInterfaceSupplier.apply(logger);
    }
}
