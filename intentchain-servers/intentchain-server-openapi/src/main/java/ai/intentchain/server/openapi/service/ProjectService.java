package ai.intentchain.server.openapi.service;

import ai.intentchain.core.chain.data.CascadeResult;
import ai.intentchain.core.classifiers.data.TextLabel;
import ai.intentchain.sdk.ProjectRunner;
import ai.intentchain.sdk.data.project.Project;
import ai.intentchain.sdk.utils.ProjectUtil;
import ai.intentchain.server.openapi.config.ServerConfig;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ServerConfig serverConfig;
    private ProjectRunner projectRunner;

    private ProjectRunner getProjectRunner() {
        if (projectRunner == null) {
            Path projectPath = serverConfig.getAbsoluteProjectPath();
            try {
                projectRunner = new ProjectRunner(projectPath);
            } catch (Exception e) {
                log.error("Failed to initialize project runner", e);
                throw new RuntimeException("Failed to initialize project runner: " + e.getMessage(), e);
            }
        }
        return projectRunner;
    }

    public Project getProject() {
        Path projectPath = serverConfig.getAbsoluteProjectPath();
        return ProjectUtil.loadProject(projectPath);
    }

    public CascadeResult classify(@NonNull String question) {
        return getProjectRunner().classify(question);
    }

    public void train(@NonNull List<TextLabel> trainingData) {
        getProjectRunner().train(trainingData);
    }
}