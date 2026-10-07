package com.youfuns.webserver.internal;

import com.youfuns.webserver.interfaces.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InternalDynamicHandler<I> implements ExchangeHandler<I> {
    private final Map<Path, DynamicExchangeHandler<I>> dynamicPaths;
    private final Map<String, DynamicExchangeHandler<I>> dynamicPathsDefault;
    private final Map<Path, ExchangeHandler<I>> paths;
    private final Map<String, ExchangeHandler<I>> pathsDefault;
    private ExchangeHandler<I> onNotFound;

    public InternalDynamicHandler() {
        super();
        this.dynamicPaths = new HashMap<>();
        this.dynamicPathsDefault = new HashMap<>();
        this.paths = new HashMap<>();
        this.pathsDefault = new HashMap<>();
    }

    public void addPath(String template, DynamicExchangeHandler<I> dynamicExchangeHandler) {
        dynamicPathsDefault.put(template, dynamicExchangeHandler);
    }

    public void addPath(String template, ExchangeHandler<I> exchangeHandler) {
        pathsDefault.put(template, exchangeHandler);
    }

    public void addPath(String template, String method, DynamicExchangeHandler<I> dynamicExchangeHandler) {
        if (method.trim().equalsIgnoreCase("default")) dynamicPathsDefault.put(template, dynamicExchangeHandler);
        else dynamicPaths.put(new Path(template, method), dynamicExchangeHandler);
    }

    public void addPath(String template, String method, ExchangeHandler<I> exchangeHandler) {
        if (method.trim().equalsIgnoreCase("default")) pathsDefault.put(template, exchangeHandler);
        else paths.put(new Path(template, method), exchangeHandler);
    }

    public void removePath(String inputPath) {
        final String path = normalizePath(inputPath);
        dynamicPaths.keySet().removeIf(item -> normalizePath(item.url()).equals(path));
        dynamicPathsDefault.keySet().removeIf(item -> normalizePath(item).equals(path));
        paths.keySet().removeIf(item -> normalizePath(item.url()).equals(path));
        pathsDefault.keySet().removeIf(item -> normalizePath(item).equals(path));
    }

    public InternalDynamicHandler<I> setOnNotFound(ExchangeHandler<I> exchangeHandler) {
        onNotFound = exchangeHandler;
        return this;
    }

    @Override
    public void handle(Exchange<I> exchange) throws IOException {
        String address = exchange.getRequestPath();
        String matchableAddress = normalizePath(address);

        for (Map.Entry<Path, DynamicExchangeHandler<I>> pair : dynamicPaths.entrySet()) {
            Path path = pair.getKey();
            String requiredMethod = path.method().toLowerCase();
            String template = path.url();
            if (!exchange.getHttpMethod().toLowerCase().equals(requiredMethod)) {
                continue;
            }
            String[] extracted = TemplateMatcher.extractValues(template, address);
            String[] extracted2 = extracted.length > 0 && extracted[0] != null ? extracted : TemplateMatcher.extractValues(template, matchableAddress);
            if (extracted2.length > 0 && extracted2[0] != null) {
                pair.getValue().handle(extracted2, exchange);
                return;
            }
        }

        for (Map.Entry<String, DynamicExchangeHandler<I>> pair : dynamicPathsDefault.entrySet()) {
            String template = pair.getKey();
            String[] extracted = TemplateMatcher.extractValues(template, address);
            String[] extracted2 = extracted.length > 0 && extracted[0] != null ? extracted : TemplateMatcher.extractValues(template, matchableAddress);
            if (extracted2.length > 0 && extracted2[0] != null) {
                pair.getValue().handle(extracted2, exchange);
                return;
            }
        }

        for (Map.Entry<Path, ExchangeHandler<I>> pair : paths.entrySet()) {
            Path path = pair.getKey();
            String requiredMethod = path.method().toLowerCase();
            String url = path.url();
            if (!exchange.getHttpMethod().toLowerCase().equals(requiredMethod)) {
                continue;
            }
            String matchablePath = normalizePath(url);

            if (matchableAddress.equals(matchablePath)) {
                pair.getValue().handle(exchange);
                return;
            }
        }

        for (Map.Entry<String, ExchangeHandler<I>> pair : pathsDefault.entrySet()) {
            String path = pair.getKey();
            String matchablePath = normalizePath(path);

            if (matchableAddress.equals(matchablePath)) {
                pair.getValue().handle(exchange);
                return;
            }
        }

        if (onNotFound != null) { onNotFound.handle(exchange); }
        else { exchange.sendNotFound(); }
    }

    public static <I> void handleExchange(Exchange<I> exchange, HeadsAndTails<I> headsAndTails, ExchangeHandler<I> handler, ExceptionHandler<I> exceptionHandler) {
        try {
            boolean headsPassed = true;

            for (Map.Entry<String, HeadHandler<I>> head : headsAndTails.getHeads()) {
                if (!exchange.getRequestPath().replaceAll("^/|/$", "").startsWith(head.getKey().replaceAll("^/|/$", ""))) continue;
                if (!head.getValue().handle(exchange)) {
                    headsPassed = false;
                    break; // Head prevented further processing
                }
            }

            // Process the actual handler
            if (headsPassed) handler.handle(exchange);

        } catch (Exception e) {
            try {
                exceptionHandler.handle(exchange, e);
            } catch (IOException ignored) {

            }
        } finally {
            try {
                // Process tails
                for (Map.Entry<String, ExchangeHandler<I>> tail : headsAndTails.getTails()) {
                    if (!exchange.getRequestPath().replaceAll("^/|/$", "").startsWith(tail.getKey().replaceAll("^/|/$", ""))) continue;
                    tail.getValue().handle(exchange);
                }
            } catch (Exception e) {
                try {
                    exceptionHandler.handle(exchange, e);
                } catch (IOException ignored) {

                }
            }
        }
    }

    private static String normalizePath(String path) {
        path = path.trim();
        path = path.startsWith("/") ? path.substring(1) : path;
        path = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        return path;
    }

    private record Path(String url, String method) {}
}