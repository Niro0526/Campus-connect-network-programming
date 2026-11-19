package com.campusconnect.client.tcp;

import java.util.LinkedHashMap;
import java.util.Map;

/** Minimal JSON object framing for the fixed, string-valued chat protocol. */
final class JsonLine {
    private JsonLine() {
    }

    static String write(Map<String, String> fields) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            if (!first) json.append(',');
            first = false;
            json.append('"').append(escape(entry.getKey())).append("\":\"")
                    .append(escape(entry.getValue())).append('"');
        }
        return json.append('}').toString();
    }

    static Map<String, String> parse(String json) {
        if (json == null || json.isBlank()) throw new IllegalArgumentException("Empty JSON message");
        int[] index = {0};
        skipWhitespace(json, index);
        expect(json, index, '{');
        Map<String, String> fields = new LinkedHashMap<>();
        skipWhitespace(json, index);
        if (peek(json, index) == '}') {
            index[0]++;
        } else {
            while (true) {
                String key = readString(json, index);
                skipWhitespace(json, index);
                expect(json, index, ':');
                skipWhitespace(json, index);
                String value = readString(json, index);
                if (fields.put(key, value) != null) throw new IllegalArgumentException("Duplicate field: " + key);
                skipWhitespace(json, index);
                char delimiter = peek(json, index);
                index[0]++;
                if (delimiter == '}') break;
                if (delimiter != ',') throw new IllegalArgumentException("Expected ',' or '}'");
                skipWhitespace(json, index);
            }
        }
        skipWhitespace(json, index);
        if (index[0] != json.length()) throw new IllegalArgumentException("Trailing JSON content");
        return fields;
    }

    private static String readString(String json, int[] index) {
        expect(json, index, '"');
        StringBuilder result = new StringBuilder();
        while (index[0] < json.length()) {
            char character = json.charAt(index[0]++);
            if (character == '"') return result.toString();
            if (character != '\\') {
                if (character < 0x20) throw new IllegalArgumentException("Unescaped control character");
                result.append(character);
                continue;
            }
            if (index[0] >= json.length()) throw new IllegalArgumentException("Incomplete escape sequence");
            char escaped = json.charAt(index[0]++);
            switch (escaped) {
                case '"', '\\', '/' -> result.append(escaped);
                case 'b' -> result.append('\b');
                case 'f' -> result.append('\f');
                case 'n' -> result.append('\n');
                case 'r' -> result.append('\r');
                case 't' -> result.append('\t');
                case 'u' -> result.append(readUnicode(json, index));
                default -> throw new IllegalArgumentException("Invalid escape sequence");
            }
        }
        throw new IllegalArgumentException("Unterminated JSON string");
    }

    private static char readUnicode(String json, int[] index) {
        if (index[0] + 4 > json.length()) throw new IllegalArgumentException("Incomplete Unicode escape");
        try {
            char value = (char) Integer.parseInt(json.substring(index[0], index[0] + 4), 16);
            index[0] += 4;
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid Unicode escape", exception);
        }
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) escaped.append(String.format("\\u%04x", (int) character));
                    else escaped.append(character);
                }
            }
        }
        return escaped.toString();
    }

    private static void expect(String json, int[] index, char expected) {
        if (index[0] >= json.length() || json.charAt(index[0]++) != expected) {
            throw new IllegalArgumentException("Expected '" + expected + "'");
        }
    }

    private static char peek(String json, int[] index) {
        if (index[0] >= json.length()) throw new IllegalArgumentException("Unexpected end of JSON");
        return json.charAt(index[0]);
    }

    private static void skipWhitespace(String json, int[] index) {
        while (index[0] < json.length() && Character.isWhitespace(json.charAt(index[0]))) index[0]++;
    }
}
