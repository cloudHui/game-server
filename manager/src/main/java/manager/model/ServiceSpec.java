package manager.model;

public record ServiceSpec(String id, String name, String jarPath, int port, String heap) {}
