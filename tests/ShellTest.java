import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

public final class ShellTest {
    private static final int DEEP_VFS_ENTRY_COUNT = 8;
    private ShellTest() {
    }

    public static void main(String[] arguments) throws Exception {
        testParser();
        testVfs();
        testCommands();
        testScriptFailure();
        testMoveInMemory();
        testMoveErrors();
        System.out.println("All shell tests passed.");
    }

    private static void testParser() throws Exception {
        Parser parser = new Parser(Map.of("HOME", "/home/test"));
        List<String> actual = parser.parse("ls \"$HOME\" '$HOME' a\\ b ${HOME}");
        check(actual.equals(List.of("ls", "/home/test", "$HOME", "a b", "/home/test")), "parser");
        try {
            parser.parse("ls 'broken");
            throw new AssertionError("unclosed quote was accepted");
        } catch (ParseException expected) {
            check(expected.getMessage().contains("quote"), "quote error");
        }
    }

    private static void testVfs() throws Exception {
        Vfs vfs = fixture();
        check(vfs.entryCount() == DEEP_VFS_ENTRY_COUNT, "VFS entry count");
        Vfs.Node file = vfs.resolve("/home/user/projects/demo/readme.txt", vfs.root());
        check(!file.isDirectory(), "deep file");
        check(vfs.path(file).endsWith("/demo/readme.txt"), "virtual path");
    }

    private static void testCommands() throws Exception {
        Capture capture = new Capture(fixture());
        check(capture.shell.execute("ls /home/user"), "ls directory");
        check(capture.output().contains("projects"), "ls contents");
        check(capture.shell.execute("cd /home/user"), "cd directory");
        check(capture.shell.prompt().contains("~/home/user"), "prompt directory");
        check(capture.shell.execute("cat todo.txt"), "cat file");
        check(capture.output().contains("Learn shell commands."), "cat content");
        check(capture.shell.execute("whoami"), "whoami");
        check(capture.output().contains(System.getProperty("user.name")), "OS username");
        check(capture.shell.execute("date -u"), "date UTC");
        check(!capture.shell.execute("cat projects"), "cat directory error");
        check(capture.errors().contains("Is a directory"), "directory diagnostic");
    }

    private static void testScriptFailure() throws Exception {
        Capture capture = new Capture(fixture());
        BufferedReader input = new BufferedReader(new StringReader("ls /\nwrong\nwhoami\n"));
        check(!capture.shell.runScript(input), "script failure status");
        check(!capture.output().contains("whoami"), "script stops after first failure");
    }

    private static void testMoveInMemory() throws Exception {
        Path source = Path.of("tests", "fixtures", "vfs", "deep", "home", "user", "todo.txt");
        String original = Files.readString(source);
        Capture capture = new Capture(fixture());
        check(capture.shell.execute("mv /home/user/todo.txt /home/user/tasks.txt"), "rename file");
        check(capture.shell.execute("cat /home/user/tasks.txt"), "renamed content");
        check(!capture.shell.execute("cat /home/user/todo.txt"), "old virtual path is absent");
        check(capture.shell.execute("mv /docs/guide.txt /home/user/projects/demo"), "move into directory");
        check(capture.shell.execute("cat /home/user/projects/demo/guide.txt"), "moved file content");
        check(capture.shell.execute("mv /home/user/projects/demo /home/user/archive"), "move directory");
        check(capture.shell.execute("cat /home/user/archive/guide.txt"), "moved directory content");
        check(capture.shell.execute("mv /home/user/tasks.txt /home/user/archive/guide.txt"), "replace file");
        check(capture.shell.execute("cat /home/user/archive/guide.txt"), "replaced file content");
        check(Files.readString(source).equals(original), "disk file unchanged");
        check(!Files.exists(source.resolveSibling("tasks.txt")), "no disk file created");
        Vfs fresh = fixture();
        check(fresh.resolve("/home/user/todo.txt", fresh.root()) != null, "fresh VFS has original file");
    }

    private static void testMoveErrors() throws Exception {
        Capture capture = new Capture(fixture());
        check(!capture.shell.execute("mv /home/user /home/user/projects"), "directory cycle rejected");
        check(!capture.shell.execute("mv / /home/user"), "root move rejected");
        check(!capture.shell.execute("mv /home/user/todo.txt"), "missing operand rejected");
        check(capture.errors().contains("mv:"), "move errors identified");
    }

    private static Vfs fixture() throws Exception {
        return Vfs.load(Path.of("tests", "fixtures", "vfs", "deep"));
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Capture {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private final ByteArrayOutputStream errors = new ByteArrayOutputStream();
        private final Shell shell;

        private Capture(Vfs vfs) {
            shell = new Shell(new PrintStream(output, true, StandardCharsets.UTF_8),
                    new PrintStream(errors, true, StandardCharsets.UTF_8), vfs);
        }

        private String output() {
            return output.toString(StandardCharsets.UTF_8);
        }

        private String errors() {
            return errors.toString(StandardCharsets.UTF_8);
        }
    }
}
