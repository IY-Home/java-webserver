package com.youfuns.webserver;

import com.youfuns.logger.SimpleLogger;
import com.youfuns.webserver.interfaces.Exchange;
import com.youfuns.webserver.interfaces.ExchangeHandler;
import com.youfuns.webserver.servers.ExchangeHandlerInterface;

import java.io.IOException;
import java.net.URLConnection;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StaticFileHandler<I> implements ExchangeHandler<I> {
    private final Path baseDirectory;
    private final String basePath;
    private final Map<String, String> mimeTypes = new ConcurrentHashMap<>();
    private final SimpleLogger logger;
    private final boolean directoryListing;
    private final String indexPath;
    private final ExchangeHandlerInterface<I> adapter;

    public StaticFileHandler(ExchangeHandlerInterface<I> adapter, String directory, String basePath, SimpleLogger logger) {
        this(adapter, directory, basePath, logger, false, null);
    }

    public StaticFileHandler(ExchangeHandlerInterface<I> adapter, String directory, String basePath, SimpleLogger logger, boolean directoryListing, String indexPath) {
        this.logger = logger;
        this.baseDirectory = Paths.get(directory).toAbsolutePath().normalize();
        this.basePath = basePath;
        this.directoryListing = directoryListing;
        this.indexPath = indexPath;
        this.adapter = adapter;

        // Verify directory exists
        if (!Files.exists(baseDirectory) || !Files.isDirectory(baseDirectory)) {
            logger.log(StaticFileHandler.class, "Static directory does not exist: " + directory, SimpleLogger.Level.ERROR);
            throw new IllegalArgumentException("Static directory does not exist: " + directory);
        }

        // Initialize common MIME types
        initMimeTypes();

        logger.log(StaticFileHandler.class, "Serving static files from: " + baseDirectory, SimpleLogger.Level.INFO);
    }

    @Override
    public void handle(Exchange<I> req) throws IOException {
        String path = req.getRequestPath();

        // Remove base path prefix if present
        String relativePath = path;
        if (basePath != null && !basePath.isEmpty() && path.startsWith(basePath)) {
            relativePath = path.substring(basePath.length());
        }

        // Security: Prevent directory traversal
        if (relativePath.contains("..")) {
            req.sendForbidden();
            return;
        }

        // If path ends with /, serve index.html if configured
        if (relativePath.endsWith("/") && indexPath != null) {
            relativePath += indexPath;
        }

        // If path is root, serve index.html if configured
        if ((relativePath.isEmpty() || relativePath.equals("/")) && indexPath != null) {
            relativePath = "/" + indexPath;
        }

        Path filePath = baseDirectory.resolve(relativePath.startsWith("/") ? relativePath.substring(1) : relativePath).normalize();

        // Security: Ensure the resolved path is still inside base directory
        if (!filePath.startsWith(baseDirectory)) {
            req.sendForbidden();
            return;
        }

        // Check if file exists and is readable
        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            if (Files.isDirectory(filePath) && directoryListing) {
                sendDirectoryListing(req, filePath);
                return;
            }
            req.sendNotFound();
            return;
        }

        // Serve the file
        req.serveFile(filePath.toString());
    }

    private void sendDirectoryListing(Exchange<I> req, Path dirPath) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dirPath)) {
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html>");
            html.append("<html><head><title>Index of ").append(req.getRequestPath()).append("</title>");
            html.append("<style>");
            html.append("body { font-family: monospace; max-width: 800px; margin: 40px auto; padding: 20px; }");
            html.append("h1 { color: #333; border-bottom: 2px solid #eee; padding-bottom: 10px; }");
            html.append("ul { list-style: none; padding: 0; }");
            html.append("li { padding: 5px 0; border-bottom: 1px solid #f5f5f5; }");
            html.append("a { text-decoration: none; color: #0066cc; }");
            html.append("a:hover { text-decoration: underline; }");
            html.append(".folder { color: #e67e22; }");
            html.append(".file { color: #2c3e50; }");
            html.append("</style>");
            html.append("</head><body>");
            html.append("<h1>📂 Index of ").append(req.getRequestPath()).append("</h1>");
            html.append("<ul>");

            // Add parent directory link
            if (!req.getRequestPath().equals("/")) {
                html.append("<li><a href=\"../\">📁 ../</a></li>");
            }

            for (Path entry : stream) {
                String fileName = entry.getFileName().toString();
                String filePath = req.getRequestPath() + (req.getRequestPath().endsWith("/") ? "" : "/") + fileName;

                if (Files.isDirectory(entry)) {
                    html.append("<li><a href=\"").append(filePath).append("/\">📁 ").append(fileName).append("/</a></li>");
                } else {
                    long size = Files.size(entry);
                    String sizeStr = formatFileSize(size);
                    html.append("<li><a href=\"").append(filePath).append("\">📄 ").append(fileName).append("</a> <span style=\"color: #999; float: right;\">")
                            .append(sizeStr).append("</span></li>");
                }
            }

            html.append("</ul>");
            html.append("</body></html>");

            req.addResponseHeader("Content-Type", "text/html; charset=UTF-8");
            req.send(html.toString());
        } catch (IOException e) {
            req.sendError("Failed to list directory");
        }
    }

    private String getMimeType(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            String ext = fileName.substring(dotIndex + 1);
            String mime = mimeTypes.get(ext);
            if (mime != null) return mime;
        }
        // Fallback to Java's built-in detection
        String detected = URLConnection.getFileNameMap().getContentTypeFor(path.toString());
        return detected != null ? detected : "application/octet-stream";
    }

    private void initMimeTypes() {
        mimeTypes.put("html", "text/html; charset=UTF-8");
        mimeTypes.put("htm", "text/html; charset=UTF-8");
        mimeTypes.put("css", "text/css; charset=UTF-8");
        mimeTypes.put("js", "application/javascript; charset=UTF-8");
        mimeTypes.put("json", "application/json; charset=UTF-8");
        mimeTypes.put("xml", "application/xml; charset=UTF-8");
        mimeTypes.put("txt", "text/plain; charset=UTF-8");
        mimeTypes.put("csv", "text/csv; charset=UTF-8");

        mimeTypes.put("png", "image/png");
        mimeTypes.put("jpg", "image/jpeg");
        mimeTypes.put("jpeg", "image/jpeg");
        mimeTypes.put("gif", "image/gif");
        mimeTypes.put("svg", "image/svg+xml");
        mimeTypes.put("ico", "image/x-icon");
        mimeTypes.put("webp", "image/webp");

        mimeTypes.put("pdf", "application/pdf");
        mimeTypes.put("zip", "application/zip");
        mimeTypes.put("gz", "application/gzip");
        mimeTypes.put("tar", "application/x-tar");

        mimeTypes.put("mp3", "audio/mpeg");
        mimeTypes.put("mp4", "video/mp4");
        mimeTypes.put("webm", "video/webm");
        mimeTypes.put("ogg", "audio/ogg");

        mimeTypes.put("woff", "font/woff");
        mimeTypes.put("woff2", "font/woff2");
        mimeTypes.put("ttf", "font/ttf");
        mimeTypes.put("eot", "application/vnd.ms-fontobject");
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}