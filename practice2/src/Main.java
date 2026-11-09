public class Main {
    public static void main(String[] args) {
        try {
            Options.parse(args).print();
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            System.exit(2);
        }
    }
}
