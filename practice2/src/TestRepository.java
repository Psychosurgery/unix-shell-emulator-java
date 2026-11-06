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
            if (!name.matches("[A-Z]+")) throw new IllegalArgumentException("test repository: expected uppercase name: " + name);
            Map<String, Release> releases = new LinkedHashMap<>();
            for (var version : Json.object(entry.getValue()).entrySet()) {
                new Version(version.getKey());
                List<String> requirements = new ArrayList<>();
                for (Object value : Json.array(version.getValue())) {
                    String raw = Json.string(value);
                    Requirement.parse(raw);
                    String target = raw.split("[\\[<>=!~; (]", 2)[0];
                    if (!target.matches("[A-Z]+")) throw new IllegalArgumentException("test dependency: expected uppercase name: " + target);
                    requirements.add(raw);
                }
                releases.put(version.getKey(), new Release(name, version.getKey(), requirements));
            }
            if (releases.isEmpty()) throw new IllegalArgumentException("test repository: no versions: " + name);
            packages.put(Release.normalize(name), releases);
        }
        for (var releases : packages.values()) {
            for (Release release : releases.values()) {
                for (String raw : release.requirements()) {
                    Requirement requirement = Requirement.parse(raw);
                    if (!packages.containsKey(requirement.name()) || versions(requirement.name()).stream().noneMatch(requirement::accepts)) {
                        throw new IllegalArgumentException("test repository: missing dependency: " + raw);
                    }
                }
            }
        }
    }

    @Override
    public Release load(String name, String version) throws IOException {
        Map<String, Release> releases = packages.get(Release.normalize(name));
        if (releases == null || !releases.containsKey(version)) throw new IOException("unknown release: " + name + "==" + version);
        return releases.get(version);
    }

    @Override
    public List<String> versions(String name) throws IOException {
        Map<String, Release> releases = packages.get(Release.normalize(name));
        if (releases == null) throw new IOException("unknown package: " + name);
        return releases.keySet().stream().sorted((a, b) -> new Version(b).compareTo(new Version(a))).toList();
    }
}
