package com.youfuns.webserver.interfaces;

import java.io.IOException;

@FunctionalInterface
public interface HeadHandler {
    boolean handle(Exchange exchange) throws IOException;
}