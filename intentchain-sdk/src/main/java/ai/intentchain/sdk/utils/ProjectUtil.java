package ai.intentchain.sdk.utils;

import ai.intentchain.core.chain.CascadeIntentChain;
import ai.intentchain.core.classifiers.IntentClassifier;
import ai.intentchain.core.classifiers.data.TextLabel;
import ai.intentchain.core.configuration.ConfigOption;
import ai.intentchain.core.configuration.ConfigOptions;
import ai.intentchain.core.configuration.ReadableConfig;
import ai.intentchain.core.exception.ValidationException;
import ai.intentchain.core.factories.*;
import ai.intentchain.core.utils.FactoryUtil;
import ai.intentchain.core.utils.JinjaTemplateUtil;
import ai.intentchain.core.utils.YamlTemplateUtil;
import ai.intentchain.sdk.data.project.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.common.base.Preconditions;
import com.networknt.schema.Error;
import com.networknt.schema.*;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.Getter;
import lombok.NonNull;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

public class ProjectUtil {

    public final static String PROJECT_CONFIG_FILE_NAME_PREFIX = "project";
    public final static String PROJECT_CONFIG_FILE_NAME_YAML = PROJECT_CONFIG_FILE_NAME_PREFIX + ".yaml";
    public final static String PROJECT_CONFIG_FILE_NAME_YML = PROJECT_CONFIG_FILE_NAME_PREFIX + ".yml";

    public final static Set<String> YAML_EXTENSIONS = Set.of(".yaml", ".yml");
    public final static Set<String> CSV_EXTENSIONS = Set.of(".csv");

    public final static String DUCKDB_EMBEDDING_STORE_FILE_PREFIX = "embeddings_";
    public final static String MODELS_DIR_NAME = "models";
    public final static String INTENTCHAIN_DIR_NAME = ".intentchain";

    private static final YAMLMapper YAML_MAPPER = new YAMLMapper();
    private static final JsonMapper JSON_MAPPER = new JsonMapper();

