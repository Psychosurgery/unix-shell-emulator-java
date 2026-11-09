import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TestRepository implements Repository {
    private final Map<String, Map<String, Release>> packages = new LinkedHashMap<>();

    public TestRepository(Path path) throws IOException {
        String text = Files.readString(path);
        Map<String, Object> data = Json.object(Json.parse(text.startsWith("\uFEFF") ? text.substring(1) : text));
        for (var entry : data.entrySet()) {
            String name = entry.getKey();
            if (!name.matches("[A-Z]+")) {
                throw new IllegalArgumentException("test repository: expected uppercase name: " + name);
            }
            packages.put(Release.normalize(name), parseReleases(name, Json.object(entry.getValue())));
        }
        validateRequirements();
    }

    private static Map<String, Release> parseReleases(String name, Map<String, Object> data) {
        Map<String, Release> releases = new LinkedHashMap<>();
        for (var entry : data.entrySet()) {
            String version = entry.getKey();
            new Version(version);
            List<String> requirements = new ArrayList<>();
            for (Object value : Json.array(entry.getValue())) {
                String raw = Json.string(value);
                Requirement.parse(raw);
                String target = raw.split("[\\[<>=!~; (]", 2)[0];
                if (!target.matches("[A-Z]+")) {
                    throw new IllegalArgumentException("test dependency: expected uppercase name: " + target);
                }
                requirements.add(raw);
            }
            releases.put(version, new Release(name, version, requirements));
        }
        if (releases.isEmpty()) throw new IllegalArgumentException("test repository: no versions: " + name);
        return releases;
    }

    private void validateRequirements() {
        for (var releases : packages.values()) {
            for (Release release : releases.values()) {
                for (String raw : release.requirements()) {
                    Requirement requirement = Requirement.parse(raw);
                    Map<String, Release> candidates = packages.get(requirement.name());
                    if (candidates == null || candidates.keySet().stream().noneMatch(requirement::accepts)) {
                        throw new IllegalArgumentException("test repository: missing dependency: " + raw);
                    }
                }
            }
        }
    }

    @Override
    public Release load(String name, String version) throws IOException {
        Map<String, Release> releases = packages.get(Release.normalize(name));
        if (releases == null || !releases.containsKey(version)) {
            throw new IOException("unknown release: " + name + "==" + version);
        }
        return releases.get(version);
    }

    @Override
    public List<String> versions(String name) throws IOException {
        Map<String, Release> releases = packages.get(Release.normalize(name));
        if (releases == null) throw new IOException("unknown package: " + name);
        return releases.keySet().stream().sorted((a, b) -> new Version(b).compareTo(new Version(a))).toList();
    }
}
