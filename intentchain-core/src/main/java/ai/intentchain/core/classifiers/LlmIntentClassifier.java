package ai.intentchain.core.classifiers;

import ai.intentchain.core.classifiers.data.Intent;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.output.structured.Description;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Intent classifier using the LLM (Large Language Model)
 */
@Slf4j
public class LlmIntentClassifier implements IntentClassifier {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final String name;

    private final Assistant assistant;

    private final List<Category> categories;
    private final List<String> fallbackCategories;
    private final String instruction;

    @Builder
    public LlmIntentClassifier(@NonNull String name,
                               @NonNull ChatModel chatModel,
                               @NonNull Map<String, String> categories,
                               List<String> fallbackCategories, String instruction) {
        this.name = name;
        this.assistant = AiServices.builder(Assistant.class)
                .chatModel(chatModel)
                .build();

        this.categories = categories.entrySet().stream()
                .map(e -> new Category(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
        this.fallbackCategories = Optional.ofNullable(fallbackCategories).orElse(Collections.emptyList());
        this.instruction = Optional.ofNullable(instruction).orElse("");
    }

    @Override
    public String classifierName() {
        return name;
    }

    @Override
    public List<Intent> classify(@NonNull String text) {
        log.debug("LLM - Start text classification.");
        String categoriesJson;
        try {
            categoriesJson = JSON_MAPPER.writeValueAsString(categories);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        CategoryResult result = assistant.textClassification(text, categoriesJson, instruction);
        if (!fallbackCategories.isEmpty()) {
            log.debug("LLM - Start fallback categories check.");
            if (fallbackCategories.stream().anyMatch(c -> c.equals(result.category_name))) {
                log.debug("LLM - Categories check fallback.");
                return Collections.emptyList();
            }
        }
        if (categories.stream().noneMatch(c -> c.name.equals(result.category_name))) {
            log.debug("LLM - Category not exist fallback.");
            return Collections.emptyList();
        }
        List<Intent> intents = Collections.singletonList(Intent.from(result.category_name));
        try {
            log.debug("LLM - Return the intents: " + JSON_MAPPER.writeValueAsString(intents));
        } catch (JsonProcessingException e) {
            //
        }
        return intents;
    }

    private interface Assistant {
        @UserMessage(fromResource = "prompts/text_classification_user_prompt.txt")
        CategoryResult textClassification(@V("input_text") String inputText,
                                          @V("categories") String categoriesJson,
                                          @V("instruction") String instruction);
    }

    private record Category(@NonNull @JsonProperty("category_name") String name,
                            @NonNull @JsonProperty("category_description") String description) {
    }

    @Getter
    @Setter
    @NoArgsConstructor
    private static class CategoryResult {
        @Description("Extract the key words from the text that are related to the classification")
        private List<String> keywords;

        @Description("The category name of which category it belongs to")
        @JsonProperty("category_name")
        private String category_name;
    }
}
