package com.youfuns.webserver;

import com.youfuns.webserver.interfaces.DynamicExchangeHandler;
import com.youfuns.webserver.interfaces.Exchange;
import com.youfuns.webserver.interfaces.ExchangeHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InternalDynamicHandler<I> implements ExchangeHandler<I> {
    private final TemplateMatcher templateMatcher;
    private final Map<Map.Entry<String, String>, DynamicExchangeHandler<I>> dynamicPaths;
    private final Map<Map.Entry<String, String>, ExchangeHandler<I>> paths;
    private ExchangeHandler<I> onNotFound;

    public InternalDynamicHandler() {
        super();
        this.dynamicPaths = new HashMap<>();
        this.paths = new HashMap<>();
        this.templateMatcher = new TemplateMatcher('$');
    }

    public void addPath(String template, DynamicExchangeHandler<I> dynamicExchangeHandler) {
        dynamicPaths.put(Map.entry(template, "DEFAULT"), dynamicExchangeHandler);
    }

    public void addPath(String template, ExchangeHandler<I> exchangeHandler) {
        paths.put(Map.entry(template, "DEFAULT"), exchangeHandler);
    }

    public void addPath(String template, String method, DynamicExchangeHandler<I> dynamicExchangeHandler) {
        dynamicPaths.put(Map.entry(template, method), dynamicExchangeHandler);
    }

    public void addPath(String template, String method, ExchangeHandler<I> exchangeHandler) {
        paths.put(Map.entry(template, method), exchangeHandler);
    }

    public void removePath(String template) {
        List<Map.Entry<String, String>> pathsToRemove = new ArrayList<>();
        for (Map.Entry<String, String> entry : paths.keySet()) {
            if (entry.getValue().equals(template)) pathsToRemove.add(entry);
        }
        for (Map.Entry<String, String> path : pathsToRemove) {
            paths.remove(path);
        }
        pathsToRemove.clear();
        for (Map.Entry<String, String> entry : dynamicPaths.keySet()) {
            if (entry.getValue().equals(template)) pathsToRemove.add(entry);
        }
        for (Map.Entry<String, String> path : pathsToRemove) {
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
        for (Map.Entry<Map.Entry<String, String>, DynamicExchangeHandler<I>> entry : dynamicPaths.entrySet()) {
            Map.Entry<String, String> key = entry.getKey();
            String requiredMethod = key.getValue().toLowerCase();
            String template = key.getKey();
            if (requiredMethod.equals("default")) {
                defaultTemplateHandlers.put(template, entry.getValue());
                continue;
            }
            if (!exchange.getHttpMethod().toLowerCase().equals(requiredMethod)) {
                continue;
            }
            String[] extracted = templateMatcher.extractValues(template, address);
            String[] extracted2 = extracted != null && extracted.length > 0 ? extracted : templateMatcher.extractValues(template, matchableAddress);
            if (extracted2 != null && extracted2.length > 0) {
                entry.getValue().handle(extracted2, exchange);
                return;
            }
        }

        for (Map.Entry<String, DynamicExchangeHandler<I>> entry : defaultTemplateHandlers.entrySet()) {
            String template = entry.getKey();
            String[] extracted = templateMatcher.extractValues(template, address);
            String[] extracted2 = extracted != null && extracted.length > 0 ? extracted : templateMatcher.extractValues(template, matchableAddress);
            if (extracted2 != null && extracted2.length > 0) {
                entry.getValue().handle(extracted2, exchange);
                return;
            }
        }


        Map<String, ExchangeHandler<I>> defaultPathHandlers = new HashMap<>();
        String normalizedAddress = address.endsWith("/") ? address.substring(0, address.length() - 1) : address;
        for (Map.Entry<Map.Entry<String, String>, ExchangeHandler<I>> entry : paths.entrySet()) {
            Map.Entry<String, String> key = entry.getKey();
            String requiredMethod = key.getValue().toLowerCase();
            String path = key.getKey();
            if (requiredMethod.equals("default")) {
                defaultPathHandlers.put(path, entry.getValue());
                continue;
            }
            if (!exchange.getHttpMethod().toLowerCase().equals(requiredMethod)) {
                continue;
            }
            // Normalize both paths (remove trailing slash for comparison)
            String normalizedPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;

            if (normalizedAddress.equals(normalizedPath)) {
                entry.getValue().handle(exchange);
                return;
            }
        }

        for (Map.Entry<String, ExchangeHandler<I>> entry : defaultPathHandlers.entrySet()) {
            String path = entry.getKey();
            String normalizedPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;

            if (normalizedAddress.equals(normalizedPath)) {
                entry.getValue().handle(exchange);
                return;
            }
        }

        if (onNotFound != null) { onNotFound.handle(exchange); }
        else { exchange.sendNotFound(); }
    }
}