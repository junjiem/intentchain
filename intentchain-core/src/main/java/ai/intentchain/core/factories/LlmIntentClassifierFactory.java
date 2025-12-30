package ai.intentchain.core.factories;

import ai.intentchain.core.classifiers.IntentClassifier;
import ai.intentchain.core.classifiers.LlmIntentClassifier;
import ai.intentchain.core.configuration.ConfigOption;
import ai.intentchain.core.configuration.ConfigOptions;
import ai.intentchain.core.configuration.ReadableConfig;
import ai.intentchain.core.utils.FactoryUtil;
import com.google.common.base.Preconditions;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 *
 */
public class LlmIntentClassifierFactory implements IntentClassifierFactory {

    public static final String IDENTIFIER = "llm";

    public static final ConfigOption<Map<String, String>> CATEGORIES =
            ConfigOptions.key("categories")
                    .mapType()
                    .noDefaultValue()
                    .withDescription("""
                            A map containing name and description of texts for each category.
                            Providing more detailed description per category generally improves classification accuracy.
                            For example:
                            ```
                            categories:
                              billing_and_payments: Billing and payments.
                              technical_support: Technical support.
                              account_management: Account management.
                              product_information: Product information.
                              order_status: Order status.
                              returns_and_exchanges: Returns and exchanges.
                              feedback_and_complaints: Feedback and complaints.
                            ```
                            """);

    public static final ConfigOption<List<String>> FALLBACK_CATEGORIES =
            ConfigOptions.key("fallback-categories")
                    .stringType()
                    .asList()
                    .noDefaultValue()
                    .withDescription("The fallback category names.");

    public static final ConfigOption<String> INSTRUCTION =
            ConfigOptions.key("instruction")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("Input additional instruction to help it better understand how to categorize intent.");

    @Override
    public String factoryIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public String factoryDescription() {
        return "Intent classifier using the LLM (Large Language Model).";
    }

    @Override
    public Set<ConfigOption<?>> requiredOptions() {
        return new LinkedHashSet<>(List.of(CATEGORIES));
    }

    @Override
    public Set<ConfigOption<?>> optionalOptions() {
        return new LinkedHashSet<>(List.of(FALLBACK_CATEGORIES, INSTRUCTION));
    }

    @Override
    public IntentClassifier create(@NonNull String name,
                                   @NonNull ReadableConfig config,
                                   EmbeddingModel embeddingModel,
                                   EmbeddingStore<TextSegment> embeddingStore,
                                   ScoringModel scoringModel,
                                   ChatModel chatModel) {
        FactoryUtil.validateFactoryOptions(this, config);
        validateConfigOptions(config, chatModel);

        Map<String, String> categories = config.get(CATEGORIES);
        LlmIntentClassifier.LlmIntentClassifierBuilder builder = LlmIntentClassifier.builder()
                .name(name)
                .chatModel(chatModel)
                .categories(categories.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getKey,
                                e -> StringUtils.isBlank(e.getValue()) ?
                                        "<no description>" : e.getValue())
                        )
                );

        config.getOptional(FALLBACK_CATEGORIES).ifPresent(builder::fallbackCategories);
        config.getOptional(INSTRUCTION).ifPresent(builder::instruction);

        return builder.build();
    }

    private void validateConfigOptions(ReadableConfig config, ChatModel chatModel) {
        Preconditions.checkArgument(chatModel != null, "llm has not been set yet");
    }
}
