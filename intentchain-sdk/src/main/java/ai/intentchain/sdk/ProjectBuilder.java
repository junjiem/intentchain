package ai.intentchain.sdk;


import ai.intentchain.sdk.data.FileChanges;
import ai.intentchain.sdk.data.FileState;
import ai.intentchain.sdk.data.project.Project;
import ai.intentchain.sdk.utils.ProjectUtil;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public class ProjectBuilder {

    private final Path projectPath;

    private Project project;

    private final BuildStateManager stateManager;

    public ProjectBuilder(@NonNull Path projectPath) {
        this.projectPath = projectPath;
        this.stateManager = new BuildStateManager(projectPath);
    }

    /**
     * 构建项目
     *
     * @throws IOException
     */
    public void build() throws IOException {
        log.info("Start incremental build project ...");
        if (project == null) {
            project = ProjectUtil.loadProject(projectPath);
        }

        String fingerprint = ProjectUtil.storeFingerprint(project);
        List<FileState> fileStates = stateManager.loadBuildState(fingerprint);

        FileChangeAnalyzer fileChangeAnalyzer = new FileChangeAnalyzer(project, projectPath);
        FileChanges changes = fileChangeAnalyzer.analyzeChanges(fileStates);

        if (changes.hasChanges()) {
            // 更新状态
            StoreManager storeManager = new StoreManager(project, projectPath, fingerprint);
            storeManager.updateStore(fileStates, changes);
        }
        log.info("Incremental build project completed");
    }

    /**
     * 强制重建项目
     *
     * @throws IOException
     */
    public void forceRebuild() throws IOException {
        log.info("Start force rebuild project ...");
        cleanState();
        build();
    }

    /**
     * 清理当前状态文件
     */
    public void cleanState() throws IOException {
        log.info("Start clean current states ...");
        if (project == null) {
            project = ProjectUtil.loadProject(projectPath);
        }
        stateManager.cleanState(ProjectUtil.storeFingerprint(project));
    }

    /**
     * 清理所有状态文件
     */
    public void cleanAllStates() throws IOException {
        log.info("Start clean all states ...");
        stateManager.cleanAllState();
    }

    /**
     * 清理过期的状态文件
     */
    public void cleanOldStates(int keepCount) throws IOException {
        log.info("Start clean the expired states ..., keep count: {}", keepCount);
        stateManager.cleanOldStates(keepCount);
    }
}