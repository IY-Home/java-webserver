package com.youfuns.webserver;

import com.youfuns.webserver.interfaces.Exchange;
import com.youfuns.webserver.interfaces.ExchangeHandler;

import java.io.IOException;

public class InternalHomeHandler<InternalExchange> implements ExchangeHandler<InternalExchange> {
    private final InternalDynamicHandler<InternalExchange> dynamicHandler;
    private ExchangeHandler<InternalExchange> notFound;

    public InternalHomeHandler() {
        super();
        this.dynamicHandler = new InternalDynamicHandler<>();
    }

    public void setRoot(ExchangeHandler<InternalExchange> root) {
        this.dynamicHandler.addPath("/", root);
    }

    public void setRoot(String method, ExchangeHandler<InternalExchange> root) {
        this.dynamicHandler.addPath("/", method, root);
    }

    public ExchangeHandler<InternalExchange> getNotFound() {
        return this.notFound;
    }

    public void setNotFound(ExchangeHandler<InternalExchange> notFound) {
        this.notFound = notFound;
        this.dynamicHandler.setOnNotFound(notFound);
    }

    @Override
    public void handle(Exchange<InternalExchange> exchange) throws IOException {
        String address = exchange.getRequestPath();

        if (address == null || address.isEmpty() || address.equals("/")) {
            dynamicHandler.handle(exchange);
        } else {
            if (notFound == null) {
                exchange.sendNotFoundResponse();
                return;
            }
            notFound.handle(exchange);
        }
    }
}