package ai.intentchain.sdk;


import ai.intentchain.core.classifiers.data.TextLabel;
import ai.intentchain.sdk.data.FileChanges;
import ai.intentchain.sdk.data.FileState;
import ai.intentchain.sdk.data.project.Project;
import ai.intentchain.sdk.utils.FileUtil;
import ai.intentchain.sdk.utils.ProjectUtil;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文件变化分析器
 */
@Slf4j
class FileChangeAnalyzer {

    private final Path modelsPath;

    private final Project project;

    private final List<Path> csvFilePaths;

    public FileChangeAnalyzer(Project project, Path projectPath) {
        this.project = project;
        this.modelsPath = projectPath.resolve(ProjectUtil.MODELS_DIR_NAME);
        this.csvFilePaths = ProjectUtil.scanCsvFiles(modelsPath);
    }

    public FileChanges analyzeChanges(List<FileState> fileStates) {
        ChangeTrainingDataCacheUtil.remove(project.getName());

        Map<String, FileState> fileStateMap = fileStates.stream()
                .collect(Collectors.toMap(FileState::getRelativePath, Function.identity()));

        List<FileState> newFiles = new ArrayList<>();
        List<FileState> modifiedFiles = new ArrayList<>();
        List<FileState> unchangedFiles = new ArrayList<>();

        // 分析当前存在的CSV文件
        for (Path filePath : csvFilePaths) {
            String relativePath = modelsPath.relativize(filePath).toString();
            FileState fileState = fileStateMap.get(relativePath);
            if (fileState == null) {
                // 新CSV文件
                long lastModified = FileUtil.lastModified(filePath);
                String md5Hash = FileUtil.md5(filePath);
                List<TextLabel> textLabels = ProjectUtil.loadTextLabels(filePath, modelsPath);
                newFiles.add(createFileState(relativePath, lastModified, md5Hash, textLabels));
            } else {
                // 已存在的CSV文件，检查是否发生变化
                boolean hasChanged = false;
                String md5Hash = null;
                long lastModified = FileUtil.lastModified(filePath);
                if (lastModified - fileState.getLastModified() > 0) {
                    md5Hash = FileUtil.md5(filePath);
                    hasChanged = !md5Hash.equals(fileState.getMd5Hash());
                }
                if (hasChanged) { // CSV文件已修改
                    List<TextLabel> textLabels = ProjectUtil.loadTextLabels(filePath, modelsPath);
                    modifiedFiles.add(createFileState(relativePath, lastModified, md5Hash, textLabels));
                } else {
                    // CSV文件未变化，保留之前的元数据
                    unchangedFiles.add(fileState);
                }
            }
        }

        // 查找已删除的CSV文件
        List<String> relativePaths = csvFilePaths.stream()
                .map(p -> modelsPath.relativize(p).toString()).toList();
        List<FileState> deletedFiles = fileStates.stream()
                .filter(p -> !relativePaths.contains(p.getRelativePath()))
                .collect(Collectors.toList());

        return new FileChanges(newFiles, modifiedFiles, unchangedFiles, deletedFiles);
    }

    private FileState createFileState(String relativePath, long lastModified, String md5Hash,
                                      List<TextLabel> textLabels) {
        ChangeTrainingDataCacheUtil.add(project.getName(), relativePath, textLabels);
        return FileState.builder()
                .relativePath(relativePath)
                .lastModified(lastModified)
                .md5Hash(md5Hash)
                .build();
    }
}