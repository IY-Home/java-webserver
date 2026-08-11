package com.youfuns.webserver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TemplateEngine {
    private final String template;
    private final Map<String, String> attributes;
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{(\\w+)\\}\\}");

    public TemplateEngine(String template) {
        this.template = template;
        this.attributes = new HashMap<>();
        extractAttributes(template);
    }

    /**
     * Extracts all {{attribute}} placeholders from the template
     * and initializes them with empty strings in the attributes map.
     */
    private void extractAttributes(String template) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        while (matcher.find()) {
            String attributeName = matcher.group(1);
            attributes.putIfAbsent(attributeName, "");
        }
    }

    /**
     * Gets the current template string with all replacements applied.
     */
    public String getTemplate() {
        String result = template;
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() != null ? entry.getValue() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }

    /**
     * Gets all attribute names found in the template.
     */
    public Set<String> getAttributeNames() {
        return Collections.unmodifiableSet(attributes.keySet());
    }

    /**
     * Gets the current value of an attribute.
     */
    public String getAttribute(String name) {
        return attributes.get(name);
    }

    /**
     * Replaces the value of an attribute.
     * If duplicate attributes exist, all are replaced simultaneously.
     */
    public TemplateEngine replace(String attribute, String value) {
        if (attributes.containsKey(attribute)) {
            attributes.put(attribute, value != null ? value : "");
        }
        return this;
    }

    /**
     * Replaces multiple attributes at once.
     */
    public TemplateEngine replaceAll(Map<String, String> replacements) {
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            replace(entry.getKey(), entry.getValue());
        }
        return this;
    }

    /**
     * Replaces attributes from an object using its fields.
     * Uses reflection to get field values.
     */
    public TemplateEngine replaceFromObject(Object obj) {
        try {
            for (String attribute : attributes.keySet()) {
                try {
                    java.lang.reflect.Field field = obj.getClass().getDeclaredField(attribute);
                    field.setAccessible(true);
                    Object value = field.get(obj);
                    replace(attribute, value != null ? value.toString() : "");
                } catch (NoSuchFieldException e) {
                    // Field doesn't exist, skip
                }
            }
        } catch (IllegalAccessException e) {
            // Ignore
        }
        return this;
    }

    /**
     * Replaces attributes from a Map.
     */
    public TemplateEngine replaceFromMap(Map<String, Object> data) {
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            Object value = entry.getValue();
            replace(entry.getKey(), value != null ? value.toString() : "");
        }
        return this;
    }

    /**
     * Checks if a specific attribute exists in the template.
     */
    public boolean hasAttribute(String name) {
        return attributes.containsKey(name);
    }

    /**
     * Checks if any attributes were found in the template.
     */
    public boolean hasAttributes() {
        return !attributes.isEmpty();
    }

    /**
     * Returns the number of unique attributes found in the template.
     */
    public int attributeCount() {
        return attributes.size();
    }

    /**
     * Resets all attribute values to empty strings.
     */
    public TemplateEngine reset() {
        for (String key : attributes.keySet()) {
            attributes.put(key, "");
        }
        return this;
    }

    /**
     * Creates a new TemplateEngine from a file.
     */
    public static TemplateEngine fromFile(String filePath) throws IOException {
        String content = new String(java.nio.file.Files.readAllBytes(
                java.nio.file.Paths.get(filePath)),
                java.nio.charset.StandardCharsets.UTF_8
        );
        return new TemplateEngine(content);
    }

    public static TemplateEngine fromResource(String path) throws IOException {
        try (InputStream is = TemplateEngine.class.getClassLoader()
                .getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Resource not found: " + path);
            }
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return new TemplateEngine(content);
        }
    }

    /**
     * Creates a new TemplateEngine from a String.
     */
    public static TemplateEngine fromString(String template) {
        return new TemplateEngine(template);
    }

    @Override
    public String toString() {
        return getTemplate();
    }
}