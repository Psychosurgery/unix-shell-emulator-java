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
        versions();
        markers();
        graphs();
        invalidRepositories();
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
            check(repository.versions("demo").equals(java.util.List.of("2", "1")));
            check(repository.select(Requirement.parse("demo<2")).version().equals("1"));
            Graph graph = new Graph(repository, "", new Markers("3.13.0", "win32")).build("demo", "1");
            check(graph.nodes().keySet().equals(java.util.Set.of("demo", "child")));
            check(graph.edges().get("demo").equals(java.util.Set.of("child")));
            fails(() -> repository.select(Requirement.parse("demo>10")));
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
            case "/pypi/demo/json" -> """
                    {"releases":{"1":[{}],"2":[{}],"3rc1":[{}],"4":[{"yanked":true}],
                    "5":[{"requires_python":">=4"}],"old-invalid":[{}],"6":[]}}
                    """;
            case "/pypi/child/json" -> "{\"releases\":{\"2\":[{}]}}";
            case "/pypi/child/2/json" -> "{\"info\":{\"name\":\"child\",\"version\":\"2\",\"requires_dist\":null}}";
            default -> "missing";
        };
    }

    private static void versions() throws Exception {
        for (String[] pair : new String[][]{{"1.9", "1.10"}, {"1.dev1", "1a1"}, {"1a1", "1b1"}, {"1b1", "1rc1"},
                {"1rc1", "1"}, {"1", "1.post1.dev1"}, {"1.post1.dev1", "1.post1"}, {"1", "1+abc"},
                {"1+abc", "1+2"}, {"99", "1!1"}, {"1.999999999999999999999", "2"}}) {
            check(new Version(pair[0]).compareTo(new Version(pair[1])) < 0);
        }
        check(new Version("1.0.0").compareTo(new Version("1")) == 0);
        for (String[] pair : new String[][]{{"2.31.0", ">=2,<3"}, {"1.4.9", "~=1.4.5"}, {"1.9", "~=1.4"},
                {"1.2.3", "==1.2.*"}, {"1.0+local", "==1"}, {"1.0rc2", ">1.0rc1"}, {"1.0", ">1.0rc1"},
                {"2", "(>=1,!=3)"}, {"1!2", ">=1!1"}}) check(Version.matches(pair[0], pair[1]));
        for (String[] pair : new String[][]{{"2", "<2"}, {"1.5", "~=1.4.5"}, {"2", "~=1.4"}, {"1.2", "!=1.*"},
                {"1.0rc1", "<1"}, {"1.post1", ">1"}, {"1.0.1", "==1"}}) check(!Version.matches(pair[0], pair[1]));
        fails(() -> Requirement.parse("bad@https://example.org/x"));
        fails(() -> Requirement.parse("foo>=1,nonsense"));
        fails(() -> Requirement.parse("foo~=1"));
        fails(() -> Requirement.parse("foo>1.*"));
        fails(() -> Requirement.parse("foo[extra,]"));
        check(Requirement.parse("My_Package[a,b]>=1; python_version >= '3.10'").name().equals("my-package"));
    }

    private static void markers() throws Exception {
        Markers markers = new Markers("3.13.0", "win32");
        for (String value : new String[]{"python_version >= '3.10'", "sys_platform == 'win32' and os_name == 'nt'",
                "(python_version < '3' or python_version >= '3.13') and extra != 'docs'",
                "'win' in sys_platform", "implementation_name not in 'pypy,jython'", "extra == 'some-extra'"}) {
            check(markers.active(value, java.util.Set.of("Some_Extra")));
        }
        check(!markers.active("extra == 'docs'", java.util.Set.of()));
        check(!markers.active("python_version < '3.10' or sys_platform == 'linux'", java.util.Set.of()));
        fails(() -> markers.active("unknown == 'x'", java.util.Set.of()));
        fails(() -> markers.active("python_version >=", java.util.Set.of()));
        fails(() -> markers.active("sys_platform == 'win32' or unknown == 'x'", java.util.Set.of()));
        fails(() -> markers.active("(sys_platform == 'win32'", java.util.Set.of()));
    }

    static Graph graph(String fixture, String filter) throws Exception {
        return new Graph(new TestRepository(java.nio.file.Path.of("tests/fixtures/" + fixture + ".json")), filter,
                new Markers("3.13.0", "win32")).build("A", "1");
    }

    private static void graphs() throws Exception {
        Graph chain = graph("chain", "");
        check(chain.nodes().keySet().equals(java.util.Set.of("a", "b", "c")) && chain.cycles().isEmpty());
        check(chain.edges().get("a").equals(java.util.Set.of("b")));
        Graph diamond = graph("diamond", "");
        check(diamond.nodes().keySet().equals(java.util.Set.of("a", "b", "c", "d")));
        check(diamond.edges().get("c").equals(java.util.Set.of("d")));
        check(graph("cycle", "").cycles().equals(java.util.Set.of("a -> b -> c -> a")));
        check(graph("self", "").cycles().equals(java.util.Set.of("a -> a")));
        check(graph("empty", "").nodes().keySet().equals(java.util.Set.of("a")));
        check(graph("chain", "B").nodes().keySet().equals(java.util.Set.of("a")));
        check(graph("diamond", "B").nodes().keySet().equals(java.util.Set.of("a", "c", "d")));
        check(graph("chain", "A").nodes().isEmpty());
        check(graph("versions", "").nodes().get("b").version().equals("1"));
        Repository versions = new TestRepository(java.nio.file.Path.of("tests/fixtures/versions.json"));
        Markers linux = new Markers("3.13.0", "linux");
        check(new Graph(versions, "", linux).build("A", "2").nodes().get("b").version().equals("2"));
        check(graph("extras", "").nodes().keySet().equals(java.util.Set.of("a", "b", "c", "d")));
        fails(() -> graph("conflict", ""));
        fails(() -> new Graph(versions, "", linux).build("A", "3"));
    }

    private static void invalidRepositories() throws Exception {
        for (String content : new String[]{"[]", "{\"a\":{\"1\":[]}}", "{\"A\":{\"1\":[\"B==1\"]}}", "{\"A\":{}}"}) {
            java.nio.file.Path path = java.nio.file.Path.of("build/invalid.json");
            java.nio.file.Files.writeString(path, content);
            fails(() -> new TestRepository(path));
        }
        String[][] invalidOptions = {{"--mode", "bad"}, {"--python-version", "latest"}, {"--platform", "dos"}};
        for (String[] extra : invalidOptions) {
            invalid("--package", "a", "--version", "1", extra[0], extra[1]);
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
