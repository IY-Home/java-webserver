package com.youfuns.webserver.interfaces;

import java.io.IOException;

@FunctionalInterface
public interface DynamicExchangeHandler<InternalExchange> {
    void handle(String[] urlParams, Exchange<InternalExchange> exchange) throws IOException;
}