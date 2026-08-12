package com.youfuns.webserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.youfuns.logger.SimpleLogger;
import com.youfuns.webserver.interfaces.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class InternalHandlerWrapper implements HttpHandler {
    private final InternalHandler handler;
    private final HeadsAndTails headsAndTails;
    private final Supplier<ExceptionHandler> exceptionHandler; // Supplier for dynamic acquisition of ExceptionHandler
    private final SimpleLogger logger;

    public InternalHandlerWrapper(InternalHandler handler, HeadsAndTails headsAndTails, Supplier<ExceptionHandler> exceptionHandler, SimpleLogger logger) {
        this.handler = handler;
        this.headsAndTails = headsAndTails;
        this.exceptionHandler = exceptionHandler;
        this.logger = logger;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        Exchange exchange = new Exchange(httpExchange, logger);
        try {
            boolean headsPassed = true;

            // Process heads
            for (HeadHandler head : headsAndTails.heads) {
                if (!head.handle(exchange)) {
                    headsPassed = false;
                    break; // Head prevented further processing
                }
            }

            // Process the actual handler(
            if (headsPassed) handler.handle(exchange);

            // Process tails
            for (ExchangeHandler tail : headsAndTails.tails) {
                tail.handle(exchange);
            }
        } catch (Exception e) {
            exceptionHandler.get().handle(exchange, e);
        }
    }

    public static class HeadsAndTails {
        private List<HeadHandler> heads = new ArrayList<>();
        private List<ExchangeHandler> tails = new ArrayList<>();

        public List<HeadHandler> getHooks() {
            return heads;
        }

        public List<ExchangeHandler> getTails() {
            return tails;
        }

        public void addHead(HeadHandler head) {
            heads.add(head);
        }

        public void removeHead(HeadHandler head) {
            heads.remove(head);
        }

        public void clearHeads() {
            heads.clear();
        }

        public void addTail(ExchangeHandler tail) {
            tails.add(tail);
        }

        public void removeTail(ExchangeHandler tail) {
            tails.remove(tail);
        }

        public void clearTails() {
            tails.clear();
        }
    }
}