package com.youfuns.webserver;

import java.io.IOException;

@FunctionalInterface
public interface FileAction<T> {
    void accept(T t) throws IOException;
}
