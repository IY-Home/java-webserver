package com.youfuns.webserver.hotloading;

import java.io.Serializable;

public record Result(boolean success, short code, String message) implements Serializable {
}
