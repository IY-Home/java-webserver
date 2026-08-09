package com.youfuns.webserver.interfaces;

import java.io.IOException;

@FunctionalInterface
public interface ExchangeHandler extends InternalHandler {
    void handle(Exchange exchange) throws IOException;
}