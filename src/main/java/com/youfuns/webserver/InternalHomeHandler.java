package com.youfuns.webserver;

import com.youfuns.logger.SimpleLogger;
import com.youfuns.webserver.interfaces.DynamicExchangeHandler;
import com.youfuns.webserver.interfaces.Exchange;
import com.youfuns.webserver.interfaces.ExchangeHandler;

import java.io.IOException;

public class InternalHomeHandler<InternalExchange> implements ExchangeHandler<InternalExchange> {
    private final InternalDynamicHandler<InternalExchange> dynamicHandler;
    private ExchangeHandler<InternalExchange> notFound;
    private final SimpleLogger logger;

    public InternalHomeHandler(SimpleLogger logger) {
        super();
        this.logger = logger;
        this.dynamicHandler = new InternalDynamicHandler<>();
    }

    public void setRoot(ExchangeHandler<InternalExchange> root) {
        logger.log(this.getClass(), "Set root path", SimpleLogger.Level.DEBUG);
        this.dynamicHandler.addPath("/", root);
    }

    public void setRoot(String method, ExchangeHandler<InternalExchange> root) {
        logger.log(this.getClass(), "Set root path", SimpleLogger.Level.DEBUG);
        this.dynamicHandler.addPath("/", method, root);
    }

    public void setDynamicRoot(DynamicExchangeHandler<InternalExchange> root) {
        logger.log(this.getClass(), "Set dynamic root path", SimpleLogger.Level.DEBUG);
        this.dynamicHandler.addPath("/$", root);
    }

    public ExchangeHandler<InternalExchange> getNotFound() {
        return this.notFound;
    }

    public void setNotFound(ExchangeHandler<InternalExchange> notFound) {
        logger.log(this.getClass(), "Set not found handler", SimpleLogger.Level.DEBUG);
        this.notFound = notFound;
        this.dynamicHandler.setOnNotFound(notFound);
    }

    @Override
    public void handle(Exchange<InternalExchange> exchange) throws IOException {
        logger.log(this.getClass(), "Handling request to " + exchange.getRequestPath(), SimpleLogger.Level.DEBUG);
        dynamicHandler.handle(exchange);
    }
}