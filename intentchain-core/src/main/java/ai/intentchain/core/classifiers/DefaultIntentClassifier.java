package ai.intentchain.core.classifiers;

import ai.intentchain.core.classifiers.data.Intent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Intent classifier using the defaults
 */
@Slf4j
public class DefaultIntentClassifier implements IntentClassifier {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final String name;

    private final List<String> defaults;

    public DefaultIntentClassifier(@NonNull String name, @NonNull List<String> defaults) {
        this.name = name;
        this.defaults = defaults;
    }

    @Override
    public String classifierName() {
        return name;
    }

    @Override
    public List<Intent> classify(@NonNull String text) {
        log.debug("Default - Start get default content.");
        List<Intent> intents = defaults.stream().map(Intent::from).collect(Collectors.toList());
        try {
            log.debug("Default - Return the intents: " + JSON_MAPPER.writeValueAsString(intents));
        } catch (JsonProcessingException e) {
            //
        }
        return intents;
    }
}
