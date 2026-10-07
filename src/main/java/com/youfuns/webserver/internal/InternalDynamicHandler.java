package com.youfuns.webserver.internal;

import com.youfuns.webserver.interfaces.DynamicExchangeHandler;
import com.youfuns.webserver.interfaces.Exchange;
import com.youfuns.webserver.interfaces.ExchangeHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InternalDynamicHandler<I> implements ExchangeHandler<I> {
    private final Map<Path, DynamicExchangeHandler<I>> dynamicPaths;
    private final Map<Path, ExchangeHandler<I>> paths;
    private ExchangeHandler<I> onNotFound;

    public InternalDynamicHandler() {
        super();
        this.dynamicPaths = new HashMap<>();
        this.paths = new HashMap<>();
    }

    public void addPath(String template, DynamicExchangeHandler<I> dynamicExchangeHandler) {
        dynamicPaths.put(new Path(template, "DEFAULT"), dynamicExchangeHandler);
    }

    public void addPath(String template, ExchangeHandler<I> exchangeHandler) {
        paths.put(new Path(template, "DEFAULT"), exchangeHandler);
    }

    public void addPath(String template, String method, DynamicExchangeHandler<I> dynamicExchangeHandler) {
        dynamicPaths.put(new Path(template, method), dynamicExchangeHandler);
    }

    public void addPath(String template, String method, ExchangeHandler<I> exchangeHandler) {
        paths.put(new Path(template, method), exchangeHandler);
    }

    public void removePath(String template) {
        List<Path> pathsToRemove = new ArrayList<>();
        for (Path entry : paths.keySet()) {
            if (entry.url().equals(template)) pathsToRemove.add(entry);
        }
        for (Path path : pathsToRemove) {
            paths.remove(path);
        }
        pathsToRemove.clear();
        for (Path entry : dynamicPaths.keySet()) {
            if (entry.url().equals(template)) pathsToRemove.add(entry);
        }
        for (Path path : pathsToRemove) {
            dynamicPaths.remove(path);
        }
    }

    public InternalDynamicHandler<I> setOnNotFound(ExchangeHandler<I> exchangeHandler) {
        onNotFound = exchangeHandler;
        return this;
    }

    @Override
    public void handle(Exchange<I> exchange) throws IOException {
        String address = exchange.getRequestPath();
        String matchableAddress = address.endsWith("/") ? address : address + "/";

        Map<String, DynamicExchangeHandler<I>> defaultTemplateHandlers = new HashMap<>();
        for (Map.Entry<Path, DynamicExchangeHandler<I>> pair : dynamicPaths.entrySet()) {
            Path path = pair.getKey();
            String requiredMethod = path.method().toLowerCase();
            String template = path.url();
            if (requiredMethod.equals("default")) {
                defaultTemplateHandlers.put(template, pair.getValue());
                continue;
            }
            if (!exchange.getHttpMethod().toLowerCase().equals(requiredMethod)) {
                continue;
            }
            String[] extracted = TemplateMatcher.extractValues(template, address);
            String[] extracted2 = extracted != null && extracted.length > 0 ? extracted : TemplateMatcher.extractValues(template, matchableAddress);
            if (extracted2 != null && extracted2.length > 0) {
                pair.getValue().handle(extracted2, exchange);
                return;
            }
        }

        for (Map.Entry<String, DynamicExchangeHandler<I>> pair : defaultTemplateHandlers.entrySet()) {
            String template = pair.getKey();
            String[] extracted = TemplateMatcher.extractValues(template, address);
            String[] extracted2 = extracted != null && extracted.length > 0 ? extracted : TemplateMatcher.extractValues(template, matchableAddress);
            if (extracted2 != null && extracted2.length > 0) {
                pair.getValue().handle(extracted2, exchange);
                return;
            }
        }


        Map<String, ExchangeHandler<I>> defaultPathHandlers = new HashMap<>();
        String normalizedAddress = address.endsWith("/") ? address.substring(0, address.length() - 1) : address;
        for (Map.Entry<Path, ExchangeHandler<I>> pair : paths.entrySet()) {
            Path path = pair.getKey();
            String requiredMethod = path.method().toLowerCase();
            String path = path.url();
            if (requiredMethod.equals("default")) {
                defaultPathHandlers.put(path, pair.getValue());
                continue;
            }
            if (!exchange.getHttpMethod().toLowerCase().equals(requiredMethod)) {
                continue;
            }
            // Normalize both paths (remove trailing slash for comparison)
            String normalizedPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;

            if (normalizedAddress.equals(normalizedPath)) {
                pair.getValue().handle(exchange);
                return;
            }
        }

        for (Map.Entry<String, ExchangeHandler<I>> pair : defaultPathHandlers.entrySet()) {
            String path = pair.getKey();
            String normalizedPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;

            if (normalizedAddress.equals(normalizedPath)) {
                pair.getValue().handle(exchange);
                return;
            }
        }

        if (onNotFound != null) { onNotFound.handle(exchange); }
        else { exchange.sendNotFound(); }
    }

    private record Path(String url, String method) {}
}