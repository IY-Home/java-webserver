package com.youfuns.webserver.servers;

import com.youfuns.logger.SimpleLogger;
import com.youfuns.webserver.HeadsAndTails;
import com.youfuns.webserver.interfaces.ExceptionHandler;
import com.youfuns.webserver.interfaces.Exchange;
import com.youfuns.webserver.interfaces.ExchangeHandler;
import com.youfuns.webserver.interfaces.HeadHandler;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public interface ExchangeHandlerInterface<InternalExchange> {
    void setLogger(SimpleLogger logger); // Marker: should have a logger

    Exchange<InternalExchange> createExchange(InternalExchange internalExchange);

    void serveFile(InternalExchange internalExchange, int statusCode, Map<String, String> headers, Path file) throws IOException;

    void serveFile(InternalExchange internalExchange, int statusCode, Map<String, String> headers, byte[] fileBytes) throws IOException;

    String extractBody(InternalExchange internalExchange);

    void sendResponse(InternalExchange internalExchange, int statusCode, Map<String, String> headers, String body) throws IOException;

    boolean isMultipart(InternalExchange internalExchange);

    org.apache.commons.fileupload.RequestContext createFileUploadRequestContext(InternalExchange internalExchange);

    void closeExchange(InternalExchange internalExchange);

    default void handleExchange(Exchange<InternalExchange> exchange, HeadsAndTails<InternalExchange> headsAndTails, ExchangeHandler<InternalExchange> handler, ExceptionHandler<InternalExchange> exceptionHandler) {
        try {
            boolean headsPassed = true;

            // Process heads
            for (HeadHandler<InternalExchange> head : headsAndTails.getHeads()) {
                if (!head.handle(exchange)) {
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
                for (ExchangeHandler<InternalExchange> tail : headsAndTails.getTails()) {
                    tail.handle(exchange);
                }
            } catch (Exception e) {
                try {
                    exceptionHandler.handle(exchange, e);
                } catch (IOException ignored) {

                }
            }
        }
    }
}
