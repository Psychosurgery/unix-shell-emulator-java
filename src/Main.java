import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        Configuration configuration;
        try {
            configuration = Configuration.parse(args);
        } catch (IllegalArgumentException exception) {
            System.err.println("shell: " + exception.getMessage());
            System.exit(2);
            return;
        }
        System.out.println("VFS: " + Configuration.display(configuration.vfsPath()));
        System.out.println("Startup script: " + Configuration.display(configuration.scriptPath()));
        Vfs vfs;
        try {
            vfs = configuration.vfsPath() == null
                    ? Vfs.empty() : Vfs.load(configuration.vfsPath());
        } catch (IOException exception) {
            System.err.println("shell: " + exception.getMessage());
            System.exit(1);
            return;
        }
        System.out.println("VFS entries: " + vfs.entryCount());
        Shell shell = new Shell(System.out, System.err, vfs);
        Path script = configuration.scriptPath();
        if (script != null) {
            System.exit(runScript(shell, script));
            return;
        }
        try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in))) {
            shell.run(input);
        } catch (IOException exception) {
            System.err.println("shell: input error: " + exception.getMessage());
            System.exit(1);
        }
    }

    private static int runScript(Shell shell, Path script) {
        try (BufferedReader input = Files.newBufferedReader(script)) {
            return shell.runScript(input) ? 0 : 1;
        } catch (IOException exception) {
            System.err.println("shell: cannot read startup script: " + exception.getMessage());
            return 1;
        }
    }
}
