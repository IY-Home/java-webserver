package com.youfuns.webserver;

import com.youfuns.webserver.interfaces.ExchangeHandler;
import com.youfuns.webserver.interfaces.HeadHandler;

import java.util.ArrayList;
import java.util.List;

public class HeadsAndTails<InternalExchange> {
    private final List<HeadHandler<InternalExchange>> heads = new ArrayList<>();
    private final List<ExchangeHandler<InternalExchange>> tails = new ArrayList<>();

    public List<HeadHandler<InternalExchange>> getHeads() {
        return heads;
    }

    public List<ExchangeHandler<InternalExchange>> getTails() {
        return tails;
    }

    public void addHead(HeadHandler<InternalExchange> head) {
        heads.add(head);
    }

    public void removeHead(HeadHandler<InternalExchange> head) {
        heads.remove(head);
    }

    public void clearHeads() {
        heads.clear();
    }

    public void addTail(ExchangeHandler<InternalExchange> tail) {
        tails.add(tail);
    }

    public void removeTail(ExchangeHandler<InternalExchange> tail) {
        tails.remove(tail);
    }

    public void clearTails() {
        tails.clear();
    }
}
