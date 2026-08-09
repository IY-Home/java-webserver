package com.youfuns.webserver.interfaces;

import java.io.IOException;

@FunctionalInterface
public interface ExceptionHandler {
    void handle(Exchange exchange, Exception exception) throws IOException;
}