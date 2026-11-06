public class Main {
    public static void main(String[] args) {
        try {
            Options options = Options.parse(args);
            if (options.mode().equals("config")) {
                options.print();
                return;
            }
            Repository repository = options.test() ? new TestRepository(java.nio.file.Path.of(options.repository()))
                    : new PyPi(options.repository(), options.python());
            if (options.mode().equals("direct")) {
                repository.load(options.name(), options.version()).requirements().forEach(System.out::println);
                return;
            }
            Graph graph = new Graph(repository, options.filter(), new Markers(options.python(), options.platform()))
                    .build(options.name(), options.version());
            if (options.mode().equals("order")) {
                graph.order().forEach(release -> System.out.println(release.label()));
            } else {
                graph.print();
            }
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
