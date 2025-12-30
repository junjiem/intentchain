package ai.intentchain.sdk;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;

@Slf4j
public class ProjectTester {

    private final Path projectPath;

    public ProjectTester(@NonNull Path projectPath) {
        this.projectPath = projectPath;
    }

    public void test() {

    }
}
