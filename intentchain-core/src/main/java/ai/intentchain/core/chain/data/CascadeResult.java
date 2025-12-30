package ai.intentchain.core.chain.data;

import ai.intentchain.core.classifiers.data.Intent;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NonNull;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 *
 */
@Getter
public class CascadeResult {
    @NonNull
    private final String traceId;

    @NonNull
    private final String content;

    @NonNull
    private List<Intent> intents = Collections.emptyList();

    @NonNull
    private final List<String> cascadePath;

    @NonNull
    private final Duration duration;

    @JsonProperty("duration")
    public long getDurationMillis() {
        return duration.toMillis();
    }

    public CascadeResult(@NonNull String traceId,
                         @NonNull String content,
                         @NonNull List<String> cascadePath,
                         @NonNull Instant start) {
        this.traceId = traceId;
        this.content = content;
        this.cascadePath = cascadePath;
        this.duration = Duration.between(start, Instant.now());
    }

    public CascadeResult(@NonNull String traceId,
                         @NonNull String content,
                         @NonNull List<Intent> intents,
                         @NonNull List<String> cascadePath,
                         @NonNull Instant start) {
        this(traceId, content, cascadePath, start);
        this.intents = intents;
    }
}
