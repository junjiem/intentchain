package ai.intentchain.core.classifiers;

import ai.intentchain.core.classifiers.data.Intent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Intent classifier using simple keyword matching.
 * <p>
 * The classifier takes a list of keywords and associated intents as an input.
 * An input sentence is checked for the keywords and the intent is returned.
 */
@Slf4j
public class KeywordIntentClassifier implements IntentClassifier {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final String name;

    private final boolean caseSensitive;
    private Map<String, List<String>> keywordsByLabel;

    public KeywordIntentClassifier(@NonNull String name, boolean caseSensitive,
                                   @NonNull Map<String, List<String>> keywordsByLabel) {
        this.name = name;
        this.caseSensitive = caseSensitive;
        this.keywordsByLabel = keywordsByLabel;
    }

    @Override
    public String classifierName() {
        return name;
    }

    @Override
    public List<Intent> classify(@NonNull String text) {
        log.debug("Keyword - Start matching content.");

        List<Intent> intents = keywordsByLabel.entrySet().stream()
                .filter(e -> e.getValue().stream().filter(Objects::nonNull)
                        .anyMatch(s -> caseSensitive ? text.contains(s) :
                                text.toLowerCase().contains(s.toLowerCase())))
                .map(e -> Intent.from(e.getKey()))
                .collect(Collectors.toList());
        if (intents.isEmpty()) {
            log.debug("Keyword - Not matched fallback.");
            return Collections.emptyList();
        }
        try {
            log.debug("Keyword - Return the intents: " + JSON_MAPPER.writeValueAsString(intents));
        } catch (JsonProcessingException e) {
            //
        }
        return intents;
    }
}
