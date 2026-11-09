import java.util.List;
import java.util.Locale;

public record Release(String name, String version, List<String> requirements) {
    public Release {
        requirements = List.copyOf(requirements);
    }

    public static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[-_.]+", "-");
    }

    public String label() {
        return name + "==" + version;
    }
}
