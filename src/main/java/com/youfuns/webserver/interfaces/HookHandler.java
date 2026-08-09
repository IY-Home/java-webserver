package com.youfuns.webserver.interfaces;

import java.io.IOException;

@FunctionalInterface
public interface HookHandler {
    boolean handle(Exchange exchange) throws IOException;
}