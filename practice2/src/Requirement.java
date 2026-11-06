import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

public record Requirement(String name, String constraint, Set<String> extras, String marker) {
    public static Requirement parse(String text) {
        var m = Pattern.compile("^([A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?)(?:\\[([A-Za-z0-9._, -]+)\\])?\\s*([^;]*?)(?:\\s*;\\s*(.+))?$")
                .matcher(text.strip());
        if (!m.matches()) throw new IllegalArgumentException("invalid requirement: " + text);
        Set<String> extras = new LinkedHashSet<>();
        if (m.group(2) != null) {
            Arrays.stream(m.group(2).split(",", -1)).map(String::strip).forEach(extra -> {
                if (!extra.matches("[A-Za-z0-9]+(?:[._-][A-Za-z0-9]+)*")) throw new IllegalArgumentException("invalid extra: " + extra);
                extras.add(Release.normalize(extra));
            });
        }
        String constraint = m.group(3).strip();
        if (constraint.startsWith("@")) throw new IllegalArgumentException("URL requirements are unsupported: " + text);
        Version.matches("0", constraint);
        return new Requirement(Release.normalize(m.group(1)), constraint, Set.copyOf(extras), m.group(4) == null ? "" : m.group(4));
    }

    public boolean accepts(String version) {
        return Version.matches(version, constraint);
    }
}
