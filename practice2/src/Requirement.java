import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

public record Requirement(String name, String constraint, Set<String> extras, String marker) {
    private static final Pattern FORMAT = Pattern.compile(
            "^(?<name>[A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?)(?:\\[(?<extras>[A-Za-z0-9._, -]+)\\])?"
                    + "\\s*(?<constraint>[^;]*?)(?:\\s*;\\s*(?<marker>.+))?$");

    public static Requirement parse(String text) {
        var m = FORMAT.matcher(text.strip());
        if (!m.matches()) throw new IllegalArgumentException("invalid requirement: " + text);
        Set<String> extras = parseExtras(m.group("extras"));
        String constraint = m.group("constraint").strip();
        if (constraint.startsWith("@")) {
            throw new IllegalArgumentException("URL requirements are unsupported: " + text);
        }
        Version.matches("0", constraint);
        String marker = m.group("marker");
        return new Requirement(Release.normalize(m.group("name")), constraint, extras,
                marker == null ? "" : marker);
    }

    private static Set<String> parseExtras(String group) {
        Set<String> extras = new LinkedHashSet<>();
        if (group != null) {
            Arrays.stream(group.split(",", -1)).map(String::strip).forEach(extra -> {
                if (!extra.matches("[A-Za-z0-9]+(?:[._-][A-Za-z0-9]+)*")) {
                    throw new IllegalArgumentException("invalid extra: " + extra);
                }
                extras.add(Release.normalize(extra));
            });
        }
        return Set.copyOf(extras);
    }

    public boolean accepts(String version) {
        return Version.matches(version, constraint);
    }
}
