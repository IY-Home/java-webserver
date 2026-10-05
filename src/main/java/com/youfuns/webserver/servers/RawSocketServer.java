package com.youfuns.webserver.servers;

import com.youfuns.logger.OutputLogger;
import com.youfuns.logger.SimpleLogger;
import com.youfuns.webserver.interfaces.Exchange;
import org.apache.commons.fileupload.RequestContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.apache.commons.io.IOUtils.closeQuietly;

public class RawSocketServer implements WebServerInterface<RawSocketServer.ServerAdapter, Socket, Consumer<Socket>> {
    private SimpleLogger logger = new OutputLogger();

    @Override
    public RawSocketServer.ServerAdapter createServer(InetSocketAddress address, int backlog) {
        return null;
    }

    @Override
    public RawSocketServer.ServerAdapter createServer(InetSocketAddress address, int backlog, Consumer<Socket> handler) {
        try {
            return new RawSocketServer.ServerAdapter(new ServerSocket(address.getPort(), backlog), handler, new AtomicBoolean(false));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void createContext(RawSocketServer.ServerAdapter serverSocket, String endpoint, Consumer<Socket> handler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeContext(RawSocketServer.ServerAdapter serverSocket, String endpoint) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean supportsMultipleContexts() {
        return false;
    }

    @Override
    public boolean supportsContextMutationAfterStart() {
        return false;
    }

    @Override
    public Consumer<Socket> createInternalHandler(Consumer<Socket> handler) {
        return handler;
    }

    @Override
    public void start(RawSocketServer.ServerAdapter serverSocket) {
        serverSocket.started().set(true);
        new Thread(() -> {
            while (serverSocket.started().get()) {
                try {
                    Socket client = serverSocket.serverSocket().accept();

                    // Spawn new thread immediately
                    new Thread(() -> {
                        serverSocket.handler().accept(client);
                    }).start();
                } catch (Exception e) {
                    serverSocket.started().set(false);
                    throw new RuntimeException(e);
                }
                // Loop continues, accepting next client
            }
        }).start();
    }

    @Override
    public void stop(RawSocketServer.ServerAdapter serverSocket, int delay) {
        serverSocket.started().set(false);
        try {
            serverSocket.serverSocket.close();
        } catch (IOException ignored) {
        }
    }

    @Override
    public ExchangeHandlerInterface<Socket> getExchangeHandlerAdapters() {
        SocketAdapter adapter = new SocketAdapter();
        adapter.setLogger(logger);
        return adapter;
    }

    @Override
    public void setLogger(SimpleLogger logger) {
        this.logger = logger;
    }

    public static final class SocketAdapter implements ExchangeHandlerInterface<Socket> {
        private SimpleLogger logger;

        public SocketAdapter() {
            this.logger = new OutputLogger();
        }

        @Override
        public void setLogger(SimpleLogger logger) {
            this.logger = logger;
        }

        @Override
        public Exchange<Socket> createExchange(Socket socket) {
            try {
                // Set timeout to prevent hanging
                socket.setSoTimeout(30000);

                InputStream is = socket.getInputStream();
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(is, StandardCharsets.UTF_8)
                );

                String requestLine;
                try {
                    requestLine = reader.readLine();
                } catch (java.net.SocketTimeoutException e) {
                    logger.log(this.getClass(), "Connection timed out (idle), closing",
                            SimpleLogger.Level.DEBUG);
                    closeQuietly(socket);
                    return null;
                }
                if (requestLine == null || requestLine.isEmpty()) {
                    throw new IOException("Empty request");
                }


                String[] parts = requestLine.split(" ");
                if (parts.length != 3) {
                    throw new IOException("Malformed request line: " + requestLine);
                }

                String method = parts[0];
                String pathWithQuery = parts[1];
                String protocol = parts[2];

                // ===== 2. Parse Headers =====
                Map<String, List<String>> headers = new HashMap<>();
                int contentLength = 0;
                String transferEncoding = null;
                String contentType = null;

                String line;
                while (!(line = reader.readLine()).isEmpty()) {
                    int colonIdx = line.indexOf(':');
                    if (colonIdx > 0) {
                        String key = line.substring(0, colonIdx).trim();
                        String value = line.substring(colonIdx + 1).trim();

                        headers.computeIfAbsent(key, k -> new ArrayList<>()).add(value);

                        // Track important headers
                        switch (key.toLowerCase()) {
                            case "content-length":
                                contentLength = Integer.parseInt(value);
                                break;
                            case "transfer-encoding":
                                transferEncoding = value;
                                break;
                            case "content-type":
                                contentType = value;
                                break;
                        }
                    }
                }

                // ===== 3. Parse URI =====
                URI uri = parseURI(pathWithQuery, socket);

                // ===== 4. Parse Body =====
                String body = extractBody(socket, reader, contentLength, transferEncoding, contentType);

                // ===== 5. Remote Address =====
                InetSocketAddress remoteAddress = (InetSocketAddress) socket.getRemoteSocketAddress();

                return new Exchange<>(
                        method,
                        uri,
                        protocol,
                        remoteAddress,
                        headers,
                        logger,
                        body,
                        this,
                        socket
                );

            } catch (IOException | URISyntaxException e) {
                logger.log(this.getClass(), "Failed to create exchange: " + e.getMessage(),
                        SimpleLogger.Level.ERROR, e);
                throw new RuntimeException("Failed to create exchange", e);
            }
        }

        @Override
        public void serveFile(Socket socket, int statusCode, Map<String, String> headers, Path file)
                throws IOException {
            if (!Files.exists(file) || Files.isDirectory(file)) {
                throw new IllegalArgumentException("File does not exist or is a directory: " + file);
            }

            byte[] fileBytes = Files.readAllBytes(file);
            serveFile(socket, statusCode, headers, fileBytes);
        }

        @Override
        public void serveFile(Socket socket, int statusCode, Map<String, String> headers, byte[] fileBytes)
                throws IOException {
            try (java.io.OutputStream os = socket.getOutputStream()) {
                // Build response
                StringBuilder response = new StringBuilder();
                response.append("HTTP/1.1 ").append(statusCode).append(" ").append(getStatusText(statusCode)).append("\r\n");

                // Add headers
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    response.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
                }

                // Add content length if not present
                if (!headers.containsKey("Content-Length")) {
                    response.append("Content-Length: ").append(fileBytes.length).append("\r\n");
                }

                response.append("\r\n"); // Blank line

                // Send headers
                os.write(response.toString().getBytes(StandardCharsets.UTF_8));

                // Send file content
                os.write(fileBytes);
                os.flush();
            }
        }

        @Override
        public void sendResponse(Socket socket, int statusCode, Map<String, String> headers, String body)
                throws IOException {
            try (java.io.OutputStream os = socket.getOutputStream()) {
                // Build response
                StringBuilder response = new StringBuilder();
                response.append("HTTP/1.1 ").append(statusCode).append(" ")
                        .append(getStatusText(statusCode)).append("\r\n");

                // Add headers
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    response.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
                }

                // Add content length if not present and body is not null
                if (body != null && !headers.containsKey("Content-Length")) {
                    response.append("Content-Length: ").append(body.getBytes(StandardCharsets.UTF_8).length).append("\r\n");
                }

                response.append("\r\n"); // Blank line

                // Send headers
                os.write(response.toString().getBytes(StandardCharsets.UTF_8));

                // Send body
                if (body != null && !body.isEmpty()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }

                os.flush();

                logger.log(this.getClass(), "Response sent: " + statusCode, SimpleLogger.Level.DEBUG);
            }
        }

        @Override
        public RequestContext createFileUploadRequestContext(Exchange<Socket> socket) {
            return new RawSocketRequestContext(socket.getUnderlyingExchange(), socket.getRequestHeaderMap());
        }

        @Override
        public void closeExchange(Socket socket) {
            try {
                if (socket != null && !socket.isClosed()) {
                    socket.close();
                    logger.log(this.getClass(), "Socket closed", SimpleLogger.Level.DEBUG);
                }
            } catch (IOException e) {
                logger.log(this.getClass(), "Error closing socket: " + e.getMessage(),
                        SimpleLogger.Level.WARN);
            }
        }

        // ============================================================
        // Helper Methods
        // ============================================================

        private URI parseURI(String pathWithQuery, Socket socket) throws URISyntaxException {
            // Handle absolute URLs
            if (pathWithQuery.startsWith("http://") || pathWithQuery.startsWith("https://")) {
                return new URI(pathWithQuery);
            }

            // Parse relative URL
            String path = pathWithQuery;
            String query = null;

            int questionMark = pathWithQuery.indexOf('?');
            if (questionMark > 0) {
                path = pathWithQuery.substring(0, questionMark);
                query = pathWithQuery.substring(questionMark + 1);
            }

            String host = socket.getLocalAddress().getHostName();
            int port = socket.getLocalPort();

            return new URI("http", null, host, port, path, query, null);
        }

        private String extractBody(Socket socket, BufferedReader reader, int contentLength,
                                   String transferEncoding, String contentType) throws IOException {
            // Check if multipart - don't read body for multipart requests
            boolean isMultipart = contentType != null &&
                    contentType.toLowerCase().startsWith("multipart/form-data");

            if (isMultipart) {
                return "[multipart/form-data - stream preserved for parser]";
            }

            // Handle chunked encoding
            if ("chunked".equalsIgnoreCase(transferEncoding)) {
                return readChunkedBody(reader);
            }

            // Handle normal body
            if (contentLength > 0) {
                char[] bodyChars = new char[contentLength];
                int totalRead = 0;
                while (totalRead < contentLength) {
                    int read = reader.read(bodyChars, totalRead, contentLength - totalRead);
                    if (read == -1) break;
                    totalRead += read;
                }
                return new String(bodyChars, 0, totalRead);
            }

            return "";
        }

        private String readChunkedBody(BufferedReader reader) throws IOException {
            StringBuilder body = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                // Parse chunk size (hexadecimal)
                String chunkSizeStr = line.trim();
                if (chunkSizeStr.isEmpty()) continue;

                int chunkSize;
                try {
                    chunkSize = Integer.parseInt(chunkSizeStr, 16);
                } catch (NumberFormatException e) {
                    // Not a valid chunk size, might be end of chunks
                    break;
                }

                if (chunkSize == 0) {
                    // Final chunk
                    reader.readLine(); // Read trailing CRLF
                    break;
                }

                // Read chunk data
                char[] chunkData = new char[chunkSize];
                int totalRead = 0;
                while (totalRead < chunkSize) {
                    int read = reader.read(chunkData, totalRead, chunkSize - totalRead);
                    if (read == -1) break;
                    totalRead += read;
                }
                body.append(chunkData, 0, totalRead);

                // Read CRLF after chunk
                reader.readLine();
            }

            return body.toString();
        }

