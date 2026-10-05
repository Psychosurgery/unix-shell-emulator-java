import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class Shell {
    private static final int MIN_MOVE_PATHS = 2;
    private final PrintStream output;
    private final PrintStream errors;
    private final Parser parser;
    private final Vfs vfs;
    private final String user;
    private final String host;
    private Vfs.Node workingDirectory;
    private Vfs.Node previousDirectory;
    private boolean running = true;

    public Shell(PrintStream output, PrintStream errors, Vfs vfs) {
        this.output = output;
        this.errors = errors;
        this.vfs = vfs;
        this.parser = new Parser(System.getenv());
        this.user = System.getProperty("user.name", "unknown");
        this.host = findHost();
        this.workingDirectory = vfs.root();
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
        } catch (ParseException | VfsException exception) {
            errors.println("shell: " + exception.getMessage());
            return false;
        }
    }

    public String prompt() {
        String directory = vfs.path(workingDirectory);
        String display = directory.equals("/") ? "~" : "~" + directory;
        return user + "@" + host + ":" + display + "$ ";
    }

    private boolean dispatch(List<String> words) throws VfsException {
        String command = words.get(0);
        switch (command) {
            case "ls" -> list(words.subList(1, words.size()));
            case "cd" -> changeDirectory(words.subList(1, words.size()));
            case "date" -> showDate(words.subList(1, words.size()));
            case "whoami" -> showUser(words.subList(1, words.size()));
            case "cat" -> printFiles(words.subList(1, words.size()));
            case "mv" -> move(words.subList(1, words.size()));
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

    private void changeDirectory(List<String> arguments) throws VfsException {
        if (arguments.size() > 1) {
            throw new VfsException("cd: too many arguments");
        }
        String target = arguments.isEmpty() ? "~" : arguments.get(0);
        Vfs.Node destination = target.equals("-") ? previousDirectory
                : vfs.resolve(target, workingDirectory);
        if (destination == null) {
            throw new VfsException("cd: OLDPWD not set");
        }
        if (!destination.isDirectory()) {
            throw new VfsException("cd: " + target + ": Not a directory");
        }
        previousDirectory = workingDirectory;
        workingDirectory = destination;
        if (target.equals("-")) {
            output.println(vfs.path(destination));
        }
    }

    private void list(List<String> arguments) throws VfsException {
        LsOptions options = LsOptions.parse(arguments);
        List<String> targets = options.targets().isEmpty() ? List.of(".") : options.targets();
        for (int index = 0; index < targets.size(); index++) {
            String target = targets.get(index);
            Vfs.Node node = vfs.resolve(target, workingDirectory);
            if (targets.size() > 1) {
                if (index > 0) {
                    output.println();
                }
                output.println(target + ":");
            }
            listNode(node, options);
        }
    }

    private void listNode(Vfs.Node node, LsOptions options) {
        if (!node.isDirectory()) {
            output.println(formatEntry(node, options.longFormat()));
            return;
        }
        if (options.all()) {
            output.println(".");
            output.println("..");
        }
        node.children().values().stream()
                .filter(child -> options.all() || !child.name().startsWith("."))
                .sorted(Comparator.comparing(Vfs.Node::name))
                .forEach(child -> output.println(formatEntry(child, options.longFormat())));
    }

    private String formatEntry(Vfs.Node node, boolean longFormat) {
        if (!longFormat) {
            return node.name();
        }
        String type = node.isDirectory() ? "d" : "-";
        int size = node.isDirectory() ? 0 : node.content().length;
        return type + " " + size + " " + node.name();
    }

    private void printFiles(List<String> arguments) throws VfsException {
        if (arguments.isEmpty()) {
            throw new VfsException("cat: missing file operand");
        }
        for (String path : arguments) {
            Vfs.Node node = vfs.resolve(path, workingDirectory);
            if (node.isDirectory()) {
                throw new VfsException("cat: " + path + ": Is a directory");
            }
            output.print(new String(node.content(), StandardCharsets.UTF_8));
        }
    }

    private void showDate(List<String> arguments) throws VfsException {
        if (arguments.size() > 1 || (!arguments.isEmpty() && !arguments.get(0).equals("-u"))) {
            throw new VfsException("date: usage: date [-u]");
        }
        ZoneId zone = arguments.isEmpty() ? ZoneId.systemDefault() : ZoneOffset.UTC;
        DateTimeFormatter format = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss z yyyy", Locale.ENGLISH);
        output.println(ZonedDateTime.now(zone).format(format));
    }

    private void showUser(List<String> arguments) throws VfsException {
        if (!arguments.isEmpty()) {
            throw new VfsException("whoami: too many arguments");
        }
        output.println(user);
    }

    private void move(List<String> arguments) throws VfsException {
        List<String> paths = new ArrayList<>(arguments);
        if (!paths.isEmpty() && paths.get(0).equals("--")) {
            paths.remove(0);
        } else if (!paths.isEmpty() && paths.get(0).startsWith("-")) {
            throw new VfsException("mv: unsupported option: " + paths.get(0));
        }
        if (paths.size() < MIN_MOVE_PATHS) {
            throw new VfsException("mv: missing source or destination operand");
        }
        String target = paths.remove(paths.size() - 1);
        try {
            vfs.move(paths, target, workingDirectory);
        } catch (VfsException exception) {
            throw new VfsException("mv: " + exception.getMessage());
        }
    }

    private String findHost() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException exception) {
            return System.getenv().getOrDefault("COMPUTERNAME", "localhost");
        }
    }
}
