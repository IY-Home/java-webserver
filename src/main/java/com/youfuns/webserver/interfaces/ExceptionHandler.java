package com.youfuns.webserver.interfaces;

import java.io.IOException;

@FunctionalInterface
public interface ExceptionHandler<InternalExchange> {
    void handle(Exchange<InternalExchange> exchange, Exception exception) throws IOException;
}