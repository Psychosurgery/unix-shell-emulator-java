public class Tests {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Options options = Options.parse("--package", "requests", "--version", "2.32.3");
        check(options.repository().equals("https://pypi.org/pypi"));
        check(!options.test() && options.filter().isEmpty());
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
        check(Json.string(Json.object(Json.parse("{\"x\":\"a\\n\\u0042\",\"v\":[true,null,1.2e3]}" )).get("x")).equals("a\nB"));
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
            String path = exchange.getRequestURI().getPath();
            String body = switch (path) {
                case "/pypi/demo/1/json" -> "{\"info\":{\"name\":\"demo\",\"version\":\"1\",\"requires_dist\":[\"child>=2\",\"opt; extra == 'test'\"]}}";
                case "/pypi/demo/2/json" -> "{\"info\":{\"name\":\"demo\",\"version\":\"2\",\"requires_dist\":null}}";
                case "/pypi/bad/1/json" -> "oops";
                default -> "missing";
            };
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(body.equals("missing") ? 404 : 200, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        });
        server.start();
        try {
            PyPi repository = new PyPi("http://127.0.0.1:" + server.getAddress().getPort() + "/pypi/");
            check(repository.load("demo", "1").requirements().equals(java.util.List.of("child>=2", "opt; extra == 'test'")));
            repository.load("demo", "1");
            check(hits.get() == 1);
            check(repository.load("demo", "2").requirements().isEmpty());
            fails(() -> repository.load("absent", "1"));
            fails(() -> repository.load("bad", "1"));
        } finally {
            server.stop(0);
        }
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
