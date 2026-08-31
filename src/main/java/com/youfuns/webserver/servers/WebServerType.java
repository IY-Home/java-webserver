package com.youfuns.webserver.servers;

import com.youfuns.logger.SimpleLogger;

import java.util.function.Supplier;

public enum WebServerType {
    SUN_NET_HTTPSERVER(NetHttpServer::new);

    // Import Undertow, remove .txt from the UndertowServer.java.txt file,
    // and uncomment the following line to use Undertow
    // UNDERTOW(UndertowServer::new);

    // To add your own server, put it here

    private final Supplier<WebServerInterface<?, ?, ?>> serverInterfaceSupplier;

    WebServerType(Supplier<WebServerInterface<?, ?, ?>> serverInterfaceSupplier) {
        this.serverInterfaceSupplier = serverInterfaceSupplier;
    }

    public WebServerInterface<?, ?, ?> getServerInterface(SimpleLogger logger) {
        return serverInterfaceSupplier.get();
    }
}
