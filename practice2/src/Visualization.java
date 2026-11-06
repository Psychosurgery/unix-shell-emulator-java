import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

public final class Visualization {
    private static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 16);
    private record Box(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
        int centerX() { return x + width / 2; }
        int centerY() { return y + height / 2; }
    }

    public static String d2(Graph graph) {
        StringBuilder text = new StringBuilder("direction: right\n");
        Map<String, String> ids = new LinkedHashMap<>();
        for (var entry : graph.nodes().entrySet()) {
            String id = "n" + ids.size();
            ids.put(entry.getKey(), id);
            text.append(id).append(": \"").append(entry.getValue().label()).append("\"\n");
        }
        graph.edges().forEach((name, children) -> {
            for (String child : children) text.append(ids.get(name)).append(" -> ").append(ids.get(child)).append('\n');
        });
        return text.toString();
    }

    public static Path write(Graph graph, Path prefix) throws IOException {
        Path absolute = prefix.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path png = Path.of(absolute + ".png");
        Files.writeString(Path.of(absolute + ".d2"), d2(graph));
        if (!ImageIO.write(image(graph), "png", png.toFile())) throw new IOException("PNG writer is unavailable");
        return png;
    }

    public static BufferedImage image(Graph graph) throws IOException {
        if (graph.nodes().size() > 300) throw new IOException("image: at most 300 packages");
        Map<String, Integer> levels = new LinkedHashMap<>();
        Map<String, java.util.Set<String>> edges = graph.edges();
        ArrayDeque<String> queue = new ArrayDeque<>();
        for (String root : graph.nodes().keySet()) {
            if (levels.containsKey(root)) continue;
            levels.put(root, 0);
            queue.add(root);
            while (!queue.isEmpty()) {
                String name = queue.remove();
                for (String child : edges.get(name)) {
                    if (!levels.containsKey(child)) {
                        levels.put(child, levels.get(name) + 1);
                        queue.add(child);
                    }
                }
            }
        }
        List<List<String>> columns = new ArrayList<>();
        for (var entry : levels.entrySet()) {
            while (columns.size() <= entry.getValue()) columns.add(new ArrayList<>());
            columns.get(entry.getValue()).add(entry.getKey());
        }
        var measure = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
        measure.setFont(FONT);
        int rows = columns.stream().mapToInt(List::size).max().orElse(1);
        int height = 140 + rows * 100 + (graph.cycles().isEmpty() ? 0 : 70);
        int x = 50;
        Map<String, Box> boxes = new LinkedHashMap<>();
        for (List<String> column : columns) {
            int width = column.stream().mapToInt(n -> measure.getFontMetrics().stringWidth(graph.nodes().get(n).label()) + 40).max().orElse(180);
            width = Math.max(180, width);
            for (int row = 0; row < column.size(); row++) {
                int y = 100 + (rows * 100 - 54) / 2 + (row * 100 - (column.size() - 1) * 50);
                boxes.put(column.get(row), new Box(x, y, width, 54));
            }
            x += width + 120;
        }
        measure.dispose();
        int width = Math.max(440, x - 70);
        if ((long) width * height > 40_000_000) throw new IOException("image is too large");
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(248, 250, 252));
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(15, 23, 42));
        g.setFont(FONT.deriveFont(Font.BOLD, 22));
        g.drawString(graph.nodes().isEmpty() ? "Empty graph" : graph.nodes().values().iterator().next().label(), 50, 42);
        g.setColor(new Color(100, 116, 139));
        g.setFont(FONT.deriveFont(13f));
        g.drawString("package -> dependency", 50, 68);
        g.setStroke(new BasicStroke(2));
        for (var entry : edges.entrySet()) {
            for (String child : entry.getValue()) {
                Box from = boxes.get(entry.getKey());
                Box to = boxes.get(child);
                boolean back = levels.get(child) <= levels.get(entry.getKey());
                g.setColor(back ? new Color(190, 70, 60) : new Color(100, 116, 139));
                Path2D path = new Path2D.Double();
                if (back) {
                    int bottom = height - 25;
                    path.moveTo(from.right(), from.centerY());
                    path.curveTo(from.right() + 40, from.centerY(), from.right() + 40, bottom, from.centerX(), bottom);
                    path.lineTo(to.centerX(), bottom);
                    path.curveTo(to.centerX() - 30, bottom, to.centerX(), to.bottom() + 35, to.centerX(), to.bottom());
                    g.draw(path);
                    arrow(g, to.centerX(), to.bottom(), false);
                } else {
                    int middle = (from.right() + to.x()) / 2;
                    path.moveTo(from.right(), from.centerY());
                    path.curveTo(middle, from.centerY(), middle, to.centerY(), to.x(), to.centerY());
                    g.draw(path);
                    arrow(g, to.x(), to.centerY(), true);
                }
            }
        }
        g.setFont(FONT);
        for (var entry : boxes.entrySet()) {
            Box box = entry.getValue();
            boolean root = levels.get(entry.getKey()) == 0;
            g.setColor(root ? new Color(219, 234, 254) : Color.WHITE);
            g.fillRoundRect(box.x(), box.y(), box.width(), box.height(), 12, 12);
            g.setColor(root ? new Color(37, 99, 235) : new Color(148, 163, 184));
            g.drawRoundRect(box.x(), box.y(), box.width(), box.height(), 12, 12);
            g.setColor(new Color(15, 23, 42));
            String label = graph.nodes().get(entry.getKey()).label();
            g.drawString(label, box.centerX() - g.getFontMetrics().stringWidth(label) / 2, box.centerY() + 6);
        }
        g.dispose();
        return image;
    }

    private static void arrow(Graphics2D g, int x, int y, boolean right) {
        int[] xs = right ? new int[]{x, x - 10, x - 10} : new int[]{x, x - 5, x + 5};
        int[] ys = right ? new int[]{y, y - 5, y + 5} : new int[]{y, y + 10, y + 10};
        g.fillPolygon(xs, ys, 3);
    }

    public static void show(Path png) throws IOException {
        if (GraphicsEnvironment.isHeadless()) throw new IOException("display is unavailable; use --show false");
        BufferedImage image = ImageIO.read(png.toFile());
        if (image == null) throw new IOException("invalid image: " + png);
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame(png.getFileName().toString());
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.add(new JScrollPane(new JLabel(new ImageIcon(image))));
            frame.setSize(Math.min(image.getWidth() + 35, 1200), Math.min(image.getHeight() + 60, 850));
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