    private static final SchemaRegistryConfig SCHEMA_CONFIG =
            SchemaRegistryConfig.builder().locale(Locale.ENGLISH).build();
    private static final SchemaRegistry SCHEMA_REGISTRY = SchemaRegistry
            .withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
                    builder -> builder.schemaRegistryConfig(SCHEMA_CONFIG));
    private static final String SCHEMA_PATH = "schemas/project_schema.json";
    private static final String TEMPLATE_PATH = "templates/project_yaml_template.jinja";

    private static final Schema SCHEMA;
    private static final String TEMPLATE;

    static {
        try {
            SCHEMA = loadProjectSchema();
        } catch (IOException e) {
            throw new ExceptionInInitializerError("Failed to load project schema file: " + e.getMessage());
        }
        TEMPLATE = loadText();
    }

    private static Schema loadProjectSchema() throws IOException {
        try (InputStream stream = ProjectUtil.class.getClassLoader().getResourceAsStream(SCHEMA_PATH)) {
            if (stream == null) {
                throw new IOException("Project schema file not found in classpath: " + SCHEMA_PATH);
            }
            try {
                JsonNode schemaNode = JSON_MAPPER.readTree(stream);
                return SCHEMA_REGISTRY.getSchema(schemaNode);
            } catch (IOException e) {
                throw new IOException("Failed to parse project schema file: " + SCHEMA_PATH
                                      + " - " + e.getMessage(), e);
            }
        }
    }

    private static String loadText() {
        try (InputStream inputStream = ProjectUtil.class.getClassLoader().getResourceAsStream(TEMPLATE_PATH)) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load text from resources: " + TEMPLATE_PATH, e);
        }
    }

    private ProjectUtil() {
    }

    private static final ConfigOption<Boolean> SELF_LEARNING =
            ConfigOptions.key("self-learning")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription("Enable online self-learning");

    private static final ConfigOption<Double> SELF_LEARNING_THRESHOLD =
            ConfigOptions.key("self-learning-threshold")
                    .doubleType()
                    .defaultValue(0.95)
                    .withDescription("Self-learning confidence threshold, must be between 0.0 and 1.0");

    public static final ConfigOption<List<String>> SELF_LEARNING_EXCLUDES =
            ConfigOptions.key("self-learning-excludes")
                    .stringType()
                    .asList()
                    .defaultValues("other")
                    .withDescription("List of intentions for self-learning exclusion");

    private static Set<ConfigOption<?>> requiredOptions() {
        return Collections.emptySet();
    }

    private static Set<ConfigOption<?>> optionalOptions() {
        return new LinkedHashSet<>(List.of(SELF_LEARNING, SELF_LEARNING_THRESHOLD, SELF_LEARNING_EXCLUDES));
    }

    public static Set<ConfigOption<?>> fingerprintOptions() {
        return Collections.emptySet();
    }

    public static Map<String, String> fingerprintConfigs(@NonNull ReadableConfig config) {
        List<String> keys = fingerprintOptions().stream()
                .map(ConfigOption::key)
                .toList();
        return config.toMap().entrySet().stream()
                .filter(e -> keys.contains(e.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public static List<Error> validate(@NonNull String yamlContent) throws IOException {
        Preconditions.checkArgument(!yamlContent.isEmpty(), "yamlContent cannot be empty");
        try {
            JsonNode jsonNode = YAML_MAPPER.readTree(yamlContent);
            return SCHEMA.validate(jsonNode);
        } catch (IOException e) {
            throw new IOException("Failed to parse YAML content: " + e.getMessage(), e);
        }
    }

    public static Project project(@NonNull String yamlContent) throws IOException {
        List<Error> errors = ProjectUtil.validate(yamlContent);
        if (!errors.isEmpty()) {
            throw new ValidationException("The YAML verification not pass: \n" + errors);
        }
        return YAML_MAPPER.readValue(yamlContent, Project.class);
    }

    public static String yamlTemplate() {
        List<SingleItemTemplate> llms = ChatModelFactoryManager.getSupports().stream()
                .map(identifier -> {
                    ChatModelFactory factory = ChatModelFactoryManager.getFactory(identifier);
                    return new SingleItemTemplate(identifier, false, getConfiguration(factory));
                })
                .sorted((o1, o2) -> Boolean.compare(o2.display, o1.display))
                .collect(Collectors.toList());

        List<SingleItemTemplate> embeddings = EmbeddingModelFactoryManager.getSupports().stream()
                .map(identifier -> {
                    EmbeddingModelFactory factory = EmbeddingModelFactoryManager.getFactory(identifier);
                    boolean display = EmbeddingConfig.DEFAULT_PROVIDER.equals(identifier);
                    return new SingleItemTemplate(identifier, display, getConfiguration(factory));
                })
                .sorted((o1, o2) -> Boolean.compare(o2.display, o1.display))
                .collect(Collectors.toList());

        List<SingleItemTemplate> embeddingStores = EmbeddingStoreFactoryManager.getSupports().stream()
                .map(identifier -> {
                    EmbeddingStoreFactory factory = EmbeddingStoreFactoryManager.getFactory(identifier);
                    boolean display = EmbeddingStoreConfig.DEFAULT_PROVIDER.equals(identifier);
                    return new SingleItemTemplate(identifier, display, getConfiguration(factory));
                })
                .sorted((o1, o2) -> Boolean.compare(o2.display, o1.display))
                .collect(Collectors.toList());

        List<SingleItemTemplate> rerankings = ScoringModelFactoryManager.getSupports().stream()
                .map(identifier -> {
                    ScoringModelFactory factory = ScoringModelFactoryManager.getFactory(identifier);
                    boolean display = RerankingConfig.DEFAULT_PROVIDER.equals(identifier);
                    return new SingleItemTemplate(identifier, display, getConfiguration(factory));
                })
                .sorted((o1, o2) -> Boolean.compare(o2.display, o1.display))
                .collect(Collectors.toList());

        List<MultipleItemContainCommentTemplate> classifiers = IntentClassifierFactoryManager.getSupports().stream()
                .map(identifier -> {
                    IntentClassifierFactory factory = IntentClassifierFactoryManager.getFactory(identifier);
                    boolean display = ClassifierConfig.DEFAULT_PROVIDER.equals(identifier);
                    String name = display ? ClassifierConfig.DEFAULT_NAME : identifier;
                    return new MultipleItemContainCommentTemplate(factory.factoryDescription(), name,
                            identifier, display, getConfiguration(factory));
                })
                .sorted((o1, o2) -> Boolean.compare(o2.display, o1.display))
                .collect(Collectors.toList());

        Map<String, Object> variables = new HashMap<>();
        variables.put("project_configuration", getProjectConfiguration());
        variables.put("llms", llms);
        variables.put("embeddings", embeddings);
        variables.put("embedding_stores", embeddingStores);
        variables.put("rerankings", rerankings);
        variables.put("classifiers", classifiers);

        return JinjaTemplateUtil.render(TEMPLATE, variables);
    }

    public static String getProjectConfiguration() {
        return YamlTemplateUtil.getConfiguration(requiredOptions(), optionalOptions());
    }

    public static String getDefaultIntentClassifierConfiguration() {
        return getConfiguration(new DefaultIntentClassifierFactory());
    }

    private static String getConfiguration(Factory factory) {
        return YamlTemplateUtil.getConfiguration(factory);
    }

    private record SingleItemTemplate(@Getter String provider, @Getter boolean display,
                                      @Getter String configuration) {
    }

    private record MultipleItemTemplate(@Getter String name, @Getter String provider, @Getter boolean display,
                                        @Getter String configuration) {
    }

    private record MultipleItemContainCommentTemplate(@Getter String comment, @Getter String name,
                                                      @Getter String provider, @Getter boolean display,
                                                      @Getter String configuration) {
    }

    public static CascadeIntentChain createIntentChain(@NonNull Project project, @NonNull Path projectPath) {
        ReadableConfig config = project.getConfiguration();
        LinkedHashMap<String, IntentClassifier> classifiers = createClassifiers(project, projectPath);

        FactoryUtil.validateFactoryOptions(requiredOptions(), optionalOptions(), config);
        validateConfigOptions(config);

        CascadeIntentChain.CascadeIntentChainBuilder builder = CascadeIntentChain.builder()
                .classifiers(classifiers);
        config.getOptional(SELF_LEARNING).ifPresent(builder::selfLearning);
        config.getOptional(SELF_LEARNING_THRESHOLD).ifPresent(builder::selfLearningThreshold);
        config.getOptional(SELF_LEARNING_EXCLUDES).ifPresent(builder::selfLearningExcludes);
        return builder.build();
    }

    private static void validateConfigOptions(ReadableConfig config) {
        Double selfLearningThreshold = config.get(SELF_LEARNING_THRESHOLD);
        Preconditions.checkArgument(selfLearningThreshold >= 0.0 && selfLearningThreshold <= 1.0,
                "'" + SELF_LEARNING_THRESHOLD.key() + "' value must be between 0.0 and 1.0");
    }

    public static LinkedHashMap<String, IntentClassifier> createClassifiers(@NonNull Project project,
                                                                            @NonNull Path projectPath) {
        Map<String, ClassifierConfig> classifierConfigs = project.getClassifiers().stream()
                .collect(Collectors.toMap(ClassifierConfig::getName, o -> o));
        project.getChain().forEach(n -> Preconditions.checkArgument(classifierConfigs.containsKey(n),
                "The project doesn't exist classifier: " + n));

        EmbeddingModel embeddingModel = null;
        EmbeddingConfig embeddingConfig = project.getEmbedding();
        if (embeddingConfig != null) {
            embeddingModel = FactoryUtil.createEmbeddingModel(
                    embeddingConfig.getProvider(),
                    embeddingConfig.getConfiguration()
            );
        }

        ScoringModel scoringModel = null;
        RerankingConfig rerankingConfig = project.getReranking();
        if (rerankingConfig != null) {
            scoringModel = FactoryUtil.createScoringModel(
                    rerankingConfig.getProvider(),
                    rerankingConfig.getConfiguration()
            );
        }

        ChatModel chatModel = null;
        LlmConfig llmConfig = project.getLlm();
        if (llmConfig != null) {
            chatModel = FactoryUtil.createChatModel(
                    llmConfig.getProvider(),
                    llmConfig.getConfiguration()
            );
        }

        EmbeddingStoreConfig embeddingStoreConfig = project.getEmbeddingStore();
        if (embeddingStoreConfig != null) {
            adjustEmbeddingStoreConfig(project, projectPath); // 调整Embedding存储配置
        }

        LinkedHashMap<String, IntentClassifier> classifiers = new LinkedHashMap<>();
        for (String classifierName : project.getChain()) {
            ClassifierConfig classifierConfig = classifierConfigs.get(classifierName);
            String classifierFingerprint = contentClassifierFingerprint(project, classifierConfig);
            EmbeddingStore<TextSegment> embeddingStore = null;
            if (embeddingStoreConfig != null) {
                embeddingStore = FactoryUtil.createEmbeddingStore(
                        classifierFingerprint,
                        embeddingStoreConfig.getProvider(),
                        embeddingStoreConfig.getConfiguration()
                );
            }
            IntentClassifier classifier = FactoryUtil.createIntentClassifier(classifierName,
                    classifierConfig.getProvider(), classifierConfig.getConfiguration(),
                    embeddingModel, embeddingStore, scoringModel, chatModel);
            classifiers.put(classifierFingerprint, classifier);
        }

        return classifiers;
    }

    private static String contentClassifierFingerprint(@NonNull Project project,
                                                       ClassifierConfig classifierConfig) {
        try {
            String configStr = String.format("project:name=%s;" +
                                             "project:configuration=%s;",
                    project.getName(),
                    JSON_MAPPER.writeValueAsString(fingerprintConfigs(project.getConfiguration()))
            );
            if (classifierConfig != null) {
                IntentClassifierFactory factory = IntentClassifierFactoryManager
                        .getFactory(classifierConfig.getProvider());
                Map<String, String> fingerprintConfigs = factory
                        .fingerprintConfigs(classifierConfig.getConfiguration());
                configStr += String.format("classifier:name=%s;" +
                                           "classifier:provider=%s;" +
                                           "classifier:configuration=%s;",
                        classifierConfig.getName(),
                        classifierConfig.getProvider(),
                        JSON_MAPPER.writeValueAsString(fingerprintConfigs)
                );
            }
            return DigestUtils.md5Hex(configStr);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Calculate the classifier fingerprint failed", e);
        }
    }

    private static void adjustEmbeddingStoreConfig(@NonNull Project project, @NonNull Path projectPath) {
        EmbeddingStoreConfig embeddingStore = project.getEmbeddingStore();
        if (EmbeddingStoreConfig.DUCKDB_PROVIDER.equals(embeddingStore.getProvider())
            && embeddingStore.getConfiguration().getOptional(EmbeddingStoreConfig.DUCKDB_FILE_PATH).isEmpty()) {
            Path datDirPath = projectPath.resolve(INTENTCHAIN_DIR_NAME);
            if (!Files.exists(datDirPath)) {
                try {
                    Files.createDirectories(datDirPath);
                } catch (IOException e) {
                    throw new RuntimeException("The creation of the " + INTENTCHAIN_DIR_NAME
                                               + " directory under the project root directory failed", e);
                }
            }
            String storeFileName = DUCKDB_EMBEDDING_STORE_FILE_PREFIX + storeFingerprint(project);
            Path filePath = projectPath.resolve(INTENTCHAIN_DIR_NAME + File.separator + storeFileName);
            embeddingStore.setConfiguration(
                    Map.of(EmbeddingStoreConfig.DUCKDB_FILE_PATH.key(), filePath.toAbsolutePath().toString())
            );
        }
    }

    public static String storeFingerprint(@NonNull Project project) {
        EmbeddingConfig embedding = project.getEmbedding();
        EmbeddingStoreConfig embeddingStore = project.getEmbeddingStore();
        try {
            String configStr = String.format("project:name=%s;" +
                                             "project:configuration=%s;",
                    project.getName(),
                    JSON_MAPPER.writeValueAsString(fingerprintConfigs(project.getConfiguration()))
            );
            if (embedding != null) {
                EmbeddingModelFactory embeddingModelFactory = EmbeddingModelFactoryManager
                        .getFactory(embedding.getProvider());
                Map<String, String> embeddingModelFingerprintConfigs = embeddingModelFactory
                        .fingerprintConfigs(embedding.getConfiguration());
                configStr += String.format("embedding:provider=%s;" +
                                           "embedding:configuration=%s;",
                        embedding.getProvider(),
                        JSON_MAPPER.writeValueAsString(embeddingModelFingerprintConfigs)
                );
            }
            if (embeddingStore != null) {
                EmbeddingStoreFactory embeddingStoreFactory = EmbeddingStoreFactoryManager
                        .getFactory(embeddingStore.getProvider());
                Map<String, String> embeddingStoreFingerprintConfigs = embeddingStoreFactory
                        .fingerprintConfigs(embeddingStore.getConfiguration());
                configStr += String.format("embeddingStore:provider=%s;" +
                                           "embeddingStore:configuration=%s;",
                        embeddingStore.getProvider(),
                        JSON_MAPPER.writeValueAsString(embeddingStoreFingerprintConfigs)
                );
            }
            return DigestUtils.md5Hex(configStr);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Calculate the store fingerprint failed", e);
        }
    }

    public static Project loadProject(@NonNull Path projectPath) {
        Path filePath = findProjectConfigFile(projectPath);
        if (filePath == null) {
            throw new RuntimeException("The project configuration file not found "
                                       + PROJECT_CONFIG_FILE_NAME_YAML + " or " + PROJECT_CONFIG_FILE_NAME_YML
                                       + ", please ensure that the project configuration file exists in the project root directory.");
        }
        try {
            return project(Files.readString(filePath));
        } catch (Exception e) {
            throw new RuntimeException("The " + projectPath.relativize(filePath)
                                       + " YAML file content does not meet the requirements: \n" + e.getMessage(), e);
        }
    }

    private static Path findProjectConfigFile(@NonNull Path projectPath) {
        Path projectYaml = projectPath.resolve(PROJECT_CONFIG_FILE_NAME_YAML);
        Path projectYml = projectPath.resolve(PROJECT_CONFIG_FILE_NAME_YML);
        if (Files.exists(projectYaml)) {
            return projectYaml;
        } else if (Files.exists(projectYml)) {
            return projectYml;
        }
        return null;
    }

    public static List<TextLabel> loadTextLabels(@NonNull Path filePath, @NonNull Path dirPath) {
        List<TextLabel> trainingData = new ArrayList<>();
        try (Reader reader = new FileReader(filePath.toFile());
             CSVParser csvParser = CSVParser.parse(reader, CSVFormat.DEFAULT)) {  // 不使用表头
            int i = 0;
            for (CSVRecord record : csvParser) {
                Preconditions.checkArgument(record.size() == 2,
                        "The data format in line " + (i + 1) + " of the "
                        + dirPath.relativize(filePath) + " CSV file is incorrect");
                String text = record.get(0).trim();
                String label = record.get(1).trim();
                trainingData.add(new TextLabel(text, label));
                i++;
            }
        } catch (IOException e) {
            throw new RuntimeException("The " + dirPath.relativize(filePath)
                                       + " CSV file content read failed: \n" + e.getMessage(), e);
        }
        return trainingData;
    }

    public static List<Path> scanCsvFiles(@NonNull Path dirPath) {
        List<Path> files = new ArrayList<>();
        Preconditions.checkArgument(Files.exists(dirPath),
                "There is no '" + MODELS_DIR_NAME + "' directory in the project root directory");
        try {
            Files.walkFileTree(dirPath, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String fileName = file.getFileName().toString();
                    if (isCsvFile(fileName)) { // 检查是否为CSV文件
                        files.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("The scan for the CSV file in the '" + MODELS_DIR_NAME + "' directory failed", e);
        }
        return files;
    }

    private static boolean isCsvFile(@NonNull String fileName) {
        return CSV_EXTENSIONS.stream().anyMatch(fileName::endsWith);
    }
}