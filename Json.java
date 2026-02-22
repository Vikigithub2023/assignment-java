import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private Json() {
    }

    public static Object parse(String json) {
        if (json == null) {
            throw new IllegalArgumentException("json cannot be null");
        }
        Parser parser = new Parser(json);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.isAtEnd()) {
            throw new IllegalArgumentException("Trailing data at position " + parser.pos);
        }
        return value;
    }

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
            return;
        }
        if (value instanceof String) {
            sb.append('"');
            writeEscapedString(sb, (String) value);
            sb.append('"');
            return;
        }
        if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
            return;
        }
        if (value instanceof Map<?, ?>) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                sb.append('"');
                writeEscapedString(sb, e.getKey());
                sb.append('"');
                sb.append(':');
                writeValue(sb, e.getValue());
            }
            sb.append('}');
            return;
        }
        if (value instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) value;
            sb.append('[');
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                writeValue(sb, list.get(i));
            }
            sb.append(']');
            return;
        }

        throw new IllegalArgumentException("Unsupported type: " + value.getClass().getName());
    }

    private static void writeEscapedString(StringBuilder sb, String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
    }

    private static final class Parser {
        private final String s;
        private int pos;

        private Parser(String s) {
            this.s = s;
        }

        private boolean isAtEnd() {
            return pos >= s.length();
        }

        private void skipWhitespace() {
            while (!isAtEnd()) {
                char c = s.charAt(pos);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                    pos++;
                } else {
                    return;
                }
            }
        }

        private Object parseValue() {
            skipWhitespace();
            if (isAtEnd()) {
                throw new IllegalArgumentException("Unexpected end of input");
            }

            char c = s.charAt(pos);
            if (c == '{') {
                return parseObject();
            }
            if (c == '[') {
                return parseArray();
            }
            if (c == '"') {
                return parseString();
            }
            if (c == 't' || c == 'f') {
                return parseBoolean();
            }
            if (c == 'n') {
                return parseNull();
            }
            if (c == '-' || (c >= '0' && c <= '9')) {
                return parseNumber();
            }

            throw new IllegalArgumentException("Unexpected character '" + c + "' at position " + pos);
        }

        private Map<String, Object> parseObject() {
            expect('{');
            skipWhitespace();
            Map<String, Object> map = new HashMap<>();
            if (tryConsume('}')) {
                return map;
            }

            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                if (tryConsume('}')) {
                    return map;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            skipWhitespace();
            List<Object> list = new ArrayList<>();
            if (tryConsume(']')) {
                return list;
            }

            while (true) {
                Object value = parseValue();
                list.add(value);
                skipWhitespace();
                if (tryConsume(']')) {
                    return list;
                }
                expect(',');
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (!isAtEnd()) {
                char c = s.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (isAtEnd()) {
                    throw new IllegalArgumentException("Unterminated escape at position " + pos);
                }
                char e = s.charAt(pos++);
                switch (e) {
                    case '"':
                        sb.append('"');
                        break;
                    case '\\':
                        sb.append('\\');
                        break;
                    case '/':
                        sb.append('/');
                        break;
                    case 'b':
                        sb.append('\b');
                        break;
                    case 'f':
                        sb.append('\f');
                        break;
                    case 'n':
                        sb.append('\n');
                        break;
                    case 'r':
                        sb.append('\r');
                        break;
                    case 't':
                        sb.append('\t');
                        break;
                    case 'u':
                        sb.append(parseUnicodeEscape());
                        break;
                    default:
                        throw new IllegalArgumentException("Invalid escape '\\" + e + "' at position " + (pos - 1));
                }
            }
            throw new IllegalArgumentException("Unterminated string");
        }

        private char parseUnicodeEscape() {
            if (pos + 4 > s.length()) {
                throw new IllegalArgumentException("Invalid unicode escape at position " + pos);
            }
            int codePoint = 0;
            for (int i = 0; i < 4; i++) {
                char c = s.charAt(pos++);
                codePoint <<= 4;
                if (c >= '0' && c <= '9') {
                    codePoint |= (c - '0');
                } else if (c >= 'a' && c <= 'f') {
                    codePoint |= (c - 'a' + 10);
                } else if (c >= 'A' && c <= 'F') {
                    codePoint |= (c - 'A' + 10);
                } else {
                    throw new IllegalArgumentException("Invalid hex digit '" + c + "' at position " + (pos - 1));
                }
            }
            return (char) codePoint;
        }

        private Boolean parseBoolean() {
            if (s.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Invalid boolean at position " + pos);
        }

        private Object parseNull() {
            if (s.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Invalid null at position " + pos);
        }

        private Number parseNumber() {
            int start = pos;
            if (s.charAt(pos) == '-') {
                pos++;
            }
            while (!isAtEnd()) {
                char c = s.charAt(pos);
                if (c >= '0' && c <= '9') {
                    pos++;
                } else {
                    break;
                }
            }
            boolean isDouble = false;
            if (!isAtEnd() && s.charAt(pos) == '.') {
                isDouble = true;
                pos++;
                while (!isAtEnd()) {
                    char c = s.charAt(pos);
                    if (c >= '0' && c <= '9') {
                        pos++;
                    } else {
                        break;
                    }
                }
            }
            if (!isAtEnd()) {
                char c = s.charAt(pos);
                if (c == 'e' || c == 'E') {
                    isDouble = true;
                    pos++;
                    if (!isAtEnd()) {
                        char sign = s.charAt(pos);
                        if (sign == '+' || sign == '-') {
                            pos++;
                        }
                    }
                    while (!isAtEnd()) {
                        char d = s.charAt(pos);
                        if (d >= '0' && d <= '9') {
                            pos++;
                        } else {
                            break;
                        }
                    }
                }
            }

            String num = s.substring(start, pos);
            try {
                if (isDouble) {
                    return Double.parseDouble(num);
                }
                long v = Long.parseLong(num);
                if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) {
                    return (int) v;
                }
                return v;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid number '" + num + "' at position " + start, e);
            }
        }

        private void expect(char c) {
            if (isAtEnd() || s.charAt(pos) != c) {
                throw new IllegalArgumentException("Expected '" + c + "' at position " + pos);
            }
            pos++;
        }

        private boolean tryConsume(char c) {
            if (!isAtEnd() && s.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }
    }
}

