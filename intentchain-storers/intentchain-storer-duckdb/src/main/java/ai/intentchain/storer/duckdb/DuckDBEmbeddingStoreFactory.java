package ai.intentchain.storer.duckdb;

import ai.intentchain.core.configuration.ConfigOption;
import ai.intentchain.core.configuration.ConfigOptions;
import ai.intentchain.core.configuration.ReadableConfig;
import ai.intentchain.core.factories.EmbeddingStoreFactory;
import ai.intentchain.core.utils.FactoryUtil;
import dev.langchain4j.community.store.embedding.duckdb.DuckDBEmbeddingStore;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.util.Collections;
import java.util.Set;

public class DuckDBEmbeddingStoreFactory implements EmbeddingStoreFactory {

    public static final String IDENTIFIER = "duckdb";

    public static final String DEFAULT_TABLE_NAME_PREFIX = "ic_embeddings";

    public static final ConfigOption<String> FILE_PATH =
            ConfigOptions.key("file-path")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("""
                            DuckDB embedding store file path.
                            The project mode does not need to be filled in by default and is stored \
                            in the project root directory under the .intentchain directory, \
                            files with the prefix 'embeddings_'.
                            """);

    @Override
    public String factoryIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public Set<ConfigOption<?>> requiredOptions() {
        return Collections.emptySet();
    }

    @Override
    public Set<ConfigOption<?>> optionalOptions() {
        return Set.of(FILE_PATH);
    }

    @Override
    public Set<ConfigOption<?>> fingerprintOptions() {
        return Set.of(FILE_PATH);
    }

    @Override
    public EmbeddingStore<TextSegment> create(String storeId, ReadableConfig config) {
        FactoryUtil.validateFactoryOptions(this, config);
        String tableName = String.join("_", DEFAULT_TABLE_NAME_PREFIX, storeId.replace('-', '_'));
        DuckDBEmbeddingStore.Builder builder = DuckDBEmbeddingStore.builder()
                .tableName(tableName);
        config.getOptional(FILE_PATH).ifPresent(builder::filePath);
        return builder.build();
    }
}
