public class Tests {
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
        System.out.println("checks=" + checks);
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
