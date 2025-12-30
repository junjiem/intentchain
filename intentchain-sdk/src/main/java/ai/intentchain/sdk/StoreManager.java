package ai.intentchain.sdk;

import ai.intentchain.core.chain.CascadeIntentChain;
import ai.intentchain.core.classifiers.data.TrainingData;
import ai.intentchain.sdk.data.FileChanges;
import ai.intentchain.sdk.data.FileState;
import ai.intentchain.sdk.data.project.Project;
import ai.intentchain.sdk.utils.ProjectUtil;
import ai.intentchain.sdk.utils.TimeUtil;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Slf4j
class StoreManager {
    private final Project project;
    private final CascadeIntentChain intentChain;
    private final String stateId;
    private final BuildStateManager stateManager;

    public StoreManager(Project project, Path projectPath, String stateId) {
        this.project = project;
        this.intentChain = ProjectUtil.createIntentChain(project, projectPath);
        this.stateId = stateId;
        this.stateManager = new BuildStateManager(projectPath);
    }

    public void updateStore(@NonNull List<FileState> fileStates, @NonNull FileChanges changes) {
        Instant start = Instant.now();
        AtomicBoolean isCompleted = new AtomicBoolean(false);
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            if (!isCompleted.get()) System.out.print("█");
        }, 0, 3, TimeUnit.SECONDS); // Print the progress bar every 3 seconds
        try {
            Map<String, FileState> oldFileStates = fileStates.stream()
                    .collect(Collectors.toMap(FileState::getRelativePath, f -> f));
            List<FileState> newFileStates = new ArrayList<>(changes.unchangedFiles());
            Map<String, List<TrainingData>> trainingDataMap = ChangeTrainingDataCacheUtil.remove(project.getName());
            changes.deletedFiles().forEach(fs -> remove(oldFileStates, fs));
            changes.newFiles().forEach(fs -> add(newFileStates, fs, trainingDataMap));
            changes.modifiedFiles().forEach(fs -> {
                remove(oldFileStates, fs);
                add(newFileStates, fs, trainingDataMap);
            });
            stateManager.saveBuildState(stateId, newFileStates);
        } catch (Exception e) {
            isCompleted.set(true);
            scheduler.shutdown();
            throw new RuntimeException("Update store failed", e);
        } finally {
            isCompleted.set(true);
            scheduler.shutdown();
        }
        Duration duration = Duration.between(start, Instant.now());
        String formattedDuration = TimeUtil.formatDuration(duration.toMillis());
        System.out.println("\t[ " + formattedDuration + " ]");
    }

    private void add(List<FileState> newFileStates, FileState fileState,
                     Map<String, List<TrainingData>> trainingDataMap) {
        String relativePath = fileState.getRelativePath();
        FileState.FileStateBuilder builder = FileState.builder()
                .relativePath(fileState.getRelativePath())
                .lastModified(fileState.getLastModified())
                .md5Hash(fileState.getMd5Hash());
        if (trainingDataMap.containsKey(relativePath)) {
            List<TrainingData> trainingData = trainingDataMap.get(relativePath);
            builder.vectorIds(intentChain.train(trainingData));
        }
        newFileStates.add(builder.build());
    }

    private void remove(Map<String, FileState> oldFileStates, FileState fileState) {
        String relativePath = fileState.getRelativePath();
        if (!oldFileStates.containsKey(relativePath)) {
            return;
        }
        FileState oldFileState = oldFileStates.get(relativePath);
        intentChain.remove(oldFileState.getVectorIds());
    }
}