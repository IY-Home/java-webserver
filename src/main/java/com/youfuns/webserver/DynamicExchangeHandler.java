package com.youfuns.webserver;

import java.io.IOException;

@FunctionalInterface
public interface DynamicExchangeHandler {
    void handle(String[] urlParams, Exchange exchange) throws IOException;
}