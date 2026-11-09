import java.io.IOException;
import java.net.HttpURLConnection;
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

public final class PyPi {
    private final String base;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private final Map<String, Map<String, Object>> cache = new HashMap<>();

    public PyPi(String base) {
        this.base = base.replaceAll("/+$", "");
    }

    public Release load(String name, String version) throws IOException, InterruptedException {
        Map<String, Object> data = get(segment(Release.normalize(name)) + "/" + segment(version) + "/json");
        Map<String, Object> info = Json.object(data.get("info"));
        String actualName = Json.string(info.get("name"));
        String actualVersion = Json.string(info.get("version"));
        if (!Release.normalize(actualName).equals(Release.normalize(name)) || !actualVersion.equals(version)) {
            throw new IOException("repository returned another release: " + actualName + "==" + actualVersion);
        }
        List<String> requirements = new ArrayList<>();
        if (info.get("requires_dist") != null) {
            for (Object value : Json.array(info.get("requires_dist"))) requirements.add(Json.string(value));
        }
        return new Release(Release.normalize(actualName), actualVersion, requirements);
    }

    Map<String, Object> get(String path) throws IOException, InterruptedException {
        if (cache.containsKey(path)) return cache.get(path);
        URI uri = URI.create(base + "/" + path);
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json").header("User-Agent", "practice2-java/1").GET().build();
        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("HTTP " + response.statusCode() + ": " + uri);
        }
        Map<String, Object> result = Json.object(Json.parse(response.body()));
        cache.put(path, result);
        return result;
    }

    private static String segment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
