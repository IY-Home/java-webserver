package com.youfuns.webserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.youfuns.logger.SimpleLogger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import com.youfuns.webserver.interfaces.*;

public class InternalHandlerWrapper implements HttpHandler {
    private final InternalHandler handler;
    private final HooksAndTails hooksAndTails;
    private final Supplier<ExceptionHandler> exceptionHandler;
    private final SimpleLogger logger;

    public InternalHandlerWrapper(InternalHandler handler, HooksAndTails hooksAndTails, Supplier<ExceptionHandler> exceptionHandler, SimpleLogger logger) {
        this.handler = handler;
        this.hooksAndTails = hooksAndTails;
        this.exceptionHandler = exceptionHandler;
        this.logger = logger;
    }

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        Exchange exchange = new Exchange(httpExchange, logger);
        try {
            boolean hooksPassed = true;

            // Process hooks
            for (HookHandler hook : hooksAndTails.hooks) {
                if (!hook.handle(exchange)) {
                    hooksPassed = false;
                    break; // Hook prevented further processing
                }
            }

            // Process the actual handler
            if (hooksPassed) handler.handle(exchange);

            // Process tails
            for (HookHandler tail : hooksAndTails.tails) {
                if (!tail.handle(exchange)) {
                    break;
                }
            }
        } catch (Exception e) {
            exceptionHandler.get().handle(exchange, e);
        }
    }

    public static class HooksAndTails {
        private List<HookHandler> hooks = new ArrayList<>();
        private List<HookHandler> tails = new ArrayList<>();

        public List<HookHandler> getHooks() { return hooks; }
        public List<HookHandler> getTails() { return tails; }

        public void addHook(HookHandler hook) {
            hooks.add(hook);
        }

        public void removeHook(HookHandler hook) {
            hooks.remove(hook);
        }

        public void clearHooks() {
            hooks.clear();
        }

        public void addTail(HookHandler hook) {
            tails.add(hook);
        }

        public void removeTail(HookHandler hook) {
            tails.remove(hook);
        }

        public void clearTails() {
            tails.clear();
        }
    }
}