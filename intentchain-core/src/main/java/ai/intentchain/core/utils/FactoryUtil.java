package ai.intentchain.core.utils;


import ai.intentchain.core.classifiers.IntentClassifier;
import ai.intentchain.core.configuration.ConfigOption;
import ai.intentchain.core.configuration.ReadableConfig;
import ai.intentchain.core.exception.ValidationException;
import ai.intentchain.core.factories.*;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.NonNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Utility for working with {@link Factory}s.
 */
public final class FactoryUtil {

    public static final String HIDDEN_CONTENT = "******";
    public static final String PLACEHOLDER_SYMBOL = "#";

    // the keys whose values should be hidden
    private static final String[] SENSITIVE_KEYS =
            new String[]{
                    "password",
                    "secret",
                    "account.key",
                    "apikey",
                    "api-key",
                    "auth-params",
                    "service-key",
                    "token",
                    "basic-auth",
                    "jaas.config"
            };

    private static final String ERROR_MESSAGE = "Failed to create %s, factory identifier: '%s'.\n\t%s";

    private FactoryUtil() {
    }

    /**
     * Create Intent Classifier
     *
     * @param name
     * @param identifier
     * @param config
     * @return
     */
    public static IntentClassifier createIntentClassifier(@NonNull String name,
                                                          @NonNull String identifier,
                                                          @NonNull ReadableConfig config,
                                                          EmbeddingModel embeddingModel,
                                                          EmbeddingStore<TextSegment> embeddingStore,
                                                          ScoringModel scoringModel,
                                                          ChatModel chatModel) {
        IntentClassifierFactory factory = IntentClassifierFactoryManager.getFactory(identifier);
        try {
            return factory.create(name, config, embeddingModel, embeddingStore, scoringModel, chatModel);
        } catch (Exception e) {
            throw new RuntimeException(String.format(ERROR_MESSAGE,
                    IntentClassifierFactoryManager.getDescription(), identifier, e.getMessage()), e);
        }
    }

    /**
     * Create Embedding Model
     *
     * @param identifier
     * @param config
     * @return
     */
    public static EmbeddingModel createEmbeddingModel(@NonNull String identifier, @NonNull ReadableConfig config) {
        EmbeddingModelFactory factory = EmbeddingModelFactoryManager.getFactory(identifier);
        try {
            return factory.create(config);
        } catch (Exception e) {
            throw new RuntimeException(String.format(ERROR_MESSAGE,
                    EmbeddingModelFactoryManager.getDescription(), identifier, e.getMessage()), e);
        }
    }

    /**
     * Create Scoring (reranking) Model
     *
     * @param identifier
     * @param config
     * @return
     */
    public static ScoringModel createScoringModel(@NonNull String identifier, @NonNull ReadableConfig config) {
        ScoringModelFactory factory = ScoringModelFactoryManager.getFactory(identifier);
        try {
            return factory.create(config);
        } catch (Exception e) {
            throw new RuntimeException(String.format(ERROR_MESSAGE,
                    ScoringModelFactoryManager.getDescription(), identifier, e.getMessage()), e);
        }
    }

    /**
     * Create Embedding Store
     *
     * @param storeId
     * @param identifier
     * @param config
     * @return
     */
    public static EmbeddingStore<TextSegment> createEmbeddingStore(@NonNull String storeId,
                                                                   @NonNull String identifier,
                                                                   @NonNull ReadableConfig config) {
        EmbeddingStoreFactory factory = EmbeddingStoreFactoryManager.getFactory(identifier);
        try {
            return factory.create(storeId, config);
        } catch (Exception e) {
            throw new RuntimeException(String.format(ERROR_MESSAGE,
                    EmbeddingStoreFactoryManager.getDescription(), identifier, e.getMessage()), e);
        }
    }

