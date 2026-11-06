import java.io.IOException;
import java.util.List;

public interface Repository {
    Release load(String name, String version) throws IOException, InterruptedException;

    List<String> versions(String name) throws IOException, InterruptedException;

    default Release select(Requirement requirement) throws IOException, InterruptedException {
        for (String version : versions(requirement.name())) {
            if (requirement.accepts(version)) return load(requirement.name(), version);
        }
        throw new IOException("no matching version: " + requirement.name() + requirement.constraint());
    }
}
