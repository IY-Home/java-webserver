package com.youfuns.webserver.interfaces;

import java.io.IOException;

@FunctionalInterface
public interface HeadHandler<InternalExchange> {
    boolean handle(Exchange<InternalExchange> exchange) throws IOException;
}