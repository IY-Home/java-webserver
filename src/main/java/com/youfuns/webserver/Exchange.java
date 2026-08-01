package com.youfuns.webserver;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.youfuns.logger.SimpleLogger;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class Exchange implements AutoCloseable {
    private final HttpExchange httpExchange;
    private final String method;
    private final URI requestUri;
    private final String path;
    private final Headers headers;
    private final String query;
    private final Map<String, String> queryParams;
    private final String protocol;
    private final InetSocketAddress remoteAddress;
    private final Map<String, List<String>> requestHeaderMap;
    private final String body;
    private final Map<String, Object> attributes = new HashMap<>();
    private int responseStatusCode = 200;
    private String responseBodyContent = "";
    private final Map<String, String> responseHeadersMap = new HashMap<>();
    private boolean responseAlreadySent = false;

    // File upload fields
    private Map<String, UploadedFile> uploadedFiles = new HashMap<>();
    private Map<String, String> formFields = new HashMap<>();
    private boolean multipartParsed = false;

    // File upload configuration
    private static int MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
    private static final int MEMORY_THRESHOLD = 1024 * 1024; // 1MB - files larger go to disk

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final SimpleLogger logger;

    public Exchange(HttpExchange exchange, SimpleLogger logger) {
        this.logger = logger;
        this.httpExchange = exchange;

        method = exchange.getRequestMethod();
        requestUri = exchange.getRequestURI();
        path = requestUri.getPath();
        query = requestUri.getQuery();
        queryParams = parseQueryString(query);
        protocol = exchange.getProtocol();
        remoteAddress = exchange.getRemoteAddress();

        headers = exchange.getRequestHeaders();
        requestHeaderMap = Map.copyOf(headers);

        // IMPORTANT: Don't read body for multipart requests
        String contentType = getRequestHeaderCaseInsensitive("Content-Type");
        boolean isMultipart = contentType != null && contentType.startsWith("multipart/form-data");

        String requestBody = "";
        if (!isMultipart) {
            try (InputStream is = exchange.getRequestBody()) {
                byte[] bodyBytes = is.readAllBytes();
                requestBody = new String(bodyBytes, StandardCharsets.UTF_8);
            } catch (IOException e) {
                logger.log(Exchange.class, "Failed to read request body: " + e.getMessage(), SimpleLogger.Level.WARN);
            }
        } else {
            requestBody = "[multipart/form-data - stream preserved for parser]";
        }
        body = requestBody;

        // ===== LOGGING =====
        String fullAddress = requestUri.toString();
        logger.log(Exchange.class, "Received " + method + " request to " + fullAddress, SimpleLogger.Level.INFO);
        logger.log(Exchange.class, "Request protocol: " + protocol, SimpleLogger.Level.DEBUG);
        logger.log(Exchange.class, "Remote address: " + remoteAddress, SimpleLogger.Level.DEBUG);
        logger.log(Exchange.class, "Request headers: " + requestHeaderMap, SimpleLogger.Level.DEBUG);
        logger.log(Exchange.class, "Query parameters: " + queryParams, SimpleLogger.Level.DEBUG);
        logger.log(Exchange.class, "Request body: " + (isMultipart ? "[multipart/form-data]" : (!body.isEmpty() ? body : "(empty)")), SimpleLogger.Level.DEBUG);
    }

    // ===== REQUEST GETTERS =====

    public String getHttpMethod() { return method; }
    public URI getRequestUri() { return requestUri; }
    public String getRequestPath() { return path; }
    public String getQueryString() { return query; }
    public Map<String, String> getQueryParameters() { return queryParams; }
    public String getProtocol() { return protocol; }
    public InetSocketAddress getRemoteSocketAddress() { return remoteAddress; }
    public Map<String, List<String>> getAllRequestHeaders() { return requestHeaderMap; }
    public String getRequestBody() { return body; }
    public HttpExchange getUnderlyingHttpExchange() { return httpExchange; }

    // ===== REQUEST HEADER HELPERS =====

    public String getRequestHeader(String name) {
        List<String> values = headers.get(name);
        return values != null && !values.isEmpty() ? values.get(0) : null;
    }

    public String getRequestHeaderCaseInsensitive(String name) {
        for (String headerName : headers.keySet()) {
            if (headerName.equalsIgnoreCase(name)) {
                return getRequestHeader(headerName);
            }
        }
        return null;
    }

    public List<String> getRequestHeaders(String name) {
        return headers.get(name);
    }

    public boolean hasRequestHeader(String name) {
        return headers.containsKey(name);
    }

    public boolean hasRequestHeaderCaseInsensitive(String name) {
        for (String headerName : headers.keySet()) {
            if (headerName.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    // ===== AUTHORIZATION / JWT HELPERS =====

    public String getBearerToken() {
        String auth = getRequestHeaderCaseInsensitive("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        return null;
    }

    public Map<String, Object> decodeJwtClaims() {
        String token = getBearerToken();
        if (token == null) return null;

        try {
            String[] parts = token.split("\\.");
            if (parts.length == 3) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                return objectMapper.readValue(payload, Map.class);
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    public String getJwtClaim(String claimName) {
        Map<String, Object> claims = decodeJwtClaims();
        return claims != null ? String.valueOf(claims.get(claimName)) : null;
    }

    // ===== QUERY PARAMETER HELPERS =====

    public String getQueryParameter(String name) {
        return queryParams.get(name);
    }

    public String getQueryParameter(String name, String defaultValue) {
        return queryParams.getOrDefault(name, defaultValue);
    }

    public int getQueryParameterAsInt(String name) {
        String value = queryParams.get(name);
        try {
            return value != null ? Integer.parseInt(value) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public int getQueryParameterAsInt(String name, int defaultValue) {
        String value = queryParams.get(name);
        try {
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public long getQueryParameterAsLong(String name, long defaultValue) {
        String value = queryParams.get(name);
        try {
            return value != null ? Long.parseLong(value) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public double getQueryParameterAsDouble(String name, double defaultValue) {
        String value = queryParams.get(name);
        try {
            return value != null ? Double.parseDouble(value) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public boolean getQueryParameterAsBoolean(String name, boolean defaultValue) {
        String value = queryParams.get(name);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    public List<String> getAllQueryParameterNames() {
        return new ArrayList<>(queryParams.keySet());
    }

    // ===== URL ENCODING HELPERS =====

    /**
     * URL-encodes a string using UTF-8.
     */
    public static String urlEncode(String value) {
        if (value == null) return null;
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            return value;
        }
    }

    /**
     * URL-decodes a string using UTF-8.
     */
    public static String urlDecode(String value) {
        if (value == null) return null;
        try {
            return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            return value;
        }
    }

    /**
     * URL-encodes a map of parameters into a query string.
     */
    public static String urlEncodeParams(Map<String, String> params) {
        if (params == null || params.isEmpty()) return "";

        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (result.length() > 0) {
                result.append("&");
            }
            result.append(urlEncode(entry.getKey()))
                    .append("=")
                    .append(urlEncode(entry.getValue()));
        }
        return result.toString();
    }

    /**
     * URL-encodes a map with object values (converts objects to strings).
     */
    public static String urlEncodeObjectParams(Map<String, Object> params) {
        if (params == null || params.isEmpty()) return "";

        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (result.length() > 0) {
                result.append("&");
            }
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result.append(urlEncode(entry.getKey()))
                    .append("=")
                    .append(urlEncode(value));
        }
        return result.toString();
    }

    // ===== REDIRECT HELPERS =====

    /**
     * Sends a 302 Found redirect to the given URL.
     */
    public void redirect(String url) throws IOException {
        addResponseHeader("Location", url);
        sendResponse(302, "");
    }

    /**
     * Sends a 301 Moved Permanently redirect to the given URL.
     */
    public void redirectPermanent(String url) throws IOException {
        addResponseHeader("Location", url);
        sendResponse(301, "");
    }

    /**
     * Sends a 303 See Other redirect (POST to GET).
     */
    public void redirectSeeOther(String url) throws IOException {
        addResponseHeader("Location", url);
        sendResponse(303, "");
    }

    /**
     * Sends a redirect with a custom status code.
     */
    public void redirect(int statusCode, String url) throws IOException {
        addResponseHeader("Location", url);
        sendResponse(statusCode, "");
    }


    // ===== REQUEST BODY HELPERS (Non-multipart) =====

    public <T> T parseBodyAsJson(Class<T> targetClass) throws IOException {
        try {
            return objectMapper.readValue(body, targetClass);
        } catch (JsonProcessingException e) {
            throw new IOException("Failed to parse JSON request body", e);
        }
    }

    public Map<String, Object> parseBodyAsJsonMap() throws IOException {
        try {
            return objectMapper.readValue(body, Map.class);
        } catch (JsonProcessingException e) {
            throw new IOException("Failed to parse JSON request body as Map", e);
        }
    }

    public Map<String, Object> parseBodyAsJsonMap(Map<String, Object> defaultMap) throws IOException {
        try {
            return objectMapper.readValue(body, Map.class);
        } catch (JsonProcessingException e) {
            return defaultMap;
        }
    }

    public <T> List<T> parseBodyAsJsonList(Class<T> elementClass) throws IOException {
        try {
            return objectMapper.readValue(body,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, elementClass));
        } catch (JsonProcessingException e) {
            throw new IOException("Failed to parse JSON request body as List", e);
        }
    }

    // ===== JSON PARAMETER HELPERS =====

    public Object getJsonParameter(String name) {
        try {
            return parseBodyAsJsonMap().get(name);
        } catch (IOException e) {
            return null;
        }
    }

    public <T> T getJsonParameter(Class<T> clazz, String name, T defaultValue) {
        try {
            Object param = getJsonParameter(name);
            if (param == null) return defaultValue;
            return clazz.cast(param);
        } catch (ClassCastException e) {
            return defaultValue;
        }
    }

    public int getJsonParameterAsInt(String name, int defaultValue) {
        Integer value = getJsonParameter(Integer.class, name, defaultValue);
        return value != null ? value : defaultValue;
    }

    public long getJsonParameterAsLong(String name, long defaultValue) {
        Long value = getJsonParameter(Long.class, name, defaultValue);
        return value != null ? value : defaultValue;
    }

    public double getJsonParameterAsDouble(String name, double defaultValue) {
        Double value = getJsonParameter(Double.class, name, defaultValue);
        return value != null ? value : defaultValue;
    }

    public float getJsonParameterAsFloat(String name, float defaultValue) {
        Float value = getJsonParameter(Float.class, name, defaultValue);
        return value != null ? value : defaultValue;
    }

    public boolean getJsonParameterAsBoolean(String name, boolean defaultValue) {
        Boolean value = getJsonParameter(Boolean.class, name, defaultValue);
        return value != null ? value : defaultValue;
    }

    public short getJsonParameterAsShort(String name, short defaultValue) {
        Short value = getJsonParameter(Short.class, name, defaultValue);
        return value != null ? value : defaultValue;
    }

    public byte getJsonParameterAsByte(String name, byte defaultValue) {
        Byte value = getJsonParameter(Byte.class, name, defaultValue);
        return value != null ? value : defaultValue;
    }

    public char getJsonParameterAsChar(String name, char defaultValue) {
        Character value = getJsonParameter(Character.class, name, defaultValue);
        return value != null ? value : defaultValue;
    }

    // For non-primitive types, you can directly use the generic method or create convenience wrappers:
    public String getJsonParameterAsString(String name, String defaultValue) {
        return getJsonParameter(String.class, name, defaultValue);
    }

    public String getJsonName(String defaultValue) {
        return getJsonParameterAsString("name", defaultValue);
    }

    public String getJsonEmail(String defaultValue) {
        return getJsonParameterAsString("email", defaultValue);
    }

    public String getJsonUsername(String defaultValue) {
        return getJsonParameterAsString("username", defaultValue);
    }

    public String getJsonPassword(String defaultValue) {
        return getJsonParameterAsString("password", defaultValue);
    }

    public boolean isJsonRequestBody() {
        String contentType = getRequestHeaderCaseInsensitive("Content-Type");
        return contentType != null && contentType.contains("application/json");
    }

    public boolean hasRequestBody() {
        return body != null && !body.isEmpty();
    }

    // ===== FILE UPLOAD HELPERS =====

    /**
     * Checks if the request is a multipart/form-data request.
     */
    public boolean isMultipartRequest() {
        String contentType = getRequestHeaderCaseInsensitive("Content-Type");
        return contentType != null && contentType.startsWith("multipart/form-data");
    }

    /**
     * Parses the multipart request and populates uploadedFiles and formFields.
     * This is called automatically when needed.
     */
    private void parseMultipartIfNeeded() {
        if (multipartParsed) return;
        if (!isMultipartRequest()) return;

        try {
            parseMultipart();
            multipartParsed = true;
        } catch (IOException e) {
            logger.log(Exchange.class, "Failed to parse multipart: " + e.getMessage(), SimpleLogger.Level.ERROR);
        }
    }

    /**
     * Parse multipart request using Apache Commons FileUpload.
     */
    private void parseMultipart() throws IOException {
        DiskFileItemFactory factory = new DiskFileItemFactory();
        factory.setSizeThreshold(MEMORY_THRESHOLD);
        factory.setRepository(new File(System.getProperty("java.io.tmpdir")));

        ServletFileUpload upload = new ServletFileUpload(factory);
        upload.setSizeMax(MAX_FILE_SIZE);

        try {
            // Use the custom ApacheHttpExchangeContext to bridge HttpExchange to Commons FileUpload
            List<FileItem> items = upload.parseRequest(new ApacheHttpExchangeContext(httpExchange));

            for (FileItem item : items) {
                if (item.isFormField()) {
                    String fieldName = item.getFieldName();
                    String value = item.getString(StandardCharsets.UTF_8.name());
                    formFields.put(fieldName, value);
                    logger.log(Exchange.class, "Form field: " + fieldName + " = " + value, SimpleLogger.Level.DEBUG);
                } else {
                    String fieldName = item.getFieldName();
                    String filename = item.getName();
                    String contentType = item.getContentType();
                    byte[] data = IOUtils.toByteArray(item.getInputStream());

                    uploadedFiles.put(fieldName, new UploadedFile(fieldName, filename, contentType, data));
                    logger.log(Exchange.class, "File uploaded: " + filename + " (" + data.length + " bytes)", SimpleLogger.Level.DEBUG);
                }
            }
        } catch (FileUploadException e) {
            throw new IOException("Failed to parse multipart request: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if a file with the given field name was uploaded.
     */
    public boolean hasFile(String fieldName) {
        parseMultipartIfNeeded();
        return uploadedFiles.containsKey(fieldName);
    }

    /**
     * Gets an uploaded file by its form field name.
     */
    public UploadedFile getFile(String fieldName) {
        parseMultipartIfNeeded();
        return uploadedFiles.get(fieldName);
    }

    /**
     * Gets all uploaded files.
     */
    public Map<String, UploadedFile> getAllFiles() {
        parseMultipartIfNeeded();
        return Collections.unmodifiableMap(uploadedFiles);
    }

    /**
     * Gets a form field value (non-file field).
     */
    public String getFormField(String fieldName) {
        parseMultipartIfNeeded();
        return formFields.get(fieldName);
    }

    /**
     * Gets all form fields (non-file fields).
     */
    public Map<String, String> getAllFormFields() {
        parseMultipartIfNeeded();
        return Collections.unmodifiableMap(formFields);
    }

    /**
     * Checks if an uploaded file has a specific extension.
     */
    public boolean isExtension(UploadedFile file, String extension) {
        if (file == null || file.filename == null) return false;
        String ext = extension.startsWith(".") ? extension : "." + extension;
        return file.filename.toLowerCase().endsWith(ext.toLowerCase());
    }

    /**
     * Checks if a file's content matches a specific byte pattern (magic bytes).
     */
    public boolean isTypeByBytes(UploadedFile file, byte[] startBytes, int offset) {
        if (file == null || file.data == null) return false;
        if (offset + startBytes.length > file.data.length) return false;

        for (int i = 0; i < startBytes.length; i++) {
            if (file.data[offset + i] != startBytes[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Convenience: checks if a file is a PNG by its magic bytes.
     */
    public boolean isPNG(UploadedFile file) {
        byte[] pngSignature = {(byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47};
        return isTypeByBytes(file, pngSignature, 0);
    }

    /**
     * Convenience: checks if a file is a JPEG by its magic bytes.
     */
    public boolean isJPEG(UploadedFile file) {
        byte[] jpegSignature = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        return isTypeByBytes(file, jpegSignature, 0);
    }

    /**
     * Convenience: checks if a file is a PDF by its magic bytes.
     */
    public boolean isPDF(UploadedFile file) {
        byte[] pdfSignature = {(byte) 0x25, (byte) 0x50, (byte) 0x44, (byte) 0x46};
        return isTypeByBytes(file, pdfSignature, 0);
    }

    /**
     * Convenience: checks if a file is JSON by its magic bytes.
     * JSON files typically start with '{' or '['.
     */
    public boolean isJSON(UploadedFile file) {
        byte[] jsonSignature1 = {(byte) 0x7B}; // '{'
        byte[] jsonSignature2 = {(byte) 0x5B}; // '['
        return isTypeByBytes(file, jsonSignature1, 0) ||
                isTypeByBytes(file, jsonSignature2, 0);
    }

    /**
     * Saves an uploaded file to a directory.
     */
    public Exchange saveFileIn(UploadedFile file, String saveDir) throws IOException {
        if (file == null) return this;
        Path dir = Paths.get(saveDir);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        Path savePath = dir.resolve(file.filename);
        return saveFileAt(file, savePath.toString());
    }

    /**
     * Saves an uploaded file to a specific location.
     */
    public Exchange saveFileAt(UploadedFile file, String saveLocation) throws IOException {
        if (file == null) return this;
        Path savePath = Paths.get(saveLocation);

        Path parent = savePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        Files.write(savePath, file.data);
        return this;
    }

    /**
     * Saves an uploaded file with a generated unique name.
     * @param file The uploaded file
     * @param saveDir The directory to save to
     * @param preserveOriginalName If true, keep the original filename; if false, generate a UUID name
     * @return The path where the file was saved
     */
    public String saveFileSafe(UploadedFile file, String saveDir, boolean preserveOriginalName) throws IOException {
        if (file == null) return null;

        Path dir = Paths.get(saveDir);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }

        String filename;
        if (preserveOriginalName) {
            filename = file.filename;
        } else {
            String ext = "";
            int dotIndex = file.filename.lastIndexOf('.');
            if (dotIndex > 0) {
                ext = file.filename.substring(dotIndex);
            }
            filename = UUID.randomUUID().toString() + ext;
        }

        Path savePath = dir.resolve(filename);

        if (preserveOriginalName && Files.exists(savePath)) {
            String baseName = file.filename.substring(0, file.filename.lastIndexOf('.'));
            String ext = file.filename.substring(file.filename.lastIndexOf('.'));
            int counter = 1;
            while (Files.exists(savePath)) {
                filename = baseName + "_" + counter + ext;
                savePath = dir.resolve(filename);
                counter++;
            }
        }

        Files.write(savePath, file.data);
        return savePath.toString();
    }

    public void serveFile(String filePath) {
        String expandedPath = filePath.replace("~", System.getProperty("user.home"));
        Path file = Paths.get(expandedPath);
        if (!Files.exists(file) || Files.isDirectory(file)) {
            logger.log(WebServer.class, "File does not exist: " + filePath, SimpleLogger.Level.ERROR);
            throw new IllegalArgumentException("File does not exist: " + filePath);
        }
        try {
            String mimeType = URLConnection.getFileNameMap().getContentTypeFor(file.toString());
            if (mimeType == null) mimeType = "application/octet-stream";

            this.addResponseHeader("Content-Type", mimeType);
            this.addResponseHeader("Content-Length", String.valueOf(Files.size(file)));
            this.addResponseHeader("Cache-Control", "max-age=3600");

            this.getUnderlyingHttpExchange().sendResponseHeaders(200, Files.size(file));
            try (OutputStream os = this.getUnderlyingHttpExchange().getResponseBody();
                 InputStream is = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
        } catch (IOException e) {
            logger.log(WebServer.class, "Failed to serve file: " + e.getMessage(),  SimpleLogger.Level.ERROR);
        };
    }

    // ===== RESPONSE BUILDERS =====

    public Exchange setResponseStatusCode(int statusCode) {
        this.responseStatusCode = statusCode;
        return this;
    }

    public Exchange setResponseBody(String body) {
        this.responseBodyContent = body;
        return this;
    }

    public Exchange setResponseBodyAsJson(Object object) {
        try {
            this.responseBodyContent = objectMapper.writeValueAsString(object);
            addResponseHeader("Content-Type", "application/json");
        } catch (JsonProcessingException e) {
            this.responseBodyContent = "{\"error\": \"Failed to serialize JSON response\"}";
            addResponseHeader("Content-Type", "application/json");
            this.responseStatusCode = 500;
        }
        return this;
    }

    public Exchange addResponseHeader(String name, String value) {
        responseHeadersMap.put(name, value);
        return this;
    }

    public Exchange allowMethods(String... methods) {
        StringBuilder allowed = new StringBuilder();
        for (String m : methods) {
            allowed.append(m).append(", ");
        }
        if (allowed.length() > 0) {
            allowed.setLength(allowed.length() - 2);
        }
        addResponseHeader("Allow", allowed.toString());
        return this;
    }

    public Exchange enableCors() {
        return enableCors("*");
    }

    public Exchange enableCors(String allowedOrigin) {
        addResponseHeader("Access-Control-Allow-Origin", allowedOrigin);
        addResponseHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH");
        addResponseHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
        addResponseHeader("Access-Control-Allow-Credentials", "true");
        return this;
    }

    public Exchange setJwtResponseToken(String token) {
        addResponseHeader("Authorization", "Bearer " + token);
        return this;
    }
    // ===== COOKIE HELPERS =====

    /**
     * Gets all cookies from the request as a map.
     */
    public Map<String, String> getCookies() {
        Map<String, String> cookies = new HashMap<>();
        String cookieHeader = getRequestHeaderCaseInsensitive("Cookie");
        if (cookieHeader != null && !cookieHeader.isEmpty()) {
            for (String cookie : cookieHeader.split(";")) {
                String[] parts = cookie.trim().split("=", 2);
                if (parts.length == 2) {
                    cookies.put(parts[0], parts[1]);
                }
            }
        }
        return cookies;
    }

    /**
     * Gets a specific cookie by name.
     */
    public String getCookie(String name) {
        Map<String, String> cookies = getCookies();
        return cookies.get(name);
    }

    /**
     * Gets a specific cookie by name with a default value.
     */
    public String getCookie(String name, String defaultValue) {
        String value = getCookie(name);
        return value != null ? value : defaultValue;
    }

    /**
     * Checks if a specific cookie exists.
     */
    public boolean hasCookie(String name) {
        return getCookie(name) != null;
    }

    /**
     * Removes a cookie by setting its max age to 0.
     */
    public Exchange removeCookie(String name) {
        addResponseHeader("Set-Cookie", name + "=; Max-Age=0; HttpOnly; SameSite=Strict");
        return this;
    }

    /**
     * Removes a cookie by setting its max age to 0 for a specific path.
     */
    public Exchange removeCookie(String name, String path) {
        addResponseHeader("Set-Cookie", name + "=; Max-Age=0; Path=" + path + "; HttpOnly; SameSite=Strict");
        return this;
    }

    public Exchange addCookie(String name, String value) {
        addResponseHeader("Set-Cookie", name + "=" + value + "; HttpOnly; Path=/; SameSite=Strict");
        return this;
    }

    public Exchange addCookie(String name, String value, int maxAgeSeconds) {
        addResponseHeader("Set-Cookie", name + "=" + value + "; HttpOnly; Path=/; SameSite=Strict; Max-Age=" + maxAgeSeconds);
        return this;
    }

    public Exchange addCookie(String name, String value, int maxAgeSeconds, String path) {
        addResponseHeader("Set-Cookie", name + "=" + value + "; HttpOnly; Path=" + path + "; SameSite=Strict; Max-Age=" + maxAgeSeconds);
        return this;
    }

    public Exchange setCacheControlMaxAge(long seconds) {
        addResponseHeader("Cache-Control", "max-age=" + seconds);
        return this;
    }

    public Exchange disableCache() {
        addResponseHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        addResponseHeader("Pragma", "no-cache");
        return this;
    }

    public Exchange setAttribute(String key, Object value) {
        attributes.put(key, value);
        return this;
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public <T> T getAttribute(String key, Class<T> clazz) {
        Object value = attributes.get(key);
        return clazz.isInstance(value) ? clazz.cast(value) : null;
    }

    // ===== SEND RESPONSE =====

    public void sendResponse() throws IOException {
        if (responseAlreadySent) {
            logger.log(Exchange.class, "Response already sent, returning", SimpleLogger.Level.DEBUG);
            return;
        }

        // Set default Content-Type for non-redirects
        if (!(responseStatusCode >= 300 && responseStatusCode < 400) &&
                !responseHeadersMap.containsKey("Content-Type") &&
                !responseBodyContent.isEmpty()) {
            addResponseHeader("Content-Type", "text/plain; charset=UTF-8");
        }

        // Apply all response headers
        for (Map.Entry<String, String> entry : responseHeadersMap.entrySet()) {
            httpExchange.getResponseHeaders().set(entry.getKey(), entry.getValue());
            logger.log(Exchange.class, "Response header: " + entry.getKey() + " = " + entry.getValue(), SimpleLogger.Level.DEBUG);
        }

        // Send response
        if (responseStatusCode >= 300 && responseStatusCode < 400) {
            logger.log(Exchange.class, "Sending redirect: " + responseStatusCode + " with no body", SimpleLogger.Level.INFO);
            httpExchange.sendResponseHeaders(responseStatusCode, -1);
        } else {
            byte[] responseBytes = responseBodyContent.getBytes(StandardCharsets.UTF_8);
            String preview = responseBodyContent.length() > 32
                    ? responseBodyContent.substring(0, 32) + "..."
                    : responseBodyContent;

            logger.log(Exchange.class, "Sending response: " + responseStatusCode + " with " + responseBytes.length + " bytes. First 32 chars: " + preview, SimpleLogger.Level.INFO);httpExchange.sendResponseHeaders(responseStatusCode, responseBytes.length);
            try (OutputStream os = httpExchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
        responseAlreadySent = true;
        logger.log(Exchange.class, "Response sent successfully", SimpleLogger.Level.DEBUG);
    }

    public void sendResponse(String body) throws IOException {
        this.responseBodyContent = body;
        sendResponse();
    }

    public void sendResponse(int statusCode, String body) throws IOException {
        this.responseStatusCode = statusCode;
        this.responseBodyContent = body;
        sendResponse();
    }

    public void sendJsonResponse(Object object) throws IOException {
        setResponseBodyAsJson(object);
        sendResponse();
    }

    public void sendJsonResponse(int statusCode, Object object) throws IOException {
        this.responseStatusCode = statusCode;
        setResponseBodyAsJson(object);
        sendResponse();
    }

    public void sendErrorResponse(String errorMessage) throws IOException {
        sendResponse(500, "{\"error\": \"" + errorMessage + "\"}");
    }

    public void sendErrorResponse(int statusCode, String errorMessage) throws IOException {
        sendResponse(statusCode, "{\"error\": \"" + errorMessage + "\"}");
    }

    public void sendNotFoundResponse() throws IOException {
        sendResponse(404, "{\"error\": \"Not Found\"}");
    }

    public void sendBadRequestResponse(String errorMessage) throws IOException {
        sendResponse(400, "{\"error\": \"" + errorMessage + "\"}");
    }

    public void sendUnauthorizedResponse() throws IOException {
        sendResponse(401, "{\"error\": \"Unauthorized\"}");
    }

    public void sendForbiddenResponse() throws IOException {
        sendResponse(403, "{\"error\": \"Forbidden\"}");
    }

    public void sendMethodNotAllowedResponse() throws IOException {
        sendResponse(405, "{\"error\": \"Method Not Allowed\"}");
    }

    public void sendMethodNotAllowedResponse(String... allowedMethods) throws IOException {
        if (allowedMethods.length > 0) {
            allowMethods(allowedMethods);
        }
        sendMethodNotAllowedResponse();
    }

    public void sendCreatedResponse() throws IOException {
        sendResponse(201, "{\"message\": \"Resource created successfully\"}");
    }

    public void sendCreatedResponse(String resourceLocation) throws IOException {
        addResponseHeader("Location", resourceLocation);
        sendResponse(201, "{\"message\": \"Resource created successfully\", \"location\": \"" + resourceLocation + "\"}");
    }

    public void sendNoContentResponse() throws IOException {
        sendResponse(204, "");
    }

    // ===== REQUEST INSPECTION HELPERS =====

    public boolean isHttpMethod(String methodName) {
        return this.method.equalsIgnoreCase(methodName);
    }

    public boolean isGetRequest() { return isHttpMethod("GET"); }
    public boolean isPostRequest() { return isHttpMethod("POST"); }
    public boolean isPutRequest() { return isHttpMethod("PUT"); }
    public boolean isDeleteRequest() { return isHttpMethod("DELETE"); }
    public boolean isPatchRequest() { return isHttpMethod("PATCH"); }
    public boolean isOptionsRequest() { return isHttpMethod("OPTIONS"); }
    public boolean isHeadRequest() { return isHttpMethod("HEAD"); }

    public boolean isJsonRequest() {
        return isJsonRequestBody() && !body.isEmpty();
    }

    public boolean isSecureConnection() {
        return "https".equalsIgnoreCase(requestUri.getScheme());
    }

    public String getUserAgent() {
        return getRequestHeaderCaseInsensitive("User-Agent");
    }

    public String getReferer() {
        return getRequestHeaderCaseInsensitive("Referer");
    }

    public String getOrigin() {
        return getRequestHeaderCaseInsensitive("Origin");
    }

    public String getHost() {
        return getRequestHeaderCaseInsensitive("Host");
    }

    public String getContentType() {
        return getRequestHeaderCaseInsensitive("Content-Type");
    }

    public int getContentLength() {
        String length = getRequestHeaderCaseInsensitive("Content-Length");
        try {
            return length != null ? Integer.parseInt(length) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public String getClientIpAddress() {
        String forwardedFor = getRequestHeaderCaseInsensitive("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = getRequestHeaderCaseInsensitive("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            return realIp;
        }
        return remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : null;
    }

    public boolean isAjaxRequest() {
        String requestedWith = getRequestHeaderCaseInsensitive("X-Requested-With");
        return "XMLHttpRequest".equals(requestedWith);
    }

    public boolean acceptsJsonResponse() {
        String accept = getRequestHeaderCaseInsensitive("Accept");
        return accept != null && accept.contains("application/json");
    }

    // ===== PRIVATE HELPERS =====

    private Map<String, String> parseQueryString(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) {
            return params;
        }

        for (String param : query.split("&")) {
            String[] keyValue = param.split("=", 2);
            try {
                String key = java.net.URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name());
                String value = keyValue.length == 2 ?
                        java.net.URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name()) : "";
                params.put(key, value);
            } catch (java.io.UnsupportedEncodingException e) {
                // Fallback - should never happen with UTF-8
                params.put(keyValue[0], keyValue.length == 2 ? keyValue[1] : "");
            }
        }
        return params;
    }

    // ===== FORM URLENCODED HELPERS =====

    /**
     * Checks if the request is application/x-www-form-urlencoded.
     */
    public boolean isFormUrlEncoded() {
        String contentType = getRequestHeaderCaseInsensitive("Content-Type");
        return contentType != null && contentType.startsWith("application/x-www-form-urlencoded");
    }

    /**
     * Parses application/x-www-form-urlencoded body into a map.
     */
    public Map<String, String> parseFormUrlEncoded() throws IOException {
        Map<String, String> params = new HashMap<>();

        if (!isFormUrlEncoded()) {
            return params;
        }

        String body = getRequestBody();
        if (body == null || body.isEmpty()) {
            return params;
        }

        return parseQueryString(body);
    }

    /**
     * Parses application/x-www-form-urlencoded body into a map with a default.
     */
    public Map<String, String> parseFormUrlEncoded(Map<String, String> defaultMap) {
        try {
            Map<String, String> result = parseFormUrlEncoded();
            if (result.isEmpty()) {
                return defaultMap;
            }
            return result;
        } catch (IOException e) {
            return defaultMap;
        }
    }

    /**
     * Gets a form field value from application/x-www-form-urlencoded body.
     */
    public String getFormFieldFromUrlEncoded(String name) throws IOException {
        Map<String, String> params = parseFormUrlEncoded();
        return params.get(name);
    }

    /**
     * Gets a form field value from application/x-www-form-urlencoded body with a default.
     */
    public String getFormFieldFromUrlEncoded(String name, String defaultValue) {
        try {
            String value = getFormFieldFromUrlEncoded(name);
            return value != null ? value : defaultValue;
        } catch (IOException e) {
            return defaultValue;
        }
    }

    /**
     * Gets multiple form fields from application/x-www-form-urlencoded body.
     */
    public Map<String, String> getFormFieldsFromUrlEncoded(String... names) throws IOException {
        Map<String, String> result = new HashMap<>();
        Map<String, String> params = parseFormUrlEncoded();

        for (String name : names) {
            result.put(name, params.get(name));
        }
        return result;
    }


    // ===== UPLOADED FILE CLASS =====

    public static class UploadedFile {
        private final String fieldName;
        private final String filename;
        private final String contentType;
        private final byte[] data;
        private final long size;

        public UploadedFile(String fieldName, String filename, String contentType, byte[] data) {
            this.fieldName = fieldName;
            this.filename = filename;
            this.contentType = contentType;
            this.data = data;
            this.size = data.length;
        }

        public String getFieldName() { return fieldName; }
        public String getFilename() { return filename; }
        public String getContentType() { return contentType; }
        public byte[] getData() { return data; }
        public long getSize() { return size; }
        public boolean isEmpty() { return data == null || data.length == 0; }

        @Override
        public String toString() {
            return "UploadedFile{" +
                    "filename='" + filename + '\'' +
                    ", contentType='" + contentType + '\'' +
                    ", size=" + size +
                    '}';
        }
    }

    // =============================================
    // ===== APACHE HTTP EXCHANGE CONTEXT =====
    // =============================================

    /**
     * Bridges HttpExchange to Apache Commons FileUpload RequestContext.
     * This is the critical piece that makes file uploads work!
     */
    private static class ApacheHttpExchangeContext implements org.apache.commons.fileupload.RequestContext {
        private final HttpExchange exchange;

        public ApacheHttpExchangeContext(HttpExchange exchange) {
            this.exchange = exchange;
        }

        @Override
        public String getCharacterEncoding() {
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType != null) {
                for (String part : contentType.split(";")) {
                    String trimmed = part.trim();
                    if (trimmed.startsWith("charset=")) {
                        return trimmed.substring("charset=".length()).replace("\"", "");
                    }
                }
            }
            return StandardCharsets.UTF_8.name();
        }

        @Override
        public String getContentType() {
            return exchange.getRequestHeaders().getFirst("Content-Type");
        }

        @Override
        public int getContentLength() {
            String length = exchange.getRequestHeaders().getFirst("Content-Length");
            if (length != null) {
                try {
                    return Integer.parseInt(length);
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
            return -1;
        }

        @Override
        public InputStream getInputStream() throws IOException {
            // Returns the raw request body stream - this is why we don't read it in the constructor!
            return exchange.getRequestBody();
        }
    }

    // ===== CLEANUP =====

    @Override
    public void close() {
        httpExchange.close();
    }

    static void setFileUploadLimit(int fileUploadLimit) {
        MAX_FILE_SIZE = fileUploadLimit;
    }
}