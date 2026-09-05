package com.youfuns.webserver;

import com.youfuns.webserver.interfaces.ExchangeHandler;
import com.youfuns.webserver.interfaces.HeadHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class HeadsAndTails<InternalExchange> {
    private final List<Map.Entry<String, HeadHandler<InternalExchange>>> heads = new ArrayList<>();
    private final List<Map.Entry<String, ExchangeHandler<InternalExchange>>> tails = new ArrayList<>();

    public List<Map.Entry<String, HeadHandler<InternalExchange>>> getHeads() {
        return heads;
    }

    public List<Map.Entry<String, ExchangeHandler<InternalExchange>>> getTails() {
        return tails;
    }

    public void addHead(String template, HeadHandler<InternalExchange> head) {
        heads.add(Map.entry(template, head));
    }

    public void removeHead(HeadHandler<InternalExchange> head) {
        heads.removeIf(entry -> entry.getValue().equals(head));
    }

    public void clearHeads() {
        heads.clear();
    }

    public void addTail(String template, ExchangeHandler<InternalExchange> tail) {
        tails.add(Map.entry(template, tail));
    }

    public void removeTail(ExchangeHandler<InternalExchange> tail) {
        tails.removeIf(entry -> entry.getValue().equals(tail));
    }

    public void clearTails() {
        tails.clear();
    }
}
