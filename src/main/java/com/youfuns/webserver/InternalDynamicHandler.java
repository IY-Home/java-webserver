package com.youfuns.webserver;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class InternalDynamicHandler implements InternalHandler {
    private final TemplateMatcher templateMatcher;
    private final Map<Map.Entry<String, String>, DynamicExchangeHandler> dynamicPaths;
    private final Map<Map.Entry<String, String>, ExchangeHandler> paths;
    private ExchangeHandler onNotFound;

    public InternalDynamicHandler() {
        super();
        this.dynamicPaths = new HashMap<>();
        this.paths = new HashMap<>();
        this.templateMatcher = new TemplateMatcher('$');
    }

    public void addPath(String template, DynamicExchangeHandler dynamicExchangeHandler) {
        dynamicPaths.put(Map.entry(template, "DEFAULT"), dynamicExchangeHandler);
    }

    public void addPath(String template, ExchangeHandler exchangeHandler) {
        paths.put(Map.entry(template, "DEFAULT"), exchangeHandler);
    }

    public void addPath(String template, String method, DynamicExchangeHandler dynamicExchangeHandler) {
        dynamicPaths.put(Map.entry(template, method), dynamicExchangeHandler);
    }

    public void addPath(String template, String method, ExchangeHandler exchangeHandler) {
        paths.put(Map.entry(template, method), exchangeHandler);
    }

    public InternalDynamicHandler setOnNotFound(ExchangeHandler exchangeHandler) {
        onNotFound = exchangeHandler;
        return this;
    }

    @Override
    public void handle(Exchange exchange) throws IOException {
        String address = exchange.getRequestPath();

        Map<String, DynamicExchangeHandler> defaultTemplateHandlers = new HashMap<>();
        for (Map.Entry<Map.Entry<String, String>, DynamicExchangeHandler> entry : dynamicPaths.entrySet()) {
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
            if (extracted != null && extracted.length > 0) {
                entry.getValue().handle(extracted, exchange);
                return;
            }
        }

        for (Map.Entry<String, DynamicExchangeHandler> entry : defaultTemplateHandlers.entrySet()) {
            String template = entry.getKey();
            String[] extracted = templateMatcher.extractValues(template, address);
            if (extracted != null && extracted.length > 0) {
                entry.getValue().handle(extracted, exchange);
                return;
            }
        }


        Map<String, ExchangeHandler> defaultPathHandlers = new HashMap<>();
        String normalizedAddress = address.endsWith("/") ? address.substring(0, address.length() - 1) : address;
        for (Map.Entry<Map.Entry<String, String>, ExchangeHandler> entry : paths.entrySet()) {
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

        for (Map.Entry<String, ExchangeHandler> entry : defaultPathHandlers.entrySet()) {
            String path = entry.getKey();
            String normalizedPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;

            if (normalizedAddress.equals(normalizedPath)) {
                entry.getValue().handle(exchange);
                return;
            }
        }

        if (onNotFound != null) { onNotFound.handle(exchange); }
        else { exchange.sendNotFoundResponse(); }
    }
}