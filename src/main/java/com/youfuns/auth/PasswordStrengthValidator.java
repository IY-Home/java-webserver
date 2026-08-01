package com.youfuns.auth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PasswordStrengthValidator {
    private static final int MIN_LENGTH = 6;
    private static final int MAX_LENGTH = 128;
    private static final boolean REQUIRES_UPPERCASE = true;
    private static final boolean REQUIRES_LOWERCASE = true;
    private static final boolean REQUIRES_NUMBERS = true;
    private static final boolean REQUIRES_SYMBOLS = true;

    private static final List<String> commonPasswords = Arrays.asList(
                    // Full common passwords
                    "123456",
                    "password",
                    "123456789",
                    "12345678",
                    "12345",
                    "1234567",
                    "qwerty",
                    "abc123",
                    "password123",
                    "admin",
                    "letmein",
                    "welcome",
                    "monkey",
                    "dragon",
                    "master",
                    "sunshine",
                    "princess",
                    "iloveyou",
                    "trustno1",
                    "admin123",
                    "password1",
                    "qwerty123",
                    "1234567890",
                    "123123"
    );
    private static final List<String> commonFragments = Arrays.asList(
                    // Common fragments for contains checking
                    "123",
                    "qwert",
                    "asdfg",
                    "zxcvb",
                    "pass",
                    "admin",
                    "abc",
                    "love",
                    "sun",
                    "dragon",
                    "master",
                    "welcome",
                    "monkey",
                    "princess",
                    "trust",
                    "ilove",
                    "letme",
                    "password",
                    "12345",
                    "123456",
                    "qwerty",
                    "admin",
                    "user",
                    "hello"
    );

    private static boolean containsCommonPassword(String password) {
        if (commonPasswords.contains(password)) return true;
        if (commonPasswords.contains(password.toLowerCase())) return true;
        List<String> passwordsContained = new ArrayList<>();
        for (String commonPassword : commonFragments) {
            if (password.contains(commonPassword)) passwordsContained.add(commonPassword);
        }
        int total_contained_password_length = 0;
        for (String commonPassword : passwordsContained) {
            if (password.length() < 2*commonPassword.length()) return true;
            total_contained_password_length += commonPassword.length();
        }
        return total_contained_password_length + 4 > password.length();
    }

    private static boolean isOfLength(String password) {
        return password.length() >= MIN_LENGTH && password.length() <= MAX_LENGTH;
    }
    private static boolean containsCharacters(String password) {
        boolean hasUppercase = false;
        boolean hasLowercase = false;
        boolean hasNumber = false;
        boolean hasSymbol = false;

        for (char c : password.toCharArray()) {
            if (Character.isUpperCase(c)) {
                hasUppercase = true;
            } else if (Character.isLowerCase(c)) {
                hasLowercase = true;
            } else if (Character.isDigit(c)) {
                hasNumber = true;
            } else if (!Character.isLetterOrDigit(c) && !Character.isWhitespace(c)) {
                // Symbol: any non-letter, non-digit, non-whitespace character
                hasSymbol = true;
            }

            // Early exit if all required conditions are met
            if (hasUppercase && hasLowercase && hasNumber && hasSymbol) {
                return true;
            }
        }

        // Check each requirement based on the boolean flags
        if (REQUIRES_UPPERCASE && !hasUppercase) return false;
        if (REQUIRES_LOWERCASE && !hasLowercase) return false;
        if (REQUIRES_NUMBERS && !hasNumber) return false;
        if (REQUIRES_SYMBOLS && !hasSymbol) return false;

        return true;
    }

    private static boolean hasRepetitivePattern(String password) {
        int totalRepeatedLength = 0;
        // Check for 3+ repeated characters in a row (e.g., "aaa", "111")
        for (int i = 0; i < password.length() - 2; i++) {
            if (password.charAt(i) == password.charAt(i + 1) &&
                    password.charAt(i) == password.charAt(i + 2)) {

                // Find the length of the repeated sequence
                char repeatedChar = password.charAt(i);
                int sequenceStart = i;
                int sequenceEnd = i + 2;

                // Expand to find full sequence length
                while (sequenceEnd + 1 < password.length() &&
                        password.charAt(sequenceEnd + 1) == repeatedChar) {
                    sequenceEnd++;
                }

                int sequenceLength = sequenceEnd - sequenceStart + 1;

                totalRepeatedLength += sequenceLength;

                // Skip past this sequence to avoid re-checking
                i = sequenceEnd;
            }
        }

        if (totalRepeatedLength * 2 > password.length()) {
            return true;
        }

        return false;
    }

    public static ResultReturn validatePasswordWithMessage(String password) {
        if (password == null || password.isEmpty()) {
            return new ResultReturn(ResultReturn.Result.FAILURE, "Password is null or empty.");
        }
        if (!isOfLength(password)) {
            return new ResultReturn(ResultReturn.Result.FAILURE, "Password is too short or too long. (6-128 chars)");
        }
        if (!containsCharacters(password)) {
            return new ResultReturn(ResultReturn.Result.FAILURE, "Password does not contain letters, numbers, or symbols.");
        }
        if (hasRepetitivePattern(password)) {
            return new ResultReturn(ResultReturn.Result.FAILURE, "Password contains too many repeated characters.");
        }
        if (containsCommonPassword(password)) {
            return new ResultReturn(ResultReturn.Result.FAILURE, "Password contains common passwords.");
        }
        return new ResultReturn(ResultReturn.Result.SUCCESS, "Password is valid.");
    }
}
