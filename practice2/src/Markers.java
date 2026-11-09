import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class Markers {
    private static final String WILDCARD = ".*";
    private static final Pattern TOKEN = Pattern.compile(
            "\\s*('[^']*'|\"[^\"]*\"|===|~=|==|!=|<=|>=|<|>|\\(|\\)|[A-Za-z_][A-Za-z_0-9]*)");
    private static final Set<String> COMPARISON_OPERATORS = Set.of("===", "~=", "==", "!=", "<", "<=", ">", ">=");
    private final Map<String, String> environment;

    public Markers(String python, String platform) {
        String[] parts = python.split("\\.");
        String system = platform.equals("win32") ? "Windows" : platform.equals("darwin") ? "Darwin" : "Linux";
        environment = new HashMap<>(Map.of(
                "python_version", parts[0] + "." + parts[1], "python_full_version", python,
                "implementation_version", python, "implementation_name", "cpython",
                "platform_python_implementation", "CPython", "sys_platform", platform,
                "os_name", platform.equals("win32") ? "nt" : "posix",
                "platform_system", system,
                "platform_machine", System.getProperty("os.arch")));
        environment.put("platform_release", System.getProperty("os.version"));
        environment.put("platform_version", System.getProperty("os.version"));
    }

    public boolean active(String marker, Set<String> extras) {
        if (marker.isBlank()) return true;
        boolean result = new Expression(marker, "").parse();
        for (String extra : extras) result |= new Expression(marker, extra).parse();
        return result;
    }

    private static boolean compare(String left, String op, String right) {
        if (op.equals("in")) return right.contains(left);
        if (op.equals("not in")) return !right.contains(left);
        if (!COMPARISON_OPERATORS.contains(op)) {
            throw new IllegalArgumentException("invalid marker operator: " + op);
        }
        String version = right.endsWith(WILDCARD) ? right.substring(0, right.length() - WILDCARD.length()) : right;
        try {
            new Version(left);
            new Version(version);
            return Version.matches(left, op + right);
        } catch (IllegalArgumentException e) {
            return compareText(left.compareTo(right), op);
        }
    }

    private static boolean compareText(int comparison, String op) {
        return switch (op) {
            case "==", "===" -> comparison == 0;
            case "!=" -> comparison != 0;
            case "<" -> comparison < 0;
            case "<=" -> comparison <= 0;
            case ">" -> comparison > 0;
            case ">=" -> comparison >= 0;
            default -> throw new IllegalArgumentException("invalid marker comparison");
        };
    }

    private final class Expression {
        private final List<String> tokens = new ArrayList<>();
        private final String extra;
        private int index;

        Expression(String text, String extra) {
            this.extra = extra;
            int offset = 0;
            while (offset < text.length() && !text.substring(offset).isBlank()) {
                var m = TOKEN.matcher(text).region(offset, text.length());
                if (!m.lookingAt()) throw new IllegalArgumentException("invalid marker: " + text);
                tokens.add(m.group(1));
                offset = m.end();
            }
        }

        boolean parse() {
            boolean value = or();
            if (index != tokens.size()) {
                throw new IllegalArgumentException("unexpected marker token: " + tokens.get(index));
            }
            return value;
        }

        boolean or() {
            boolean value = and();
            while (take("or")) value |= and();
            return value;
        }

        boolean and() {
            boolean value = atom();
            while (take("and")) value &= atom();
            return value;
        }

        boolean atom() {
            if (take("(")) {
                boolean value = or();
                require(")");
                return value;
            }
            String leftToken = next();
            String left = operand(leftToken);
            String op = next();
            if (op.equals("not")) {
                require("in");
                op = "not in";
            }
            String rightToken = next();
            String right = operand(rightToken);
            if (leftToken.equals("extra") || rightToken.equals("extra")) {
                left = Release.normalize(left);
                right = Release.normalize(right);
            }
            return compare(left, op, right);
        }

        String operand(String token) {
            if (token.startsWith("'") || token.startsWith("\"")) return token.substring(1, token.length() - 1);
            if (token.equals("extra")) return extra;
            if (!environment.containsKey(token)) {
                throw new IllegalArgumentException("unknown marker variable: " + token);
            }
            return environment.get(token);
        }

        String next() {
            if (index == tokens.size()) throw new IllegalArgumentException("incomplete marker");
            return tokens.get(index++);
        }

        boolean take(String token) {
            if (index < tokens.size() && tokens.get(index).equals(token)) { index++; return true; }
            return false;
        }

        void require(String token) {
            if (!take(token)) throw new IllegalArgumentException("marker: expected " + token);
        }
    }
}
