package ai.intentchain.sdk.data.project;

import ai.intentchain.core.configuration.Configuration;
import ai.intentchain.core.configuration.ReadableConfig;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

import java.util.Map;

@Setter
@Getter
public class ClassifierConfig {

    public static final String DEFAULT_NAME = "default";
    public static final String DEFAULT_PROVIDER = "default";

    @NonNull
    private String name = DEFAULT_NAME;

    private String description;

    @NonNull
    private String provider = DEFAULT_PROVIDER;

    @JsonIgnore
    @NonNull
    private ReadableConfig configuration = new Configuration();

    @JsonProperty("configuration")
    public void setConfiguration(Map<String, Object> configs) {
        this.configuration = Configuration.fromMap(configs);
    }
}
