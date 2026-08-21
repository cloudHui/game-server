package com.cloud.hub.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class DataPathResolver {
    private final Path root;
    public DataPathResolver(@Value("$"+"{hub.root:$"+"{user.dir}}") String root) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
    }
    public Path resolve(String configuredPath) {
        Path path = Paths.get(configuredPath);
        return (path.isAbsolute() ? path : root.resolve(path)).normalize();
    }
    public Path root() { return root; }
}
