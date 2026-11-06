public class Main {
    public static void main(String[] args) {
        try {
            Options options = Options.parse(args);
            if (options.mode().equals("config")) {
                options.print();
                return;
            }
            if (options.test()) throw new IllegalArgumentException("test graphs: use --mode config at this stage");
            Release release = new PyPi(options.repository()).load(options.name(), options.version());
            release.requirements().forEach(System.out::println);
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
