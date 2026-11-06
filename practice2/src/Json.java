import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private final String text;
    private int index;

    private Json(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        Json parser = new Json(text);
        Object value = parser.value();
        parser.space();
        if (parser.index != text.length()) throw parser.error();
        return value;
    }

    public static Map<String, Object> object(Object value) {
        if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("JSON: expected object");
        Map<String, Object> result = new LinkedHashMap<>();
        for (var entry : map.entrySet()) result.put(string(entry.getKey()), entry.getValue());
        return result;
    }

    public static List<?> array(Object value) {
        if (!(value instanceof List<?> list)) throw new IllegalArgumentException("JSON: expected array");
        return list;
    }

    public static String string(Object value) {
        if (!(value instanceof String result)) throw new IllegalArgumentException("JSON: expected string");
        return result;
    }

    private Object value() {
        space();
        if (index == text.length()) throw error();
        return switch (text.charAt(index)) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", true);
            case 'f' -> literal("false", false);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object() {
        index++;
        Map<String, Object> result = new LinkedHashMap<>();
        if (take('}')) return result;
        do {
            space();
            if (index == text.length() || text.charAt(index) != '"') throw error();
            String key = string();
            require(':');
            if (result.containsKey(key)) throw error();
            result.put(key, value());
        } while (take(','));
        require('}');
        return result;
    }

    private List<Object> array() {
        index++;
        List<Object> result = new ArrayList<>();
        if (take(']')) return result;
        do {
            result.add(value());
        } while (take(','));
        require(']');
        return result;
    }

    private String string() {
        index++;
        StringBuilder result = new StringBuilder();
        while (index < text.length()) {
            char c = text.charAt(index++);
            if (c == '"') return result.toString();
            if (c < 32) throw error();
            if (c == '\\') {
                if (index == text.length()) throw error();
                c = text.charAt(index++);
                switch (c) {
                    case '"', '\\', '/' -> result.append(c);
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> {
                        if (index + 4 > text.length()) throw error();
                        String hex = text.substring(index, index + 4);
                        if (!hex.matches("[0-9a-fA-F]{4}")) throw error();
                        result.append((char) Integer.parseInt(hex, 16));
                        index += 4;
                    }
                    default -> throw error();
                }
            } else {
                result.append(c);
            }
        }
        throw error();
    }

    private Object number() {
        var matcher = java.util.regex.Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?")
                .matcher(text).region(index, text.length());
        if (!matcher.lookingAt()) throw error();
        index = matcher.end();
        return new BigDecimal(matcher.group());
    }

    private Object literal(String token, Object value) {
        if (!text.startsWith(token, index)) throw error();
        index += token.length();
        return value;
    }

    private boolean take(char c) {
        space();
        if (index < text.length() && text.charAt(index) == c) {
            index++;
            return true;
        }
        return false;
    }

    private void require(char c) {
        if (!take(c)) throw error();
    }

    private void space() {
        while (index < text.length() && " \r\n\t".indexOf(text.charAt(index)) >= 0) index++;
    }

    private IllegalArgumentException error() {
        return new IllegalArgumentException("invalid JSON at " + index);
    }
}
