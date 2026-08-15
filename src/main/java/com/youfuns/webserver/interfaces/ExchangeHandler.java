package com.youfuns.webserver.interfaces;

import java.io.IOException;

@FunctionalInterface
public interface ExchangeHandler<InternalExchange> {
    void handle(Exchange<InternalExchange> exchange) throws IOException;
}