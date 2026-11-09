public class Main {
    public static void main(String[] args) {
        try {
            Options options = Options.parse(args);
            if (options.mode().equals("config")) {
                options.print();
                return;
            }
            Options.Source source = options.source();
            Options.Target target = options.target();
            Repository repository = source.test() ? new TestRepository(java.nio.file.Path.of(source.repository()))
                    : new PyPi(source.repository(), target.python());
            if (options.mode().equals("direct")) {
                repository.load(options.name(), options.version()).requirements().forEach(System.out::println);
                return;
            }
            Graph graph = new Graph(repository, options.filter(), new Markers(target.python(), target.platform()))
                    .build(options.name(), options.version());
            graph.print();
            graph.cycles().forEach(cycle -> System.err.println("cycle: " + cycle));
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            System.exit(2);
        } catch (java.io.IOException e) {
            System.err.println("error: " + e.getMessage());
            System.exit(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("error: interrupted");
            System.exit(1);
        }
    }
}
