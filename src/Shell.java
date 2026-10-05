import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

public final class Shell {
    private final PrintStream output;
    private final PrintStream errors;
    private final Parser parser;
    private final Vfs vfs;
    private final String user;
    private final String host;
    private boolean running = true;

    public Shell(PrintStream output, PrintStream errors, Vfs vfs) {
        this.output = output;
        this.errors = errors;
        this.vfs = vfs;
        this.parser = new Parser(System.getenv());
        this.user = System.getProperty("user.name", "unknown");
        this.host = findHost();
    }

    public void run(BufferedReader input) throws IOException {
        while (running) {
            output.print(prompt());
            output.flush();
            String line = input.readLine();
            if (line == null) {
                break;
            }
            execute(line);
        }
    }

    public boolean runScript(BufferedReader input) throws IOException {
        String line;
        while (running && (line = input.readLine()) != null) {
            output.println(prompt() + line);
            output.flush();
            if (!execute(line)) {
                return false;
            }
        }
        return true;
    }

    public boolean execute(String line) {
        try {
            List<String> words = parser.parse(line);
            if (words.isEmpty()) {
                return true;
            }
            return dispatch(words);
        } catch (ParseException exception) {
            errors.println("shell: " + exception.getMessage());
            return false;
        }
    }

    public String prompt() {
        return user + "@" + host + ":~$ ";
    }

    private boolean dispatch(List<String> words) {
        String command = words.get(0);
        switch (command) {
            case "ls", "cd" -> output.println(command + " " + words.subList(1, words.size()));
            case "exit" -> {
                if (words.size() != 1) {
                    errors.println("exit: too many arguments");
                    return false;
                }
                running = false;
            }
            default -> {
                errors.println(command + ": command not found");
                return false;
            }
        }
        return true;
    }

    private String findHost() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException exception) {
            return System.getenv().getOrDefault("COMPUTERNAME", "localhost");
        }
    }
}
