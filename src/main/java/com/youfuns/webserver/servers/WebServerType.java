package com.youfuns.webserver.servers;

import com.youfuns.logger.SimpleLogger;

import java.util.function.Supplier;

public enum WebServerType {
    SUN_NET_HTTPSERVER(NetHttpServer::new),
    UNDERTOW_SERVER(null);

    private final Supplier<WebServerInterface<?, ?, ?>> serverInterfaceSupplier;

    WebServerType(Supplier<WebServerInterface<?, ?, ?>> serverInterfaceSupplier) {
        this.serverInterfaceSupplier = serverInterfaceSupplier;
    }

    public WebServerInterface<?, ?, ?> getServerInterface(SimpleLogger logger) {
        return serverInterfaceSupplier.get();
    }
}
