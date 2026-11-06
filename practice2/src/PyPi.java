import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PyPi implements Repository {
    private final String base;
    private final String python;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final Map<String, Map<String, Object>> cache = new HashMap<>();

    public PyPi(String base) {
        this(base, "3.13.0");
    }

    public PyPi(String base, String python) {
        this.base = base.replaceAll("/+$", "");
        this.python = python;
    }

    public Release load(String name, String version) throws IOException, InterruptedException {
        Map<String, Object> data = get(segment(Release.normalize(name)) + "/" + segment(version) + "/json");
        Map<String, Object> info = Json.object(data.get("info"));
        String actualName = Json.string(info.get("name"));
        String actualVersion = Json.string(info.get("version"));
        if (!Release.normalize(actualName).equals(Release.normalize(name)) || !actualVersion.equals(version)) {
            throw new IOException("repository returned another release: " + actualName + "==" + actualVersion);
        }
        if (info.get("requires_python") instanceof String spec && !Version.matches(python, spec)) {
            throw new IOException("incompatible Python version: " + actualName + " requires " + spec);
        }
        List<String> requirements = new ArrayList<>();
        if (info.get("requires_dist") != null) {
            for (Object value : Json.array(info.get("requires_dist"))) requirements.add(Json.string(value));
        }
        return new Release(Release.normalize(actualName), actualVersion, requirements);
    }

    @Override
    public List<String> versions(String name) throws IOException, InterruptedException {
        Map<String, Object> releases = Json.object(get(segment(Release.normalize(name)) + "/json").get("releases"));
        List<String> result = new ArrayList<>();
        for (var entry : releases.entrySet()) {
            try {
                if (new Version(entry.getKey()).prerelease()) continue;
            } catch (IllegalArgumentException e) {
                continue;
            }
            for (Object raw : Json.array(entry.getValue())) {
                Map<String, Object> file = Json.object(raw);
                if (Boolean.TRUE.equals(file.get("yanked"))) continue;
                if (file.get("requires_python") instanceof String spec && !Version.matches(python, spec)) continue;
                result.add(entry.getKey());
                break;
            }
        }
        result.sort((a, b) -> new Version(b).compareTo(new Version(a)));
        return result;
    }

    Map<String, Object> get(String path) throws IOException, InterruptedException {
        if (cache.containsKey(path)) return cache.get(path);
        URI uri = URI.create(base + "/" + path);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json").header("User-Agent", "practice2-java/1").GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode() + ": " + uri);
        Map<String, Object> result = Json.object(Json.parse(response.body()));
        cache.put(path, result);
        return result;
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
