import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Graph {
    private final Repository repository;
    private final Markers markers;
    private final String filter;
    private final Map<String, Release> nodes = new LinkedHashMap<>();
    private final Map<String, Set<String>> edges = new LinkedHashMap<>();
    private final Map<String, Set<String>> extras = new LinkedHashMap<>();
    private final Set<String> complete = new LinkedHashSet<>();
    private final List<String> active = new ArrayList<>();
    private final Set<String> cycles = new LinkedHashSet<>();

    public Graph(Repository repository, String filter, Markers markers) {
        this.repository = repository;
        this.filter = Release.normalize(filter);
        this.markers = markers;
    }

    public Graph build(String name, String version) throws IOException, InterruptedException {
        Requirement root = Requirement.parse(name + "==" + version);
        if (!excluded(root.name())) visit(root, repository.load(name, version));
        return this;
    }

    private boolean excluded(String name) {
        return !filter.isEmpty() && Release.normalize(name).contains(filter);
    }

    private void visit(Requirement requirement, Release fixed) throws IOException, InterruptedException {
        String name = requirement.name();
        Release release = nodes.get(name);
        if (release == null) {
            release = fixed == null ? repository.select(requirement) : fixed;
            nodes.put(name, release);
            edges.put(name, new LinkedHashSet<>());
            extras.put(name, new LinkedHashSet<>());
        } else if (!requirement.accepts(release.version())) {
            throw new IOException("version conflict: " + release.label() + " / " + requirement.constraint() + " (no backtracking)");
        }
        boolean changed = extras.get(name).addAll(requirement.extras());
        if (active.contains(name)) {
            List<String> cycle = new ArrayList<>(active.subList(active.indexOf(name), active.size()));
            cycle.add(name);
            cycles.add(String.join(" -> ", cycle));
            return;
        }
        if (complete.contains(name) && !changed) return;
        active.add(name);
        Set<String> snapshot;
        do {
            snapshot = new LinkedHashSet<>(extras.get(name));
            for (String raw : release.requirements()) {
                Requirement dependency = Requirement.parse(raw);
                if (excluded(dependency.name()) || !markers.active(dependency.marker(), snapshot)) continue;
                edges.get(name).add(dependency.name());
                visit(dependency, null);
            }
        } while (!snapshot.equals(extras.get(name)));
        active.remove(active.size() - 1);
        complete.add(name);
    }

    public Map<String, Release> nodes() {
        return java.util.Collections.unmodifiableMap(nodes);
    }

    public Map<String, Set<String>> edges() {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        edges.forEach((name, children) -> result.put(name, java.util.Collections.unmodifiableSet(children)));
        return java.util.Collections.unmodifiableMap(result);
    }

    public Set<String> cycles() {
        return java.util.Collections.unmodifiableSet(cycles);
    }

    public void print() {
        for (String name : nodes.keySet()) {
            if (edges.get(name).isEmpty()) System.out.println(nodes.get(name).label());
            for (String child : edges.get(name)) System.out.println(nodes.get(name).label() + " -> " + nodes.get(child).label());
        }
    }
}
