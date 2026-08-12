package manager.service;

import manager.model.ServiceSpec;
import java.util.Optional;

final class ProcessFinder {
    Optional<ProcessHandle> find(ServiceSpec spec) {
        String jarName = spec.name() + ".jar";
        return ProcessHandle.allProcesses()
            .filter(ProcessHandle::isAlive)
            .filter(process -> process.info().commandLine().orElse("").contains(jarName))
            .findFirst();
    }
}
