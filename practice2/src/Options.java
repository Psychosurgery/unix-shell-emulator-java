import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record Options(
        String name, String version, String filter, Source source) {
    private static final Set<String> KEYS = Set.of(
            "--package", "--repository", "--test", "--version", "--filter");
    private static final String VERSION_PATTERN =
            "(?i)v?(?:[0-9]+!)?[0-9]+(?:\\.[0-9]+)*(?:(?:a|b|rc)[0-9]+)?"
                    + "(?:\\.post[0-9]+)?(?:\\.dev[0-9]+)?(?:\\+[a-z0-9]+(?:[._-][a-z0-9]+)*)?";

    public record Source(String repository, boolean test) {}

    public static Options parse(String... args) {
        Map<String, String> values = readPairs(args);
        String name = values.getOrDefault("--package", "");
        String version = values.getOrDefault("--version", "");
        String repository = values.getOrDefault("--repository", "https://pypi.org/pypi");
        boolean test = readBoolean(values, "--test", "false");
        String filter = values.getOrDefault("--filter", "");
        validatePackage(name, version, filter, test);
        validateRepository(repository, test);
        return new Options(name, version, filter, new Source(repository, test));
    }

    private static Map<String, String> readPairs(String[] args) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            String key = args[i];
            if (!KEYS.contains(key)) throw new IllegalArgumentException("unknown option: " + key);
            if (i + 1 == args.length || args[i + 1].startsWith("--")) {
                throw new IllegalArgumentException("missing value: " + key);
            }
            if (values.putIfAbsent(key, args[i + 1]) != null) {
                throw new IllegalArgumentException("duplicate option: " + key);
            }
        }
        return values;
    }

    private static void validatePackage(String name, String version, String filter, boolean test) {
        String namePattern = test ? "[A-Z]+" : "[A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?";
        if (!name.matches(namePattern)) throw new IllegalArgumentException("--package: invalid name");
        if (!version.matches(VERSION_PATTERN)) {
            throw new IllegalArgumentException("--version: expected a concrete version");
        }
        if (!filter.isEmpty() && !filter.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("--filter: invalid substring");
        }
    }

    private static void validateRepository(String repository, boolean test) {
        if (test) {
            if (repository.isBlank()) throw new IllegalArgumentException("--repository: expected a readable file");
            Path path = Path.of(repository);
            if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
                throw new IllegalArgumentException("--repository: expected a readable file");
            }
            return;
        }
        validateUrl(repository);
    }

    private static void validateUrl(String repository) {
        URI uri;
        try {
            uri = URI.create(repository);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("--repository: invalid URL");
        }
        boolean http = "https".equals(uri.getScheme()) || "http".equals(uri.getScheme());
        if (!http || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("--repository: expected an HTTP(S) PyPI JSON API URL");
        }
    }

    private static boolean readBoolean(Map<String, String> values, String key, String fallback) {
        String value = values.getOrDefault(key, fallback);
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new IllegalArgumentException(key + ": expected true or false");
        }
        return Boolean.parseBoolean(value);
    }

    public void print() {
        System.out.println("package=" + name);
        System.out.println("repository=" + source.repository());
        System.out.println("test=" + source.test());
        System.out.println("version=" + version);
        System.out.println("filter=" + filter);
    }
}
