import java.nio.file.Path;

public record Configuration(Path vfsPath, Path scriptPath) {
    public static Configuration parse(String[] arguments) {
        Path vfs = null;
        Path script = null;
        for (int index = 0; index < arguments.length; index += 2) {
            if (index + 1 >= arguments.length) {
                throw new IllegalArgumentException("missing value for " + arguments[index]);
            }
            String option = arguments[index];
            Path value = Path.of(arguments[index + 1]).toAbsolutePath().normalize();
            if ("--vfs".equals(option) && vfs == null) {
                vfs = value;
            } else if ("--script".equals(option) && script == null) {
                script = value;
            } else {
                throw new IllegalArgumentException("unknown or duplicate option: " + option);
            }
        }
        return new Configuration(vfs, script);
    }

    public static String display(Path path) {
        return path == null ? "<not set>" : path.toString();
    }
}

