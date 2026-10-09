package com.youfuns.webserver.internal;

import com.youfuns.webserver.interfaces.*;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public class InternalDynamicHandler<I> implements ExchangeHandler<I> {
    private final Map<DynamicPath, DynamicExchangeHandler<I>> dynamicPaths;
    private final Map<DynamicPathDefault, DynamicExchangeHandler<I>> dynamicPathsDefault;
    private final Map<Path, ExchangeHandler<I>> paths;
    private final Map<String, ExchangeHandler<I>> pathsDefault;
    private ExchangeHandler<I> onNotFound;

    public InternalDynamicHandler() {
        this.dynamicPaths = new LinkedHashMap<>();
        this.dynamicPathsDefault = new LinkedHashMap<>();
        this.paths = new LinkedHashMap<>();
        this.pathsDefault = new LinkedHashMap<>();
    }

    public void addPath(String template, DynamicExchangeHandler<I> dynamicExchangeHandler) {
        dynamicPathsDefault.put(new DynamicPathDefault(TemplateMatcher.compileTemplate(cleanPath(template)), cleanPath(template)), dynamicExchangeHandler);
    }

    public void addPath(String path, ExchangeHandler<I> exchangeHandler) {
        pathsDefault.put(cleanPath(path), exchangeHandler);
    }

    public void addPath(String template, String method, DynamicExchangeHandler<I> dynamicExchangeHandler) {
        method = method.trim().toLowerCase();
        if (method.equals("default")) dynamicPathsDefault.put(new DynamicPathDefault(TemplateMatcher.compileTemplate(cleanPath(template)), cleanPath(template)), dynamicExchangeHandler);
        else dynamicPaths.put(new DynamicPath(TemplateMatcher.compileTemplate(cleanPath(template)),cleanPath(template), method), dynamicExchangeHandler);
    }

    public void addPath(String path, String method, ExchangeHandler<I> exchangeHandler) {
        method = method.trim().toLowerCase();
        if (method.equals("default")) pathsDefault.put(cleanPath(path), exchangeHandler);
        else paths.put(new Path(cleanPath(path), method), exchangeHandler);
    }

    public void removePath(String inputPath) {
        final String path = cleanPath(inputPath);
        dynamicPaths.keySet().removeIf(item -> cleanPath(item.original()).equals(path));
        dynamicPathsDefault.keySet().removeIf(item -> cleanPath(item.original).equals(path));
        paths.keySet().removeIf(item -> cleanPath(item.url()).equals(path));
        pathsDefault.keySet().removeIf(item -> cleanPath(item).equals(path));
    }

    public InternalDynamicHandler<I> setOnNotFound(ExchangeHandler<I> exchangeHandler) {
        onNotFound = exchangeHandler;
        return this;
    }

    @Override
    public void handle(Exchange<I> exchange) throws IOException {
        String address = exchange.getRequestPath();
        String matchableAddress = cleanPath(address);
        String method = exchange.getHttpMethod().trim().toLowerCase();

        var key = new Path(matchableAddress, method);
        if (paths.containsKey(key)) { paths.get(key).handle(exchange); return; }
        if (pathsDefault.containsKey(matchableAddress)) { pathsDefault.get(matchableAddress).handle(exchange); return; }

        for (var pair : dynamicPaths.entrySet()) {
            DynamicPath path = pair.getKey();
            String requiredMethod = path.method();
            TemplateMatcher.Template template = path.template();
            if (!method.equals(requiredMethod) || template == null) {
                continue;
            }
            String[] extracted = TemplateMatcher.extractValues(template, matchableAddress);
            if (extracted.length > 0 && extracted[0] != null) {
                pair.getValue().handle(extracted, exchange);
                return;
            }
        }

        for (var pair : dynamicPathsDefault.entrySet()) {
            DynamicPathDefault template = pair.getKey();
            String[] extracted = TemplateMatcher.extractValues(template.template, matchableAddress);
            if (extracted.length > 0 && extracted[0] != null) {
                pair.getValue().handle(extracted, exchange);
                return;
            }
        }

        if (onNotFound != null) onNotFound.handle(exchange);
        else exchange.sendNotFound();
    }


    public static <I> void handleExchange(Exchange<I> exchange, HeadsAndTails<I> headsAndTails, ExchangeHandler<I> handler, ExceptionHandler<I> exceptionHandler) {
        String normalizedPath = cleanPath(exchange.getRequestPath());
        try {
            boolean headsPassed = true;

            for (Map.Entry<String, HeadHandler<I>> head : headsAndTails.getHeads()) {
                if (!normalizedPath.startsWith(cleanPath(head.getKey()))) continue;
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
            } catch (IOException i) {
                i.addSuppressed(e);
                throw new RuntimeException(i);
            }
        } finally {
            try {
                // Process tails
                for (Map.Entry<String, ExchangeHandler<I>> tail : headsAndTails.getTails()) {
                    if (!normalizedPath.startsWith(cleanPath(tail.getKey()))) continue;
                    tail.getValue().handle(exchange);
                }
            } catch (Exception e) {
                try {
                    exceptionHandler.handle(exchange, e);
                } catch (IOException i) {
                    i.addSuppressed(e);
                    throw new RuntimeException(i);
                }
            }
        }
    }

    private static String cleanPath(String path) {
        path = path.trim();
        path = path.startsWith("/") ? path.substring(1) : path;
        path = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        return path;
    }

    private record Path(String url, String method) {}
    private record DynamicPath(TemplateMatcher.Template template, String original, String method) {}
    private record DynamicPathDefault(TemplateMatcher.Template template, String original) {}
}