        private String getStatusText(int statusCode) {
            switch (statusCode) {
                case 200: return "OK";
                case 201: return "Created";
                case 204: return "No Content";
                case 301: return "Moved Permanently";
                case 302: return "Found";
                case 304: return "Not Modified";
                case 400: return "Bad Request";
                case 401: return "Unauthorized";
                case 403: return "Forbidden";
                case 404: return "Not Found";
                case 405: return "Method Not Allowed";
                case 500: return "Internal Server Error";
                case 502: return "Bad Gateway";
                case 503: return "Service Unavailable";
                default: return "Unknown";
            }
        }

        // ============================================================
        // RequestContext Implementation for File Uploads
        // ============================================================
        private record RawSocketRequestContext(Socket socket,
                                               Map<String, List<String>> headers) implements RequestContext {

            @Override
                    public String getCharacterEncoding() {
                        String contentType = getContentType();
                        if (contentType == null) return StandardCharsets.UTF_8.name();
                        for (String part : contentType.split(";")) {
                            String trimmed = part.trim();
                            if (trimmed.startsWith("charset=")) {
                                return trimmed.substring("charset=".length()).replace("\"", "");
                            }
                        }
                        return StandardCharsets.UTF_8.name();
                    }

                    @Override
                    public String getContentType() {
                        return getFirstHeader("Content-Type");
                    }

                    @Override
                    public int getContentLength() {
                        String length = getFirstHeader("Content-Length");
                        if (length != null) {
                            try {
                                return Integer.parseInt(length);
                            } catch (NumberFormatException e) {
                                return -1;
                            }
                        }
                        return -1;
                    }

                    @Override
                    public InputStream getInputStream() throws IOException {
                        return socket.getInputStream();
                    }

                    private String getFirstHeader(String name) {
                        if (headers == null) return null;
                        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                            if (entry.getKey().equalsIgnoreCase(name)) {
                                List<String> values = entry.getValue();
                                return values != null && !values.isEmpty() ? values.get(0) : null;
                            }
                        }
                        return null;
                    }
                }
    }


    public record ServerAdapter(ServerSocket serverSocket, Consumer<Socket> handler, AtomicBoolean started) {}
    public record Pair<A, B>(A first, B second) {}
}