package com.youfuns.webserver;

import com.youfuns.logger.LoggerManager;

import java.util.ArrayList;
import java.util.List;

public class TemplateMatcher {
    private char placeholder;

    public TemplateMatcher(char placeholder) {
        this.placeholder = placeholder;
    }

    public String[] extractValues(String template, String input) {
        LoggerManager.quickLog(this, template + " " + input);
        // If template has no placeholder, return empty array
        if (!template.contains(String.valueOf(placeholder))) {
            return new String[0];
        }

        // Parse the template into literal parts and placeholders
        List<String> literalParts = new ArrayList<>();
        List<Integer> placeholderPositions = new ArrayList<>();

        int i = 0;
        StringBuilder currentLiteral = new StringBuilder();
        boolean escaped = false;

        while (i < template.length()) {
            char c = template.charAt(i);

            if (escaped) {
                // Previous char was backslash
                if (c == placeholder) {
                    // Escaped $ - treat as literal
                    currentLiteral.append(placeholder);
                } else {
                    currentLiteral.append('\\');
                    currentLiteral.append(c);
                }
                escaped = false;
                i++;
                continue;
            }

            if (c == '\\') {
                escaped = true;
                i++;
                continue;
            }

            if (c == placeholder) {
                // Found a placeholder
                if (currentLiteral.length() > 0) {
                    literalParts.add(currentLiteral.toString());
                    currentLiteral = new StringBuilder();
                }

                // Skip multiple consecutive $s (treat as one)
                while (i < template.length() && template.charAt(i) == placeholder) {
                    i++;
                }

                placeholderPositions.add(literalParts.size());
                literalParts.add(""); // Placeholder will be replaced later
                continue;
            }

            currentLiteral.append(c);
            i++;
        }

        if (escaped) {
            // Trailing backslash
            currentLiteral.append('\\');
        }

        if (currentLiteral.length() > 0) {
            literalParts.add(currentLiteral.toString());
        }

        // Now try to match the input against the template
        List<String> results = new ArrayList<>();
        int inputPos = 0;

        for (int j = 0; j < literalParts.size(); j++) {
            String literal = literalParts.get(j);

            if (placeholderPositions.contains(j)) {
                // This is a placeholder position
                // Look ahead to find the next literal
                String nextLiteral = null;
                for (int k = j + 1; k < literalParts.size(); k++) {
                    if (!placeholderPositions.contains(k)) {
                        nextLiteral = literalParts.get(k);
                        break;
                    }
                }

                if (nextLiteral == null) {
                    // Last placeholder - match everything to the end
                    if (inputPos <= input.length()) {
                        results.add(input.substring(inputPos));
                        inputPos = input.length();
                    } else {
                        return new String[0];
                    }
                } else {
                    // Match until the next literal
                    int nextLiteralPos = input.indexOf(nextLiteral, inputPos);
                    if (nextLiteralPos == -1) {
                        return new String[0]; // Next literal not found
                    }
                    results.add(input.substring(inputPos, nextLiteralPos));
                    inputPos = nextLiteralPos;
                }
            } else {
                // This is a literal - must match exactly
                if (!input.startsWith(literal, inputPos)) {
                    return new String[0];
                }
                inputPos += literal.length();
            }
        }

        // If there's any remaining input, the match fails
        if (inputPos != input.length()) {
            return new String[0];
        }

        return results.toArray(new String[0]);
    }


}