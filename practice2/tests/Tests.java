import java.net.HttpURLConnection;

public class Tests {
    private static final int CACHED_REQUESTS = 1;
    private static int checks;

    public static void main(String[] args) throws Exception {
        Options options = Options.parse("--package", "requests", "--version", "2.32.3");
        check(options.source().repository().equals("https://pypi.org/pypi"));
        check(!options.source().test() && options.filter().isEmpty());
        invalid("--package", "bad/name", "--version", "1");
        invalid("--package", "a", "--version", "latest");
        invalid("--package", "a", "--version", "1", "--repository", "ftp://example.org");
        invalid("--package", "a", "--version", "1", "--repository", "http://[");
        invalid("--package", "a", "--version", "1", "--test", "yes");
        invalid("--package", "a", "--version", "1", "--filter", "*");
        invalid("--package", "A", "--version", "1", "--test", "true", "--repository", "missing.json");
        invalid("--package", "a", "--version", "1", "--test", "true", "--repository", "README.md");
        invalid("--package", "a", "--version", "1", "--version", "2");
        invalid("--package", "a", "--version");
        invalid("--unknown", "1");
        invalid("--version", "1");
        invalid("--package", "a");
        var json = Json.object(Json.parse("{\"x\":\"a\\n\\u0042\",\"v\":[true,null,1.2e3]}"));
        check(Json.string(json.get("x")).equals("a\nB"));
        for (String value : new String[]{"", "{", "[1,]", "01", "{\"x\":null,\"x\":1}", "\"\\u+123\"", "true false"}) {
            fails(() -> Json.parse(value));
        }
        http();
        System.out.println("checks=" + checks);
    }

    private static void http() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        var hits = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/pypi/", exchange -> {
            hits.incrementAndGet();
            String body = responseBody(exchange.getRequestURI().getPath());
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            int status = body.equals("missing") ? HttpURLConnection.HTTP_NOT_FOUND : HttpURLConnection.HTTP_OK;
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            PyPi repository = new PyPi("http://127.0.0.1:" + server.getAddress().getPort() + "/pypi/");
            var direct = java.util.List.of("child>=2", "opt; extra == 'test'");
            check(repository.load("demo", "1").requirements().equals(direct));
            repository.load("demo", "1");
            check(hits.get() == CACHED_REQUESTS);
            check(repository.load("demo", "2").requirements().isEmpty());
            fails(() -> repository.load("absent", "1"));
            fails(() -> repository.load("bad", "1"));
        } finally {
            server.stop(0);
        }
    }

    private static String responseBody(String path) {
        return switch (path) {
            case "/pypi/demo/1/json" -> """
                    {"info":{"name":"demo","version":"1","requires_dist":["child>=2","opt; extra == 'test'"]}}
                    """;
            case "/pypi/demo/2/json" -> "{\"info\":{\"name\":\"demo\",\"version\":\"2\",\"requires_dist\":null}}";
            case "/pypi/bad/1/json" -> "oops";
            default -> "missing";
        };
    }

    interface Action {
        void run() throws Exception;
    }

    static void fails(Action action) throws Exception {
        try {
            action.run();
            throw new AssertionError("expected an error");
        } catch (IllegalArgumentException | java.io.IOException e) {
            check(e.getMessage() != null);
        }
    }

    private static void invalid(String... args) {
        try {
            Options.parse(args);
            throw new AssertionError("accepted invalid options");
        } catch (IllegalArgumentException e) {
            check(e.getMessage() != null);
        }
    }

    static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("check " + checks);
    }
}
