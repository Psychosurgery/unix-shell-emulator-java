import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record Options(String name, String repository, boolean test, String version, String filter, String mode, String python, String platform) {
    public static Options parse(String... args) {
        Map<String, String> values = new LinkedHashMap<>();
        Set<String> known = Set.of("--package", "--repository", "--test", "--version", "--filter", "--mode", "--python-version", "--platform");
        for (int i = 0; i < args.length; i += 2) {
            String key = args[i];
            if (!known.contains(key)) throw new IllegalArgumentException("unknown option: " + key);
            if (i + 1 == args.length || args[i + 1].startsWith("--")) {
                throw new IllegalArgumentException("missing value: " + key);
            }
            if (values.putIfAbsent(key, args[i + 1]) != null) {
                throw new IllegalArgumentException("duplicate option: " + key);
            }
        }
        String name = values.getOrDefault("--package", "");
        String version = values.getOrDefault("--version", "");
        String repository = values.getOrDefault("--repository", "https://pypi.org/pypi");
        String testValue = values.getOrDefault("--test", "false");
        String filter = values.getOrDefault("--filter", "");
        if (!Set.of("true", "false").contains(testValue)) {
            throw new IllegalArgumentException("--test: expected true or false");
        }
        boolean test = Boolean.parseBoolean(testValue);
        if (!name.matches(test ? "[A-Z]+" : "[A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?")) {
            throw new IllegalArgumentException("--package: invalid name");
        }
        if (!version.matches("(?i)v?(?:[0-9]+!)?[0-9]+(?:\\.[0-9]+)*(?:(?:a|b|rc)[0-9]+)?(?:\\.post[0-9]+)?(?:\\.dev[0-9]+)?(?:\\+[a-z0-9]+(?:[._-][a-z0-9]+)*)?")) {
            throw new IllegalArgumentException("--version: expected a concrete version");
        }
        if (!filter.isEmpty() && !filter.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("--filter: invalid substring");
        }
        if (test) {
            if (repository.isBlank() || !Files.isRegularFile(Path.of(repository)) || !Files.isReadable(Path.of(repository))) {
                throw new IllegalArgumentException("--repository: expected a readable file");
            }
        } else {
            URI uri;
            try {
                uri = URI.create(repository);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("--repository: invalid URL");
            }
            if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("--repository: expected an HTTP(S) PyPI JSON API URL");
            }
        }
        String mode = values.getOrDefault("--mode", "graph");
        if (!Set.of("config", "direct", "graph").contains(mode)) throw new IllegalArgumentException("--mode: invalid mode");
        String python = values.getOrDefault("--python-version", "3.13.0");
        if (!python.matches("[0-9]+\\.[0-9]+(?:\\.[0-9]+)?")) throw new IllegalArgumentException("--python-version: invalid version");
        if (python.split("\\.").length == 2) python += ".0";
        String os = System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT);
        String platform = values.getOrDefault("--platform", os.contains("win") ? "win32" : os.contains("mac") ? "darwin" : "linux");
        if (!Set.of("win32", "linux", "darwin").contains(platform)) throw new IllegalArgumentException("--platform: invalid platform");
        return new Options(name, repository, test, version, filter, mode, python, platform);
    }

    public void print() {
        System.out.println("package=" + name);
        System.out.println("repository=" + repository);
        System.out.println("test=" + test);
        System.out.println("version=" + version);
        System.out.println("filter=" + filter);
        System.out.println("mode=" + mode);
        System.out.println("python-version=" + python);
        System.out.println("platform=" + platform);
    }
}
