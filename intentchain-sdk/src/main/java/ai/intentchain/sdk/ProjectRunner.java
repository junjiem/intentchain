package ai.intentchain.sdk;

import ai.intentchain.core.chain.CascadeIntentChain;
import ai.intentchain.core.chain.data.CascadeResult;
import ai.intentchain.core.classifiers.data.TextLabel;
import ai.intentchain.sdk.data.project.Project;
import ai.intentchain.sdk.utils.ProjectUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.util.List;

@Slf4j
public class ProjectRunner {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final CascadeIntentChain intentChain;

    public ProjectRunner(@NonNull Path projectPath) {
        Project project = ProjectUtil.loadProject(projectPath);
        this.intentChain = ProjectUtil.createIntentChain(project, projectPath);
    }

    public CascadeResult classify(@NonNull String text) {
        CascadeResult result = intentChain.classify(text);
        try {
            log.info("The cascade intent chain result: " + JSON_MAPPER.writeValueAsString(result));
        } catch (JsonProcessingException e) {
            //
        }
        return result;
    }

    public void train(@NonNull List<TextLabel> trainingData) {
        intentChain.train(trainingData);
    }
}