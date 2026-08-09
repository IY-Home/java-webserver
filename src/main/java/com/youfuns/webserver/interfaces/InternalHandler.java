package com.youfuns.webserver.interfaces;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@FunctionalInterface
public interface InternalHandler {
    void handle(Exchange exchange) throws IOException;
}