    /**
     * Create Chat Model
     *
     * @param identifier
     * @param config
     * @return
     */
    public static ChatModel createChatModel(@NonNull String identifier, @NonNull ReadableConfig config) {
        ChatModelFactory factory = ChatModelFactoryManager.getFactory(identifier);
        try {
            return factory.create(config);
        } catch (Exception e) {
            throw new RuntimeException(String.format(ERROR_MESSAGE,
                    ChatModelFactoryManager.getDescription(), identifier, e.getMessage()), e);
        }
    }

    /**
     * Validates the required and optional {@link ConfigOption}s of a factory.
     *
     * <p>Note: It does not check for left-over options.
     */
    public static void validateFactoryOptions(Factory factory, ReadableConfig options) {
        validateFactoryOptions(factory.requiredOptions(), factory.optionalOptions(), options);
    }

    /**
     * Validates the required options and optional options.
     *
     * <p>Note: It does not check for left-over options.
     */
    public static void validateFactoryOptions(
            Set<ConfigOption<?>> requiredOptions,
            Set<ConfigOption<?>> optionalOptions,
            ReadableConfig options) {
        // currently IntentChain's options have no validation feature which is why we access them eagerly
        // to provoke a parsing error
        final List<String> missingRequiredOptions =
                requiredOptions.stream()
                        // Templated options will never appear with their template key, so we need
                        // to ignore them as required properties here
                        .filter(option -> !option.key().contains(PLACEHOLDER_SYMBOL))
                        .filter(option -> readOption(options, option) == null)
                        .map(ConfigOption::key)
                        .sorted()
                        .collect(Collectors.toList());
        if (!missingRequiredOptions.isEmpty()) {
            throw new ValidationException(
                    String.format(
                            "One or more required options are missing.\n\n"
                            + "Missing required options are:\n\n"
                            + "%s",
                            String.join("\n", missingRequiredOptions)));
        }
        optionalOptions.forEach(option -> readOption(options, option));
    }

    /**
     * Validates unconsumed option keys.
     */
    public static void validateUnconsumedKeys(
            String factoryIdentifier,
            Set<String> allOptionKeys,
            Set<String> consumedOptionKeys,
            Set<String> deprecatedOptionKeys) {
        final Set<String> remainingOptionKeys = new HashSet<>(allOptionKeys);
        remainingOptionKeys.removeAll(consumedOptionKeys);
        if (!remainingOptionKeys.isEmpty()) {
            throw new ValidationException(
                    String.format(
                            "Unsupported options found for '%s'.\n\n"
                            + "Unsupported options:\n\n"
                            + "%s\n\n"
                            + "Supported options:\n\n"
                            + "%s",
                            factoryIdentifier,
                            remainingOptionKeys.stream().sorted().collect(Collectors.joining("\n")),
                            consumedOptionKeys.stream()
                                    .map(k -> {
                                        if (deprecatedOptionKeys.contains(k)) {
                                            return String.format("%s (deprecated)", k);
                                        }
                                        return k;
                                    })
                                    .sorted()
                                    .collect(Collectors.joining("\n"))));
        }
    }

    /**
     * Validates unconsumed option keys.
     */
    public static void validateUnconsumedKeys(
            String factoryIdentifier, Set<String> allOptionKeys, Set<String> consumedOptionKeys) {
        validateUnconsumedKeys(
                factoryIdentifier, allOptionKeys, consumedOptionKeys, Collections.emptySet());
    }

    public static String stringifyOption(String key, String value) {
        if (isSensitive(key)) {
            value = HIDDEN_CONTENT;
        }
        return String.format("%s: %s", key, value);
    }

    private static <T> T readOption(ReadableConfig options, ConfigOption<T> option) {
        try {
            return options.get(option);
        } catch (Throwable t) {
            throw new ValidationException(
                    String.format("Invalid value for option '%s'.", option.key()), t);
        }
    }

    public static boolean isSensitive(@NonNull String key) {
        final String keyInLower = key.toLowerCase();
        for (String hideKey : SENSITIVE_KEYS) {
            if (keyInLower.length() >= hideKey.length() && keyInLower.contains(hideKey)) {
                return true;
            }
        }
        return false;
    }
}
