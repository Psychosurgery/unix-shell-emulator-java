import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

public final class Vfs {
    public static final class Node {
        private String name;
        private Node parent;
        private final Map<String, Node> children;
        private final byte[] content;

        private Node(String name, Node parent, byte[] content) {
            this.name = name;
            this.parent = parent;
            this.content = content;
            this.children = content == null ? new LinkedHashMap<>() : null;
        }

        public String name() {
            return name;
        }

        public Node parent() {
            return parent;
        }

        public boolean isDirectory() {
            return children != null;
        }

        public Map<String, Node> children() {
            return children;
        }

        public byte[] content() {
            return content;
        }
    }

    private final Node root = new Node("", null, null);

    private Vfs() {
    }

    public static Vfs empty() {
        return new Vfs();
    }

    public static Vfs load(Path source) throws IOException {
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("VFS path not found: " + source);
        }
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("invalid VFS format (expected a directory): " + source);
        }
        Vfs vfs = new Vfs();
        vfs.loadChildren(source, vfs.root);
        return vfs;
    }

    public Node root() {
        return root;
    }

    public int entryCount() {
        return countChildren(root);
    }

    public Node resolve(String path, Node workingDirectory) throws VfsException {
        Node current = startsAtRoot(path) ? root : workingDirectory;
        for (String part : withoutTilde(path).split("/")) {
            if (!part.isEmpty()) {
                current = resolvePart(path, current, part);
            }
        }
        if (path.endsWith("/") && !current.isDirectory()) {
            throw new VfsException(path + ": Not a directory");
        }
        return current;
    }

    private boolean startsAtRoot(String path) {
        return path.startsWith("/") || path.equals("~") || path.startsWith("~/");
    }

    private String withoutTilde(String path) {
        return path.equals("~") || path.startsWith("~/") ? path.substring(1) : path;
    }

    private Node resolvePart(String path, Node current, String part) throws VfsException {
        if (!current.isDirectory()) {
            throw new VfsException(path + ": Not a directory");
        }
        if (part.equals(".")) {
            return current;
        }
        if (part.equals("..")) {
            return current.parent == null ? root : current.parent;
        }
        Node next = current.children.get(part);
        if (next == null) {
            throw new VfsException(path + ": No such file or directory");
        }
        return next;
    }

    public String path(Node node) {
        List<String> parts = new ArrayList<>();
        for (Node current = node; current.parent != null; current = current.parent) {
            parts.add(0, current.name);
        }
        return "/" + String.join("/", parts);
    }

    private void loadChildren(Path source, Node parent) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(source)) {
            for (Path item : stream) {
                Node child = loadItem(item, parent);
                parent.children.put(child.name, child);
            }
        }
    }

    private Node loadItem(Path item, Node parent) throws IOException {
        if (Files.isSymbolicLink(item)) {
            throw new IOException("invalid VFS format (symbolic link): " + item);
        }
        if (Files.isDirectory(item, LinkOption.NOFOLLOW_LINKS)) {
            Node directory = new Node(item.getFileName().toString(), parent, null);
            loadChildren(item, directory);
            return directory;
        }
        if (Files.isRegularFile(item, LinkOption.NOFOLLOW_LINKS)) {
            return new Node(item.getFileName().toString(), parent, Files.readAllBytes(item));
        }
        throw new IOException("invalid VFS format (unsupported entry): " + item);
    }

    private int countChildren(Node directory) {
        int count = 0;
        for (Node child : directory.children.values()) {
            count++;
            if (child.isDirectory()) {
                count += countChildren(child);
            }
        }
        return count;
    }
}
