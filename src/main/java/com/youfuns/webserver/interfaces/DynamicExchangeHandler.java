package com.youfuns.webserver.interfaces;

import java.io.IOException;

@FunctionalInterface
public interface DynamicExchangeHandler {
    void handle(String[] urlParams, Exchange exchange) throws IOException;
}