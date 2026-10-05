import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        Shell shell = new Shell(System.out, System.err);
        try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in))) {
            shell.run(input);
        } catch (IOException exception) {
            System.err.println("shell: input error: " + exception.getMessage());
            System.exit(1);
        }
    }
}

