package com.youfuns.webserver.servers;

import com.youfuns.logger.SimpleLogger;
import com.youfuns.webserver.interfaces.Exchange;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

public interface ExchangeHandlerInterface<InternalExchange> {
    void setLogger(SimpleLogger logger); // Marker: should have a logger

    Exchange<InternalExchange> createExchange(InternalExchange internalExchange);

    void serveFile(InternalExchange internalExchange, int statusCode, Map<String, String> headers, Path file) throws IOException;

    void serveFile(InternalExchange internalExchange, int statusCode, Map<String, String> headers, byte[] fileBytes) throws IOException;

    void sendResponse(InternalExchange internalExchange, int statusCode, Map<String, String> headers, String body) throws IOException;

    org.apache.commons.fileupload.RequestContext createFileUploadRequestContext(Exchange<InternalExchange> internalExchange);

    void closeExchange(InternalExchange internalExchange);
}
