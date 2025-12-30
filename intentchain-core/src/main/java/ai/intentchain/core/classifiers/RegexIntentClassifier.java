package ai.intentchain.core.classifiers;

import ai.intentchain.core.classifiers.data.Intent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Intent classifier using regex matching.
 * <p>
 * The classifier takes a list of regex and associated intents as an input.
 * An input sentence is checked for the regex and the intent is returned.
 */
@Slf4j
public class RegexIntentClassifier implements IntentClassifier {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final String name;

    private final int flags;
    private final Map<String, List<Pattern>> regexsByLabel;

    public RegexIntentClassifier(@NonNull String name, boolean caseSensitive,
                                 @NonNull Map<String, List<String>> regexsByLabel) {
        this.name = name;
        this.flags = caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
        this.regexsByLabel = regexsByLabel.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .map(regex -> Pattern.compile(regex, flags))
                                .collect(Collectors.toList())));
    }

    @Override
    public String classifierName() {
        return name;
    }

    @Override
    public List<Intent> classify(@NonNull String text) {
        log.debug("Regex - Start matching content.");
        List<Intent> intents = regexsByLabel.entrySet().stream()
                .filter(e -> e.getValue().stream().filter(Objects::nonNull).anyMatch(p -> p.matcher(text).find()))
                .map(e -> Intent.from(e.getKey()))
                .collect(Collectors.toList());
        if (intents.isEmpty()) {
            log.debug("Regex - Not matched fallback.");
            return Collections.emptyList();
        }
        try {
            log.debug("Regex - Return the intents: " + JSON_MAPPER.writeValueAsString(intents));
        } catch (JsonProcessingException e) {
            //
        }
        return intents;
    }
}
