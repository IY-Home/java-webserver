package com.youfuns.webserver.interfaces;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.youfuns.logger.SimpleLogger;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Exchange implements AutoCloseable {
    private HttpExchange httpExchange;
    private final String method;
    private final URI requestUri;
    private final String path;
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
    private final Map<String, UploadedFile> uploadedFiles = new HashMap<>();
    private final Map<String, String> formFields = new HashMap<>();
    private boolean multipartParsed = false;

    // File upload configuration
    private static int MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
    private static final int MEMORY_THRESHOLD = 1024 * 1024; // 1MB - files larger go to disk

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final SimpleLogger logger;

    // Primary constructor - creates Exchange from parameters
    public Exchange(String method, URI requestUri, String protocol, InetSocketAddress remoteAddress,
                    Map<String, List<String>> requestHeaderMap, String body, SimpleLogger logger) {
        this.logger = logger;
        this.httpExchange = null; // No underlying HttpExchange

        this.method = method;
        this.requestUri = requestUri;
        this.path = requestUri.getPath();
        this.query = requestUri.getQuery();
        this.queryParams = parseQueryString(query);
        this.protocol = protocol;
        this.remoteAddress = remoteAddress;
        this.requestHeaderMap = Map.copyOf(requestHeaderMap);
        this.body = body;

        // ===== LOGGING =====
        String fullAddress = requestUri.toString();
        logger.log(Exchange.class, "Received " + method + " request to " + fullAddress, SimpleLogger.Level.INFO);
        logger.log(Exchange.class, "Request protocol: " + protocol, SimpleLogger.Level.DEBUG);
        logger.log(Exchange.class, "Remote address: " + remoteAddress, SimpleLogger.Level.DEBUG);
        logger.log(Exchange.class, "Request requestHeaderMap: " + requestHeaderMap, SimpleLogger.Level.DEBUG);
        logger.log(Exchange.class, "Query parameters: " + queryParams, SimpleLogger.Level.DEBUG);
        logger.log(Exchange.class, "Request body: " + (body != null && !body.isEmpty() ? body : "(empty)"), SimpleLogger.Level.DEBUG);
    }

    // Constructor from HttpExchange - delegates to primary constructor
    public Exchange(HttpExchange exchange, SimpleLogger logger) {
        this(
                exchange.getRequestMethod(),
                exchange.getRequestURI(),
                exchange.getProtocol(),
                exchange.getRemoteAddress(),
                exchange.getRequestHeaders(),
                extractBody(exchange, logger),
                logger
        );
        this.httpExchange = exchange; // Store reference for access to underlying exchange
    }

    // Helper method to extract body from HttpExchange
    private static String extractBody(HttpExchange exchange, SimpleLogger logger) {
        // IMPORTANT: Don't read body for multipart requests
        String contentType = getRequestHeaderCaseInsensitive(exchange.getRequestHeaders(), "Content-Type");
        boolean isMultipart = contentType != null && contentType.startsWith("multipart/form-data");

        if (isMultipart) {
            return "[multipart/form-data - stream preserved for parser]";
        }

        try (InputStream is = exchange.getRequestBody()) {
            byte[] bodyBytes = is.readAllBytes();
            return new String(bodyBytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.log(Exchange.class, "Failed to read request body: " + e.getMessage(), SimpleLogger.Level.WARN);
            return "";
        }
    }

    // Static helper method to get header case-insensitively
    private static String getRequestHeaderCaseInsensitive(Map<String, List<String>> requestHeaderMap, String headerName) {
        for (Map.Entry<String, List<String>> entry : requestHeaderMap.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(headerName)) {
                List<String> values = entry.getValue();
                return values != null && !values.isEmpty() ? values.get(0) : null;
            }
        }
        return null;
    }

    // ===== REQUEST GETTERS =====

    /**
     * URL-encodes a string using UTF-8.
     */
    public static String urlEncode(String value) {
        if (value == null) return null;
        try {
            String encoded = java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name());
            // No logger here since it's static
            return encoded;
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
            String decoded = java.net.URLDecoder.decode(value, StandardCharsets.UTF_8.name());
            // No logger here since it's static
            return decoded;
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
        // No logger here since it's static
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
        // No logger here since it's static
        return result.toString();
    }

    public String getHttpMethod() {
        logger.log(Exchange.class, "Getting HTTP method: " + method, SimpleLogger.Level.DEBUG);
        return method;
    }

    public URI getRequestUri() {
        logger.log(Exchange.class, "Getting request URI: " + requestUri, SimpleLogger.Level.DEBUG);
        return requestUri;
    }

    public String getRequestPath() {
        logger.log(Exchange.class, "Getting request path: " + path, SimpleLogger.Level.DEBUG);
        return path;
    }

    public String getQueryString() {
        logger.log(Exchange.class, "Getting query string: " + query, SimpleLogger.Level.DEBUG);
        return query;
    }

    public Map<String, String> getQueryParameters() {
        logger.log(Exchange.class, "Getting query parameters: " + queryParams, SimpleLogger.Level.DEBUG);
        return queryParams;
    }

    public String getProtocol() {
        logger.log(Exchange.class, "Getting protocol: " + protocol, SimpleLogger.Level.DEBUG);
        return protocol;
    }

    // ===== REQUEST HEADER HELPERS =====

    public InetSocketAddress getRemoteSocketAddress() {
        logger.log(Exchange.class, "Getting remote socket address: " + remoteAddress, SimpleLogger.Level.DEBUG);
        return remoteAddress;
    }

    public Map<String, List<String>> getAllRequestHeaders() {
        logger.log(Exchange.class, "Getting all request requestHeaderMap: " + requestHeaderMap, SimpleLogger.Level.DEBUG);
        return requestHeaderMap;
    }

    public String getRequestBody() {
        logger.log(Exchange.class, "Getting request body: " + (body != null ? body.length() + " chars" : "null"), SimpleLogger.Level.DEBUG);
        return body;
    }

    public HttpExchange getUnderlyingHttpExchange() {
        logger.log(Exchange.class, "Getting underlying HttpExchange", SimpleLogger.Level.DEBUG);
        return httpExchange;
    }

    public String getRequestHeader(String name) {
        List<String> values = requestHeaderMap.get(name);
        String value = values != null && !values.isEmpty() ? values.get(0) : null;
        logger.log(Exchange.class, "Getting request header '" + name + "': " + value, SimpleLogger.Level.DEBUG);
        return value;
    }

    public String getRequestHeaderCaseInsensitive(String name) {
        for (String headerName : requestHeaderMap.keySet()) {
            if (headerName.equalsIgnoreCase(name)) {
                String value = getRequestHeader(headerName);
                logger.log(Exchange.class, "Getting request header (case-insensitive) '" + name + "': " + value, SimpleLogger.Level.DEBUG);
                return value;
            }
        }
        logger.log(Exchange.class, "Request header (case-insensitive) '" + name + "' not found", SimpleLogger.Level.DEBUG);
        return null;
    }

    // ===== AUTHORIZATION / JWT HELPERS =====

    public List<String> getRequestHeaders(String name) {
        List<String> values = requestHeaderMap.get(name);
        logger.log(Exchange.class, "Getting request requestHeaderMap '" + name + "': " + values, SimpleLogger.Level.DEBUG);
        return values;
    }

    public Map<String, List<String>> getRequestHeaderMap() {
        Map<String, List<String>> headerMap = requestHeaderMap;
        logger.log(Exchange.class, "Getting request header map: " + headerMap, SimpleLogger.Level.DEBUG);
        return headerMap;
    }

    public boolean hasRequestHeader(String name) {
        boolean has = requestHeaderMap.containsKey(name);
        logger.log(Exchange.class, "Checking request header '" + name + "': " + has, SimpleLogger.Level.DEBUG);
        return has;
    }

    // ===== QUERY PARAMETER HELPERS =====

    public boolean hasRequestHeaderCaseInsensitive(String name) {
        for (String headerName : requestHeaderMap.keySet()) {
            if (headerName.equalsIgnoreCase(name)) {
                logger.log(Exchange.class, "Checking request header (case-insensitive) '" + name + "': true", SimpleLogger.Level.DEBUG);
                return true;
            }
        }
        logger.log(Exchange.class, "Checking request header (case-insensitive) '" + name + "': false", SimpleLogger.Level.DEBUG);
        return false;
    }

    public String getBearerToken() {
        String auth = getRequestHeaderCaseInsensitive("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring(7);
            logger.log(Exchange.class, "Bearer token found: " + (token != null ? token.substring(0, Math.min(token.length(), 20)) + "..." : "null"), SimpleLogger.Level.DEBUG);
            return token;
        }
        logger.log(Exchange.class, "No Bearer token found", SimpleLogger.Level.DEBUG);
        return null;
    }

    public Map<String, Object> decodeJwtClaims() {
        String token = getBearerToken();
        if (token == null) {
            logger.log(Exchange.class, "No Bearer token to decode JWT claims", SimpleLogger.Level.DEBUG);
            return null;
        }

        try {
            String[] parts = token.split("\\.");
            if (parts.length == 3) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                Map<String, Object> claims = objectMapper.readValue(payload, Map.class);
                logger.log(Exchange.class, "Decoded JWT claims: " + claims, SimpleLogger.Level.DEBUG);
                return claims;
            }
        } catch (Exception e) {
            logger.log(Exchange.class, "Failed to decode JWT claims: " + e.getMessage(), SimpleLogger.Level.WARN);
        }
        logger.log(Exchange.class, "JWT claims could not be decoded", SimpleLogger.Level.DEBUG);
        return null;
    }

    public String getJwtClaim(String claimName) {
        Map<String, Object> claims = decodeJwtClaims();
        String value = claims != null ? String.valueOf(claims.get(claimName)) : null;
        logger.log(Exchange.class, "Getting JWT claim '" + claimName + "': " + value, SimpleLogger.Level.DEBUG);
        return value;
    }

    public String getQueryParameter(String name) {
        String value = queryParams.get(name);
        logger.log(Exchange.class, "Getting query parameter '" + name + "': " + value, SimpleLogger.Level.DEBUG);
        return value;
    }

    public String getQueryParameter(String name, String defaultValue) {
        String value = queryParams.getOrDefault(name, defaultValue);
        logger.log(Exchange.class, "Getting query parameter '" + name + "' with default: " + value, SimpleLogger.Level.DEBUG);
        return value;
    }

    public int getQueryParameterAsInt(String name) {
        String value = queryParams.get(name);
        try {
            int result = value != null ? Integer.parseInt(value) : 0;
            logger.log(Exchange.class, "Getting query parameter '" + name + "' as int: " + result, SimpleLogger.Level.DEBUG);
            return result;
        } catch (NumberFormatException e) {
            logger.log(Exchange.class, "Failed to parse query parameter '" + name + "' as int, returning 0", SimpleLogger.Level.WARN);
            return 0;
        }
    }

    public int getQueryParameterAsInt(String name, int defaultValue) {
        String value = queryParams.get(name);
        try {
            int result = value != null ? Integer.parseInt(value) : defaultValue;
            logger.log(Exchange.class, "Getting query parameter '" + name + "' as int with default: " + result, SimpleLogger.Level.DEBUG);
            return result;
        } catch (NumberFormatException e) {
            logger.log(Exchange.class, "Failed to parse query parameter '" + name + "' as int, returning default: " + defaultValue, SimpleLogger.Level.WARN);
            return defaultValue;
        }
    }

    // ===== URL ENCODING HELPERS =====

    public long getQueryParameterAsLong(String name, long defaultValue) {
        String value = queryParams.get(name);
        try {
            long result = value != null ? Long.parseLong(value) : defaultValue;
            logger.log(Exchange.class, "Getting query parameter '" + name + "' as long with default: " + result, SimpleLogger.Level.DEBUG);
            return result;
        } catch (NumberFormatException e) {
            logger.log(Exchange.class, "Failed to parse query parameter '" + name + "' as long, returning default: " + defaultValue, SimpleLogger.Level.WARN);
            return defaultValue;
        }
    }

    public double getQueryParameterAsDouble(String name, double defaultValue) {
        String value = queryParams.get(name);
        try {
            double result = value != null ? Double.parseDouble(value) : defaultValue;
            logger.log(Exchange.class, "Getting query parameter '" + name + "' as double with default: " + result, SimpleLogger.Level.DEBUG);
            return result;
        } catch (NumberFormatException e) {
            logger.log(Exchange.class, "Failed to parse query parameter '" + name + "' as double, returning default: " + defaultValue, SimpleLogger.Level.WARN);
            return defaultValue;
        }
    }

    public boolean getQueryParameterAsBoolean(String name, boolean defaultValue) {
        String value = queryParams.get(name);
        boolean result = value != null ? Boolean.parseBoolean(value) : defaultValue;
        logger.log(Exchange.class, "Getting query parameter '" + name + "' as boolean with default: " + result, SimpleLogger.Level.DEBUG);
        return result;
    }

    public List<String> getAllQueryParameterNames() {
        List<String> names = new ArrayList<>(queryParams.keySet());
        logger.log(Exchange.class, "Getting all query parameter names: " + names, SimpleLogger.Level.DEBUG);
        return names;
    }

    // ===== REDIRECT HELPERS =====

    /**
     * Sends a 302 Found redirect to the given URL.
     */
    public void redirect(String url) throws IOException {
        logger.log(Exchange.class, "Redirecting (302) to: " + url, SimpleLogger.Level.INFO);
        addResponseHeader("Location", url);
        sendResponse(302, "");
    }

    /**
     * Sends a 301 Moved Permanently redirect to the given URL.
     */
    public void redirectPermanent(String url) throws IOException {
        logger.log(Exchange.class, "Redirecting permanently (301) to: " + url, SimpleLogger.Level.INFO);
        addResponseHeader("Location", url);
        sendResponse(301, "");
    }

    /**
     * Sends a 303 See Other redirect (POST to GET).
     */
    public void redirectSeeOther(String url) throws IOException {
        logger.log(Exchange.class, "Redirecting (303 See Other) to: " + url, SimpleLogger.Level.INFO);
        addResponseHeader("Location", url);
        sendResponse(303, "");
    }

    /**
     * Sends a redirect with a custom status code.
     */
    public void redirect(int statusCode, String url) throws IOException {
        logger.log(Exchange.class, "Redirecting (" + statusCode + ") to: " + url, SimpleLogger.Level.INFO);
        addResponseHeader("Location", url);
        sendResponse(statusCode, "");
    }


    // ===== REQUEST BODY HELPERS (Non-multipart) =====

    public <T> T parseBodyAsJson(Class<T> targetClass) throws IOException {
        logger.log(Exchange.class, "Parsing body as JSON to class: " + targetClass.getName(), SimpleLogger.Level.DEBUG);
        try {
            T result = objectMapper.readValue(body, targetClass);
            logger.log(Exchange.class, "Successfully parsed body as JSON to " + targetClass.getName(), SimpleLogger.Level.DEBUG);
            return result;
        } catch (JsonProcessingException e) {
            logger.log(Exchange.class, "Failed to parse JSON request body: " + e.getMessage(), SimpleLogger.Level.WARN);
            throw new IOException("Failed to parse JSON request body", e);
        }
    }

    public Map<String, Object> parseBodyAsJsonMap() throws IOException {
        logger.log(Exchange.class, "Parsing body as JSON map", SimpleLogger.Level.DEBUG);
        try {
            Map<String, Object> result = objectMapper.readValue(body, Map.class);
            logger.log(Exchange.class, "Successfully parsed body as JSON map: " + result, SimpleLogger.Level.DEBUG);
            return result;
        } catch (JsonProcessingException e) {
            logger.log(Exchange.class, "Failed to parse JSON request body as Map: " + e.getMessage(), SimpleLogger.Level.WARN);
            throw new IOException("Failed to parse JSON request body as Map", e);
        }
    }

    public Map<String, Object> parseBodyAsJsonMap(Map<String, Object> defaultMap) throws IOException {
        logger.log(Exchange.class, "Parsing body as JSON map with default", SimpleLogger.Level.DEBUG);
        try {
            Map<String, Object> result = objectMapper.readValue(body, Map.class);
            logger.log(Exchange.class, "Successfully parsed body as JSON map: " + result, SimpleLogger.Level.DEBUG);
            return result;
        } catch (JsonProcessingException e) {
            logger.log(Exchange.class, "Failed to parse JSON, using default map: " + defaultMap, SimpleLogger.Level.WARN);
            return defaultMap;
        }
    }

    public <T> List<T> parseBodyAsJsonList(Class<T> elementClass) throws IOException {
        logger.log(Exchange.class, "Parsing body as JSON list of: " + elementClass.getName(), SimpleLogger.Level.DEBUG);
        try {
            List<T> result = objectMapper.readValue(body,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, elementClass));
            logger.log(Exchange.class, "Successfully parsed body as JSON list: size=" + result.size(), SimpleLogger.Level.DEBUG);
            return result;
        } catch (JsonProcessingException e) {
            logger.log(Exchange.class, "Failed to parse JSON request body as List: " + e.getMessage(), SimpleLogger.Level.WARN);
            throw new IOException("Failed to parse JSON request body as List", e);
        }
    }

    // ===== JSON PARAMETER HELPERS =====

    public Object getJsonParameter(String name) {
        logger.log(Exchange.class, "Getting JSON parameter '" + name + "'", SimpleLogger.Level.DEBUG);
        try {
            Object value = parseBodyAsJsonMap().get(name);
            logger.log(Exchange.class, "JSON parameter '" + name + "': " + value, SimpleLogger.Level.DEBUG);
            return value;
        } catch (IOException e) {
            logger.log(Exchange.class, "Failed to get JSON parameter '" + name + "': " + e.getMessage(), SimpleLogger.Level.WARN);
            return null;
        }
    }

    public <T> T getJsonParameter(Class<T> clazz, String name, T defaultValue) {
        logger.log(Exchange.class, "Getting JSON parameter '" + name + "' as " + clazz.getName(), SimpleLogger.Level.DEBUG);
        try {
            Object param = getJsonParameter(name);
            if (param == null) {
                logger.log(Exchange.class, "JSON parameter '" + name + "' is null, returning default", SimpleLogger.Level.DEBUG);
                return defaultValue;
            }
            T result = clazz.cast(param);
            logger.log(Exchange.class, "JSON parameter '" + name + "': " + result, SimpleLogger.Level.DEBUG);
            return result;
        } catch (ClassCastException e) {
            logger.log(Exchange.class, "JSON parameter '" + name + "' cast failed, returning default: " + e.getMessage(), SimpleLogger.Level.WARN);
            return defaultValue;
        }
    }

    public int getJsonParameterAsInt(String name, int defaultValue) {
        Integer value = getJsonParameter(Integer.class, name, defaultValue);
        int result = value != null ? value : defaultValue;
        logger.log(Exchange.class, "Getting JSON parameter '" + name + "' as int: " + result, SimpleLogger.Level.DEBUG);
        return result;
    }

    public long getJsonParameterAsLong(String name, long defaultValue) {
        Long value = getJsonParameter(Long.class, name, defaultValue);
        long result = value != null ? value : defaultValue;
        logger.log(Exchange.class, "Getting JSON parameter '" + name + "' as long: " + result, SimpleLogger.Level.DEBUG);
        return result;
    }

    public double getJsonParameterAsDouble(String name, double defaultValue) {
        Double value = getJsonParameter(Double.class, name, defaultValue);
        double result = value != null ? value : defaultValue;
        logger.log(Exchange.class, "Getting JSON parameter '" + name + "' as double: " + result, SimpleLogger.Level.DEBUG);
        return result;
    }

    public float getJsonParameterAsFloat(String name, float defaultValue) {
        Float value = getJsonParameter(Float.class, name, defaultValue);
        float result = value != null ? value : defaultValue;
        logger.log(Exchange.class, "Getting JSON parameter '" + name + "' as float: " + result, SimpleLogger.Level.DEBUG);
        return result;
    }

    public boolean getJsonParameterAsBoolean(String name, boolean defaultValue) {
        Boolean value = getJsonParameter(Boolean.class, name, defaultValue);
        boolean result = value != null ? value : defaultValue;
        logger.log(Exchange.class, "Getting JSON parameter '" + name + "' as boolean: " + result, SimpleLogger.Level.DEBUG);
        return result;
    }

    public short getJsonParameterAsShort(String name, short defaultValue) {
        Short value = getJsonParameter(Short.class, name, defaultValue);
        short result = value != null ? value : defaultValue;
        logger.log(Exchange.class, "Getting JSON parameter '" + name + "' as short: " + result, SimpleLogger.Level.DEBUG);
        return result;
    }

    public byte getJsonParameterAsByte(String name, byte defaultValue) {
        Byte value = getJsonParameter(Byte.class, name, defaultValue);
        byte result = value != null ? value : defaultValue;
        logger.log(Exchange.class, "Getting JSON parameter '" + name + "' as byte: " + result, SimpleLogger.Level.DEBUG);
        return result;
    }

    public char getJsonParameterAsChar(String name, char defaultValue) {
        Character value = getJsonParameter(Character.class, name, defaultValue);
        char result = value != null ? value : defaultValue;
        logger.log(Exchange.class, "Getting JSON parameter '" + name + "' as char: " + result, SimpleLogger.Level.DEBUG);
        return result;
    }

    // For non-primitive types, you can directly use the generic method or create convenience wrappers:
    public String getJsonParameterAsString(String name, String defaultValue) {
        String result = getJsonParameter(String.class, name, defaultValue);
        logger.log(Exchange.class, "Getting JSON parameter '" + name + "' as string: " + result, SimpleLogger.Level.DEBUG);
        return result;
    }

    public String getJsonName(String defaultValue) {
        String result = getJsonParameterAsString("name", defaultValue);
        logger.log(Exchange.class, "Getting JSON name: " + result, SimpleLogger.Level.DEBUG);
        return result;
    }

    public String getJsonEmail(String defaultValue) {
        String result = getJsonParameterAsString("email", defaultValue);
        logger.log(Exchange.class, "Getting JSON email: " + result, SimpleLogger.Level.DEBUG);
        return result;
    }

    public String getJsonUsername(String defaultValue) {
        String result = getJsonParameterAsString("username", defaultValue);
        logger.log(Exchange.class, "Getting JSON username: " + result, SimpleLogger.Level.DEBUG);
        return result;
    }

    public String getJsonPassword(String defaultValue) {
        String result = getJsonParameterAsString("password", defaultValue);
        logger.log(Exchange.class, "Getting JSON password: " + (result != null ? "***" : null), SimpleLogger.Level.DEBUG);
        return result;
    }

    public boolean isJsonRequestBody() {
        String contentType = getRequestHeaderCaseInsensitive("Content-Type");
        boolean isJson = contentType != null && contentType.contains("application/json");
        logger.log(Exchange.class, "Checking if request body is JSON: " + isJson, SimpleLogger.Level.DEBUG);
        return isJson;
    }

    public boolean hasRequestBody() {
        boolean hasBody = body != null && !body.isEmpty();
        logger.log(Exchange.class, "Checking if request has body: " + hasBody, SimpleLogger.Level.DEBUG);
        return hasBody;
    }

    // ===== FILE UPLOAD HELPERS =====

    /**
     * Checks if the request is a multipart/form-data request.
     */
    public boolean isMultipartRequest() {
        String contentType = getRequestHeaderCaseInsensitive("Content-Type");
        boolean isMultipart = contentType != null && contentType.startsWith("multipart/form-data");
        logger.log(Exchange.class, "Checking if request is multipart: " + isMultipart, SimpleLogger.Level.DEBUG);
        return isMultipart;
    }

    /**
     * Parses the multipart request and populates uploadedFiles and formFields.
     * This is called automatically when needed.
     */
    private void parseMultipartIfNeeded() {
        if (multipartParsed) {
            logger.log(Exchange.class, "Multipart already parsed", SimpleLogger.Level.DEBUG);
            return;
        }
        if (!isMultipartRequest()) {
            logger.log(Exchange.class, "Request is not multipart, skipping parse", SimpleLogger.Level.DEBUG);
            return;
        }

        try {
            parseMultipart();
            multipartParsed = true;
            logger.log(Exchange.class, "Multipart parsed successfully", SimpleLogger.Level.DEBUG);
        } catch (IOException e) {
            logger.log(Exchange.class, "Failed to parse multipart: " + e.getMessage(), SimpleLogger.Level.ERROR);
        }
    }

    /**
     * Parse multipart request using Apache Commons FileUpload.
     */
    private void parseMultipart() throws IOException {
        logger.log(Exchange.class, "Starting multipart parse", SimpleLogger.Level.DEBUG);
        DiskFileItemFactory factory = new DiskFileItemFactory();
        factory.setSizeThreshold(MEMORY_THRESHOLD);
        factory.setRepository(new File(System.getProperty("java.io.tmpdir")));

        ServletFileUpload upload = new ServletFileUpload(factory);
        upload.setSizeMax(MAX_FILE_SIZE);

        try {
            // Use the custom ApacheHttpExchangeContext to bridge HttpExchange to Commons FileUpload
            List<FileItem> items = upload.parseRequest(new ApacheHttpExchangeContext(httpExchange));
            logger.log(Exchange.class, "Multipart parse complete, found " + items.size() + " items", SimpleLogger.Level.DEBUG);

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
            logger.log(Exchange.class, "Failed to parse multipart request: " + e.getMessage(), SimpleLogger.Level.ERROR);
            throw new IOException("Failed to parse multipart request: " + e.getMessage(), e);
        }
    }

    /**
     * Checks if a file with the given field name was uploaded.
     */
    public boolean hasFile(String fieldName) {
        parseMultipartIfNeeded();
        boolean has = uploadedFiles.containsKey(fieldName);
        logger.log(Exchange.class, "Checking if file '" + fieldName + "' exists: " + has, SimpleLogger.Level.DEBUG);
        return has;
    }

    /**
     * Gets an uploaded file by its form field name.
     */
    public UploadedFile getFile(String fieldName) {
        parseMultipartIfNeeded();
        UploadedFile file = uploadedFiles.get(fieldName);
        logger.log(Exchange.class, "Getting file '" + fieldName + "': " + file, SimpleLogger.Level.DEBUG);
        return file;
    }

    /**
     * Gets all uploaded files.
     */
    public Map<String, UploadedFile> getAllFiles() {
        parseMultipartIfNeeded();
        logger.log(Exchange.class, "Getting all files: " + uploadedFiles.keySet(), SimpleLogger.Level.DEBUG);
        return Collections.unmodifiableMap(uploadedFiles);
    }

    /**
     * Gets a form field value (non-file field).
     */
    public String getFormField(String fieldName) {
        parseMultipartIfNeeded();
        String value = formFields.get(fieldName);
        logger.log(Exchange.class, "Getting form field '" + fieldName + "': " + value, SimpleLogger.Level.DEBUG);
        return value;
    }

    /**
     * Gets all form fields (non-file fields).
     */
    public Map<String, String> getAllFormFields() {
        parseMultipartIfNeeded();
        logger.log(Exchange.class, "Getting all form fields: " + formFields, SimpleLogger.Level.DEBUG);
        return Collections.unmodifiableMap(formFields);
    }

    /**
     * Checks if an uploaded file has a specific extension.
     */
    public boolean isExtension(UploadedFile file, String extension) {
        if (file == null || file.filename == null) {
            logger.log(Exchange.class, "File or filename is null", SimpleLogger.Level.DEBUG);
            return false;
        }
        String ext = extension.startsWith(".") ? extension : "." + extension;
        boolean matches = file.filename.toLowerCase().endsWith(ext.toLowerCase());
        logger.log(Exchange.class, "Checking file extension '" + extension + "' for " + file.filename + ": " + matches, SimpleLogger.Level.DEBUG);
        return matches;
    }

    /**
     * Checks if a file's content matches a specific byte pattern (magic bytes).
     */
    public boolean isTypeByBytes(UploadedFile file, byte[] startBytes, int offset) {
        if (file == null || file.data == null) {
            logger.log(Exchange.class, "File or data is null", SimpleLogger.Level.DEBUG);
            return false;
        }
        if (offset + startBytes.length > file.data.length) {
            logger.log(Exchange.class, "File too short for byte pattern check", SimpleLogger.Level.DEBUG);
            return false;
        }

        for (int i = 0; i < startBytes.length; i++) {
            if (file.data[offset + i] != startBytes[i]) {
                logger.log(Exchange.class, "Byte pattern mismatch at index " + i, SimpleLogger.Level.DEBUG);
                return false;
            }
        }
        logger.log(Exchange.class, "Byte pattern matched", SimpleLogger.Level.DEBUG);
        return true;
    }

    /**
     * Convenience: checks if a file is a PNG by its magic bytes.
     */
    public boolean isPNG(UploadedFile file) {
        byte[] pngSignature = {(byte) 0x89, (byte) 0x50, (byte) 0x4E, (byte) 0x47};
        boolean result = isTypeByBytes(file, pngSignature, 0);
        logger.log(Exchange.class, "Checking if file is PNG: " + result, SimpleLogger.Level.DEBUG);
        return result;
    }

    /**
     * Convenience: checks if a file is a JPEG by its magic bytes.
     */
    public boolean isJPEG(UploadedFile file) {
        byte[] jpegSignature = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
        boolean result = isTypeByBytes(file, jpegSignature, 0);
        logger.log(Exchange.class, "Checking if file is JPEG: " + result, SimpleLogger.Level.DEBUG);
        return result;
    }

    /**
     * Convenience: checks if a file is a PDF by its magic bytes.
     */
    public boolean isPDF(UploadedFile file) {
        byte[] pdfSignature = {(byte) 0x25, (byte) 0x50, (byte) 0x44, (byte) 0x46};
        boolean result = isTypeByBytes(file, pdfSignature, 0);
        logger.log(Exchange.class, "Checking if file is PDF: " + result, SimpleLogger.Level.DEBUG);
        return result;
    }

    /**
     * Convenience: checks if a file is JSON by its magic bytes.
     * JSON files typically start with '{' or '['.
     */
    public boolean isJSON(UploadedFile file) {
        byte[] jsonSignature1 = {(byte) 0x7B}; // '{'
        byte[] jsonSignature2 = {(byte) 0x5B}; // '['
        boolean result = isTypeByBytes(file, jsonSignature1, 0) ||
                isTypeByBytes(file, jsonSignature2, 0);
        logger.log(Exchange.class, "Checking if file is JSON: " + result, SimpleLogger.Level.DEBUG);
        return result;
    }

    /**
     * Saves an uploaded file to a directory.
     */
    public String saveFileIn(UploadedFile file, String saveDir) throws IOException {
        if (file == null) {
            logger.log(Exchange.class, "File is null, cannot save", SimpleLogger.Level.WARN);
            return "";
        }
        if (saveDir.contains("../") || saveDir.contains("..\\")) {
            logger.log(Exchange.class, "Save directory contains potential path traversal attempt", SimpleLogger.Level.WARN);
            this.setAttribute("_path_traversal_", true);
            return "";
        }
        logger.log(Exchange.class, "Saving file '" + file.filename + "' to directory: " + saveDir, SimpleLogger.Level.INFO);
        Path dir = Paths.get(saveDir);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
            logger.log(Exchange.class, "Created directory: " + saveDir, SimpleLogger.Level.DEBUG);
        }
        Path savePath = dir.resolve(file.filename);
        logger.log(Exchange.class, "Saving to path: " + savePath, SimpleLogger.Level.DEBUG);
        return saveFileAt(file, savePath.toString());
    }

    /**
     * Saves an uploaded file to a specific location.
     */
    public String saveFileAt(UploadedFile file, String saveLocation) throws IOException {
        if (file == null) {
            logger.log(Exchange.class, "File is null, cannot save", SimpleLogger.Level.WARN);
            return "";
        }
        if (saveLocation.contains("../") || saveLocation.contains("..\\")) {
            logger.log(Exchange.class, "Save location contains potential path traversal attempt", SimpleLogger.Level.WARN);
            this.setAttribute("_path_traversal_", true);
            return "";
        }
        logger.log(Exchange.class, "Saving file '" + file.filename + "' to: " + saveLocation, SimpleLogger.Level.INFO);
        Path savePath = Paths.get(saveLocation);

        Path parent = savePath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
            logger.log(Exchange.class, "Created parent directories: " + parent, SimpleLogger.Level.DEBUG);
        }

        Files.write(savePath, file.data);
        logger.log(Exchange.class, "File saved successfully: " + saveLocation, SimpleLogger.Level.INFO);
        return saveLocation;
    }

    /**
     * Saves an uploaded file with a generated unique name.
     * @param file The uploaded file
     * @param saveDir The directory to save to
     * @param preserveOriginalName If true, keep the original filename; if false, generate a UUID name
     * @return The path where the file was saved
     */
    public String saveFileSafe(UploadedFile file, String saveDir, boolean preserveOriginalName) throws IOException {
        if (file == null) {
            logger.log(Exchange.class, "File is null, cannot save", SimpleLogger.Level.WARN);
            return null;
        }

        if (saveDir.contains("../") || saveDir.contains("..\\")) {
            logger.log(Exchange.class, "Save directory contains potential path traversal attempt", SimpleLogger.Level.WARN);
            this.setAttribute("_path_traversal_", true);
            return "";
        }

        logger.log(Exchange.class, "Saving file safely '" + file.filename + "' to directory: " + saveDir + ", preserveOriginalName: " + preserveOriginalName, SimpleLogger.Level.INFO);

        Path dir = Paths.get(saveDir);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
            logger.log(Exchange.class, "Created directory: " + saveDir, SimpleLogger.Level.DEBUG);
        }

        String filename;
        if (preserveOriginalName) {
            filename = file.filename;
            logger.log(Exchange.class, "Preserving original filename: " + filename, SimpleLogger.Level.DEBUG);
        } else {
            String ext = file.getExtension();
            filename = UUID.randomUUID().toString() + ext;
            logger.log(Exchange.class, "Generated UUID filename: " + filename, SimpleLogger.Level.DEBUG);
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
                logger.log(Exchange.class, "Duplicate found, trying: " + filename, SimpleLogger.Level.DEBUG);
            }
        }

        Files.write(savePath, file.data);
        String result = savePath.toString();
        logger.log(Exchange.class, "File saved safely at: " + result, SimpleLogger.Level.INFO);
        return result;
    }

    public short getAndSaveAt(String filename, String[] extensions, FileAction<UploadedFile> fileAction) throws IOException {
        logger.log(Exchange.class, "getAndSaveAt called with filename: " + filename + ", extensions: " + Arrays.toString(extensions), SimpleLogger.Level.DEBUG);

        if (!this.isMultipartRequest()) {
            logger.log(Exchange.class, "Not a multipart request, returning -1", SimpleLogger.Level.WARN);
            return -1;  // "Expected multipart/form-data"
        }

        if (!this.hasFile(filename)) {
            logger.log(Exchange.class, "File '" + filename + "' not found, returning -2", SimpleLogger.Level.WARN);
            return -2; // "No file uploaded"
        }

        UploadedFile file = this.getFile(filename);

        if (file == null) {
            logger.log(Exchange.class, "File '" + filename + "' is null, returning -2", SimpleLogger.Level.WARN);
            return -2;
        }

        boolean isExtensionMatch = false;
        for (String extension : extensions) {
            if (isExtension(file, extension)) {
                isExtensionMatch = true;
                logger.log(Exchange.class, "Extension matched: " + extension, SimpleLogger.Level.DEBUG);
                break;
            }
        }
        if (!isExtensionMatch && !extensions[0].equals("all")) {
            logger.log(Exchange.class, "Extension mismatch, returning -3", SimpleLogger.Level.WARN);
            return -3; // "Only " + extensionsAccepted + " accepted"
        }

        logger.log(Exchange.class, "File validation passed, executing file action", SimpleLogger.Level.DEBUG);
        fileAction.accept(file);
        logger.log(Exchange.class, "File action executed successfully, returning 0", SimpleLogger.Level.INFO);
        return 0;
    }

    public short getAndSaveAt(String filename, String[] extensions, String savePath) throws IOException {
        logger.log(Exchange.class, "getAndSaveAt called with filename: " + filename + ", extensions: " + Arrays.toString(extensions) + ", savePath: " + savePath, SimpleLogger.Level.DEBUG);
        short result = this.getAndSaveAt(filename, extensions, file -> {
            logger.log(Exchange.class, "Saving file to: " + savePath, SimpleLogger.Level.DEBUG);
            saveFileAt(file, savePath);
        });
        if (this.getAttribute("_path_traversal_", Boolean.class) != null) {
            return -4;
        }
        return result;
    }

    public void serveFile(String filePath) throws IOException {
        logger.log(Exchange.class, "Serving file: " + filePath, SimpleLogger.Level.INFO);
        if (filePath.contains("../")) {
            logger.log(Exchange.class, "File path contains potential path traversal attempt", SimpleLogger.Level.WARN);
        }
        String expandedPath = filePath.replace("~", System.getProperty("user.home"));
        Path file = Paths.get(expandedPath);
        if (!Files.exists(file) || Files.isDirectory(file)) {
            logger.log(Exchange.class, "File does not exist: " + filePath, SimpleLogger.Level.ERROR);
            throw new IllegalArgumentException("File does not exist: " + filePath);
        }
        try {
            String mimeType = URLConnection.getFileNameMap().getContentTypeFor(file.toString());
            if (mimeType == null) mimeType = "application/octet-stream";
            logger.log(Exchange.class, "Serving file with MIME type: " + mimeType, SimpleLogger.Level.DEBUG);

            this.addResponseHeader("Content-Type", mimeType);
            this.addResponseHeader("Content-Length", String.valueOf(Files.size(file)));
            this.addResponseHeader("Cache-Control", "max-age=3600");

            this.getUnderlyingHttpExchange().sendResponseHeaders(200, Files.size(file));
            logger.log(Exchange.class, "Response requestHeaderMap sent, writing file content", SimpleLogger.Level.DEBUG);
            try (OutputStream os = this.getUnderlyingHttpExchange().getResponseBody();
                 InputStream is = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    os.write(buffer, 0, bytesRead);
                }
            }
            logger.log(Exchange.class, "File served successfully: " + filePath, SimpleLogger.Level.INFO);
        } catch (IOException e) {
            logger.log(Exchange.class, "Failed to serve file: " + e.getMessage(), SimpleLogger.Level.ERROR);
            throw e;
        }
    }

    // ===== RESPONSE BUILDERS =====

    public Exchange setResponseStatusCode(int statusCode) {
        logger.log(Exchange.class, "Setting response status code to: " + statusCode, SimpleLogger.Level.DEBUG);
        this.responseStatusCode = statusCode;
        return this;
    }

    public Exchange setResponseBody(String body) {
        logger.log(Exchange.class, "Setting response body to: " + (body != null ? body.length() + " chars" : "null"), SimpleLogger.Level.DEBUG);
        this.responseBodyContent = body;
        return this;
    }

    public Exchange setResponseBodyAsJson(Object object) {
        logger.log(Exchange.class, "Setting response body as JSON from object: " + object, SimpleLogger.Level.DEBUG);
        try {
            this.responseBodyContent = objectMapper.writeValueAsString(object);
            addResponseHeader("Content-Type", "application/json");
            logger.log(Exchange.class, "JSON response body set successfully", SimpleLogger.Level.DEBUG);
        } catch (JsonProcessingException e) {
            logger.log(Exchange.class, "Failed to serialize JSON response: " + e.getMessage(), SimpleLogger.Level.ERROR);
            this.responseBodyContent = "{\"error\": \"Failed to serialize JSON response\"}";
            addResponseHeader("Content-Type", "application/json");
            this.responseStatusCode = 500;
        }
        return this;
    }

    public Exchange addResponseHeader(String name, String value) {
        logger.log(Exchange.class, "Adding response header: " + name + " = " + value, SimpleLogger.Level.DEBUG);
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
        String allowedMethods = allowed.toString();
        addResponseHeader("Allow", allowedMethods);
        logger.log(Exchange.class, "Allowed methods: " + allowedMethods, SimpleLogger.Level.DEBUG);
        return this;
    }

    public Exchange enableCors() {
        logger.log(Exchange.class, "Enabling CORS with default origin '*'", SimpleLogger.Level.DEBUG);
        return enableCors("*");
    }

    public Exchange enableCors(String allowedOrigin) {
        logger.log(Exchange.class, "Enabling CORS with origin: " + allowedOrigin, SimpleLogger.Level.DEBUG);
        addResponseHeader("Access-Control-Allow-Origin", allowedOrigin);
        addResponseHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, PATCH");
        addResponseHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
        addResponseHeader("Access-Control-Allow-Credentials", "true");
        return this;
    }

    public Exchange setJwtResponseToken(String token) {
        logger.log(Exchange.class, "Setting JWT response token: " + (token != null ? token.substring(0, Math.min(token.length(), 20)) + "..." : "null"), SimpleLogger.Level.DEBUG);
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
        logger.log(Exchange.class, "Getting cookies: " + cookies, SimpleLogger.Level.DEBUG);
        return cookies;
    }

    /**
     * Gets a specific cookie by name.
     */
    public String getCookie(String name) {
        Map<String, String> cookies = getCookies();
        String value = cookies.get(name);
        logger.log(Exchange.class, "Getting cookie '" + name + "': " + value, SimpleLogger.Level.DEBUG);
        return value;
    }

    /**
     * Gets a specific cookie by name with a default value.
     */
    public String getCookie(String name, String defaultValue) {
        String value = getCookie(name);
        String result = value != null ? value : defaultValue;
        logger.log(Exchange.class, "Getting cookie '" + name + "' with default: " + result, SimpleLogger.Level.DEBUG);
        return result;
    }

    /**
     * Checks if a specific cookie exists.
     */
    public boolean hasCookie(String name) {
        boolean has = getCookie(name) != null;
        logger.log(Exchange.class, "Checking if cookie '" + name + "' exists: " + has, SimpleLogger.Level.DEBUG);
        return has;
    }

    /**
     * Removes a cookie by setting its max age to 0.
     */
    public Exchange removeCookie(String name) {
        logger.log(Exchange.class, "Removing cookie: " + name, SimpleLogger.Level.DEBUG);
        addResponseHeader("Set-Cookie", name + "=; Max-Age=0; HttpOnly; SameSite=Strict");
        return this;
    }

    /**
     * Removes a cookie by setting its max age to 0 for a specific path.
     */
    public Exchange removeCookie(String name, String path) {
        logger.log(Exchange.class, "Removing cookie: " + name + " with path: " + path, SimpleLogger.Level.DEBUG);
        addResponseHeader("Set-Cookie", name + "=; Max-Age=0; Path=" + path + "; HttpOnly; SameSite=Strict");
        return this;
    }

    public Exchange addCookie(String name, String value) {
        logger.log(Exchange.class, "Adding cookie: " + name + " = " + value, SimpleLogger.Level.DEBUG);
        addResponseHeader("Set-Cookie", name + "=" + value + "; HttpOnly; Path=/; SameSite=Strict");
        return this;
    }

    public Exchange addCookie(String name, String value, int maxAgeSeconds) {
        logger.log(Exchange.class, "Adding cookie: " + name + " = " + value + ", maxAge: " + maxAgeSeconds + " seconds", SimpleLogger.Level.DEBUG);
        addResponseHeader("Set-Cookie", name + "=" + value + "; HttpOnly; Path=/; SameSite=Strict; Max-Age=" + maxAgeSeconds);
        return this;
    }

    public Exchange addCookie(String name, String value, int maxAgeSeconds, String path) {
        logger.log(Exchange.class, "Adding cookie: " + name + " = " + value + ", maxAge: " + maxAgeSeconds + " seconds, path: " + path, SimpleLogger.Level.DEBUG);
        addResponseHeader("Set-Cookie", name + "=" + value + "; HttpOnly; Path=" + path + "; SameSite=Strict; Max-Age=" + maxAgeSeconds);
        return this;
    }

    public Exchange setCacheControlMaxAge(long seconds) {
        logger.log(Exchange.class, "Setting Cache-Control max-age: " + seconds, SimpleLogger.Level.DEBUG);
        addResponseHeader("Cache-Control", "max-age=" + seconds);
        return this;
    }

    public Exchange disableCache() {
        logger.log(Exchange.class, "Disabling cache", SimpleLogger.Level.DEBUG);
        addResponseHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        addResponseHeader("Pragma", "no-cache");
        return this;
    }

    public Exchange setAttribute(String key, Object value) {
        logger.log(Exchange.class, "Setting attribute: " + key + " = " + value, SimpleLogger.Level.DEBUG);
        attributes.put(key, value);
        return this;
    }

    public Object getAttribute(String key) {
        Object value = attributes.get(key);
        logger.log(Exchange.class, "Getting attribute: " + key + " = " + value, SimpleLogger.Level.DEBUG);
        return value;
    }

    public <T> T getAttribute(String key, Class<T> clazz) {
        Object value = attributes.get(key);
        T result = clazz.isInstance(value) ? clazz.cast(value) : null;
        logger.log(Exchange.class, "Getting attribute: " + key + " as " + clazz.getName() + " = " + result, SimpleLogger.Level.DEBUG);
        return result;
    }

    public <T> T getAttribute(String key, Class<T> clazz, T defaultValue) {
        return this.getAttribute(key, clazz) == null ? defaultValue : getAttribute(key, clazz);
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

        // Apply all response requestHeaderMap
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

            logger.log(Exchange.class, "Sending response: " + responseStatusCode + " with " + responseBytes.length + " bytes. First 32 chars: " + preview, SimpleLogger.Level.INFO);
            httpExchange.sendResponseHeaders(responseStatusCode, responseBytes.length);
            try (OutputStream os = httpExchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
        responseAlreadySent = true;
        logger.log(Exchange.class, "Response sent successfully", SimpleLogger.Level.DEBUG);
    }

    public void sendResponse(String body) throws IOException {
        logger.log(Exchange.class, "Sending response with body: " + (body != null ? body.length() + " chars" : "null"), SimpleLogger.Level.DEBUG);
        this.responseBodyContent = body;
        sendResponse();
    }

    public void sendResponse(int statusCode, String body) throws IOException {
        logger.log(Exchange.class, "Sending response with status: " + statusCode + ", body: " + (body != null ? body.length() + " chars" : "null"), SimpleLogger.Level.DEBUG);
        this.responseStatusCode = statusCode;
        this.responseBodyContent = body;
        sendResponse();
    }

    public void sendJsonResponse(Object object) throws IOException {
        logger.log(Exchange.class, "Sending JSON response from object", SimpleLogger.Level.DEBUG);
        setResponseBodyAsJson(object);
        sendResponse();
    }

    public void sendJsonResponse(int statusCode, Object object) throws IOException {
        logger.log(Exchange.class, "Sending JSON response with status: " + statusCode + " from object", SimpleLogger.Level.DEBUG);
        this.responseStatusCode = statusCode;
        setResponseBodyAsJson(object);
        sendResponse();
    }

    public void sendErrorResponse(String errorMessage) throws IOException {
        logger.log(Exchange.class, "Sending error response (500): " + errorMessage, SimpleLogger.Level.ERROR);
        sendResponse(500, "{\"error\": \"" + errorMessage + "\"}");
    }

    public void sendErrorResponse(int statusCode, String errorMessage) throws IOException {
        logger.log(Exchange.class, "Sending error response (" + statusCode + "): " + errorMessage, SimpleLogger.Level.ERROR);
        sendResponse(statusCode, "{\"error\": \"" + errorMessage + "\"}");
    }

    public void sendNotFoundResponse() throws IOException {
        logger.log(Exchange.class, "Sending 404 Not Found response", SimpleLogger.Level.INFO);
        sendResponse(404, "{\"error\": \"Not Found\"}");
    }

    public void sendBadRequestResponse(String errorMessage) throws IOException {
        logger.log(Exchange.class, "Sending 400 Bad Request response: " + errorMessage, SimpleLogger.Level.WARN);
        sendResponse(400, "{\"error\": \"" + errorMessage + "\"}");
    }

    public void sendUnauthorizedResponse() throws IOException {
        logger.log(Exchange.class, "Sending 401 Unauthorized response", SimpleLogger.Level.WARN);
        sendResponse(401, "{\"error\": \"Unauthorized\"}");
    }

    public void sendForbiddenResponse() throws IOException {
        logger.log(Exchange.class, "Sending 403 Forbidden response", SimpleLogger.Level.WARN);
        sendResponse(403, "{\"error\": \"Forbidden\"}");
    }

    public void sendMethodNotAllowedResponse() throws IOException {
        logger.log(Exchange.class, "Sending 405 Method Not Allowed response", SimpleLogger.Level.WARN);
        sendResponse(405, "{\"error\": \"Method Not Allowed\"}");
    }

    public void sendMethodNotAllowedResponse(String... allowedMethods) throws IOException {
        logger.log(Exchange.class, "Sending 405 Method Not Allowed response, allowed methods: " + Arrays.toString(allowedMethods), SimpleLogger.Level.WARN);
        if (allowedMethods.length > 0) {
            allowMethods(allowedMethods);
        }
        sendMethodNotAllowedResponse();
    }

    public void sendCreatedResponse() throws IOException {
        logger.log(Exchange.class, "Sending 201 Created response", SimpleLogger.Level.INFO);
        sendResponse(201, "{\"message\": \"Resource created successfully\"}");
    }

    public void sendCreatedResponse(String resourceLocation) throws IOException {
        logger.log(Exchange.class, "Sending 201 Created response with location: " + resourceLocation, SimpleLogger.Level.INFO);
        addResponseHeader("Location", resourceLocation);
        sendResponse(201, "{\"message\": \"Resource created successfully\", \"location\": \"" + resourceLocation + "\"}");
    }

    public void sendNoContentResponse() throws IOException {
        logger.log(Exchange.class, "Sending 204 No Content response", SimpleLogger.Level.INFO);
        sendResponse(204, "");
    }

    public Exchange formatHTML() {
        logger.log(Exchange.class, "Formatting response as HTML", SimpleLogger.Level.DEBUG);
        addResponseHeader("Content-Type", "text/html; charset=UTF-8");
        return this;
    }

    public Exchange formatJSON() {
        logger.log(Exchange.class, "Formatting response as JSON", SimpleLogger.Level.DEBUG);
        addResponseHeader("Content-Type", "application/json; charset=UTF-8");
        return this;
    }

    public Exchange formatXML() {
        logger.log(Exchange.class, "Formatting response as XML", SimpleLogger.Level.DEBUG);
        addResponseHeader("Content-Type", "text/xml; charset=UTF-8");
        return this;
    }

    public Exchange formatPlainText() {
        logger.log(Exchange.class, "Formatting response as plain text", SimpleLogger.Level.DEBUG);
        addResponseHeader("Content-Type", "text/plain; charset=UTF-8");
        return this;
    }

    // ===== REQUEST INSPECTION HELPERS =====

    public boolean isHttpMethod(String methodName) {
        boolean is = this.method.equalsIgnoreCase(methodName);
        logger.log(Exchange.class, "Checking if method is " + methodName + ": " + is, SimpleLogger.Level.DEBUG);
        return is;
    }

    public boolean isGetRequest() {
        boolean is = isHttpMethod("GET");
        logger.log(Exchange.class, "Checking if GET request: " + is, SimpleLogger.Level.DEBUG);
        return is;
    }

    public boolean isPostRequest() {
        boolean is = isHttpMethod("POST");
        logger.log(Exchange.class, "Checking if POST request: " + is, SimpleLogger.Level.DEBUG);
        return is;
    }

    public boolean isPutRequest() {
        boolean is = isHttpMethod("PUT");
        logger.log(Exchange.class, "Checking if PUT request: " + is, SimpleLogger.Level.DEBUG);
        return is;
    }

    public boolean isDeleteRequest() {
        boolean is = isHttpMethod("DELETE");
        logger.log(Exchange.class, "Checking if DELETE request: " + is, SimpleLogger.Level.DEBUG);
        return is;
    }

    public boolean isPatchRequest() {
        boolean is = isHttpMethod("PATCH");
        logger.log(Exchange.class, "Checking if PATCH request: " + is, SimpleLogger.Level.DEBUG);
        return is;
    }

    public boolean isOptionsRequest() {
        boolean is = isHttpMethod("OPTIONS");
        logger.log(Exchange.class, "Checking if OPTIONS request: " + is, SimpleLogger.Level.DEBUG);
        return is;
    }

    public boolean isHeadRequest() {
        boolean is = isHttpMethod("HEAD");
        logger.log(Exchange.class, "Checking if HEAD request: " + is, SimpleLogger.Level.DEBUG);
        return is;
    }

    public boolean isJsonRequest() {
        boolean is = isJsonRequestBody() && !body.isEmpty();
        logger.log(Exchange.class, "Checking if JSON request: " + is, SimpleLogger.Level.DEBUG);
        return is;
    }

    public boolean isSecureConnection() {
        boolean is = "https".equalsIgnoreCase(requestUri.getScheme());
        logger.log(Exchange.class, "Checking if secure connection: " + is, SimpleLogger.Level.DEBUG);
        return is;
    }

    public String getUserAgent() {
        String userAgent = getRequestHeaderCaseInsensitive("User-Agent");
        logger.log(Exchange.class, "Getting User-Agent: " + userAgent, SimpleLogger.Level.DEBUG);
        return userAgent;
    }

    public String getReferer() {
        String referer = getRequestHeaderCaseInsensitive("Referer");
        logger.log(Exchange.class, "Getting Referer: " + referer, SimpleLogger.Level.DEBUG);
        return referer;
    }

    public String getOrigin() {
        String origin = getRequestHeaderCaseInsensitive("Origin");
        logger.log(Exchange.class, "Getting Origin: " + origin, SimpleLogger.Level.DEBUG);
        return origin;
    }

    public String getHost() {
        String host = getRequestHeaderCaseInsensitive("Host");
        logger.log(Exchange.class, "Getting Host: " + host, SimpleLogger.Level.DEBUG);
        return host;
    }

    public String getContentType() {
        String contentType = getRequestHeaderCaseInsensitive("Content-Type");
        logger.log(Exchange.class, "Getting Content-Type: " + contentType, SimpleLogger.Level.DEBUG);
        return contentType;
    }

    public int getContentLength() {
        String length = getRequestHeaderCaseInsensitive("Content-Length");
        int result = 0;
        try {
            result = length != null ? Integer.parseInt(length) : 0;
        } catch (NumberFormatException e) {
            logger.log(Exchange.class, "Failed to parse Content-Length: " + length, SimpleLogger.Level.WARN);
        }
        logger.log(Exchange.class, "Getting Content-Length: " + result, SimpleLogger.Level.DEBUG);
        return result;
    }

    public String getClientIpAddress() {
        String forwardedFor = getRequestHeaderCaseInsensitive("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            String ip = forwardedFor.split(",")[0].trim();
            logger.log(Exchange.class, "Getting client IP from X-Forwarded-For: " + ip, SimpleLogger.Level.DEBUG);
            return ip;
        }
        String realIp = getRequestHeaderCaseInsensitive("X-Real-IP");
        if (realIp != null && !realIp.isEmpty()) {
            logger.log(Exchange.class, "Getting client IP from X-Real-IP: " + realIp, SimpleLogger.Level.DEBUG);
            return realIp;
        }
        String ip = remoteAddress != null ? remoteAddress.getAddress().getHostAddress() : null;
        logger.log(Exchange.class, "Getting client IP from remote address: " + ip, SimpleLogger.Level.DEBUG);
        return ip;
    }

    public boolean isAjaxRequest() {
        String requestedWith = getRequestHeaderCaseInsensitive("X-Requested-With");
        boolean isAjax = "XMLHttpRequest".equals(requestedWith);
        logger.log(Exchange.class, "Checking if AJAX request: " + isAjax, SimpleLogger.Level.DEBUG);
        return isAjax;
    }

    public boolean acceptsJsonResponse() {
        String accept = getRequestHeaderCaseInsensitive("Accept");
        boolean accepts = accept != null && accept.contains("application/json");
        logger.log(Exchange.class, "Checking if accepts JSON response: " + accepts, SimpleLogger.Level.DEBUG);
        return accepts;
    }

    // ===== PRIVATE HELPERS =====

    private Map<String, String> parseQueryString(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null || query.isEmpty()) {
            logger.log(Exchange.class, "Query string is null or empty", SimpleLogger.Level.DEBUG);
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
        logger.log(Exchange.class, "Parsed query string: " + params, SimpleLogger.Level.DEBUG);
        return params;
    }

    // ===== FORM URLENCODED HELPERS =====

    /**
     * Checks if the request is application/x-www-form-urlencoded.
     */
    public boolean isFormUrlEncoded() {
        String contentType = getRequestHeaderCaseInsensitive("Content-Type");
        boolean isForm = contentType != null && contentType.startsWith("application/x-www-form-urlencoded");
        logger.log(Exchange.class, "Checking if form URL-encoded: " + isForm, SimpleLogger.Level.DEBUG);
        return isForm;
    }

    /**
     * Parses application/x-www-form-urlencoded body into a map.
     */
    public Map<String, String> parseFormUrlEncoded() throws IOException {
        logger.log(Exchange.class, "Parsing form URL-encoded body", SimpleLogger.Level.DEBUG);
        Map<String, String> params = new HashMap<>();

        if (!isFormUrlEncoded()) {
            logger.log(Exchange.class, "Request is not form URL-encoded, returning empty map", SimpleLogger.Level.DEBUG);
            return params;
        }

        String body = getRequestBody();
        if (body == null || body.isEmpty()) {
            logger.log(Exchange.class, "Request body is null or empty, returning empty map", SimpleLogger.Level.DEBUG);
            return params;
        }

        Map<String, String> result = parseQueryString(body);
        logger.log(Exchange.class, "Parsed form URL-encoded body: " + result, SimpleLogger.Level.DEBUG);
        return result;
    }

    /**
     * Parses application/x-www-form-urlencoded body into a map with a default.
     */
    public Map<String, String> parseFormUrlEncoded(Map<String, String> defaultMap) {
        logger.log(Exchange.class, "Parsing form URL-encoded body with default map", SimpleLogger.Level.DEBUG);
        try {
            Map<String, String> result = parseFormUrlEncoded();
            if (result.isEmpty()) {
                logger.log(Exchange.class, "Parsed result is empty, using default map: " + defaultMap, SimpleLogger.Level.DEBUG);
                return defaultMap;
            }
            return result;
        } catch (IOException e) {
            logger.log(Exchange.class, "Failed to parse form URL-encoded, using default map: " + e.getMessage(), SimpleLogger.Level.WARN);
            return defaultMap;
        }
    }

    /**
     * Gets a form field value from application/x-www-form-urlencoded body.
     */
    public String getFormFieldFromUrlEncoded(String name) throws IOException {
        logger.log(Exchange.class, "Getting form field from URL-encoded body: " + name, SimpleLogger.Level.DEBUG);
        Map<String, String> params = parseFormUrlEncoded();
        String value = params.get(name);
        logger.log(Exchange.class, "Form field '" + name + "' = " + value, SimpleLogger.Level.DEBUG);
        return value;
    }

    /**
     * Gets a form field value from application/x-www-form-urlencoded body with a default.
     */
    public String getFormFieldFromUrlEncoded(String name, String defaultValue) {
        logger.log(Exchange.class, "Getting form field from URL-encoded body: " + name + " with default", SimpleLogger.Level.DEBUG);
        try {
            String value = getFormFieldFromUrlEncoded(name);
            String result = value != null ? value : defaultValue;
            logger.log(Exchange.class, "Form field '" + name + "' = " + result, SimpleLogger.Level.DEBUG);
            return result;
        } catch (IOException e) {
            logger.log(Exchange.class, "Failed to get form field '" + name + "', returning default: " + defaultValue, SimpleLogger.Level.WARN);
            return defaultValue;
        }
    }

    /**
     * Gets multiple form fields from application/x-www-form-urlencoded body.
     */
    public Map<String, String> getFormFieldsFromUrlEncoded(String... names) throws IOException {
        logger.log(Exchange.class, "Getting form fields from URL-encoded body: " + Arrays.toString(names), SimpleLogger.Level.DEBUG);
        Map<String, String> result = new HashMap<>();
        Map<String, String> params = parseFormUrlEncoded();

        for (String name : names) {
            result.put(name, params.get(name));
        }
        logger.log(Exchange.class, "Retrieved form fields: " + result, SimpleLogger.Level.DEBUG);
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

        public String getExtension() {
            String ext = "";
            int dotIndex = this.filename.lastIndexOf('.');
            if (dotIndex > 0) {
                ext = this.filename.substring(dotIndex);
            }
            return ext;
        }

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
        logger.log(Exchange.class, "Closing HttpExchange", SimpleLogger.Level.DEBUG);
        httpExchange.close();
    }

    public static void setFileUploadLimit(int fileUploadLimit) {
        MAX_FILE_SIZE = fileUploadLimit;
    }
}