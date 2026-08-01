package com.youfuns.webserver;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class InternalHomeHandler implements InternalHandler {
    private ExchangeHandler notFound;
    private InternalDynamicHandler dynamicHandler;

    public InternalHomeHandler() {
        super();
        this.dynamicHandler = new InternalDynamicHandler();
    }

    public void setRoot(ExchangeHandler root) {
        this.dynamicHandler.addPath("/", root);
    }

    public void setRoot(String method, ExchangeHandler root) {
        this.dynamicHandler.addPath("/", method, root);
    }

    public void setNotFound(ExchangeHandler notFound) {
        this.notFound = notFound;
        this.dynamicHandler.setOnNotFound(notFound);
    }

    public ExchangeHandler getNotFound() {
        return this.notFound;
    }

    @Override
    public void handle(Exchange exchange) throws IOException {
        String address = exchange.getRequestPath();

        if (address == null || address.isEmpty() || address.equals("/")) {
            dynamicHandler.handle(exchange);
        } else {
            if (notFound == null) exchange.sendNotFoundResponse();
            notFound.handle(exchange);
        }
    }
}