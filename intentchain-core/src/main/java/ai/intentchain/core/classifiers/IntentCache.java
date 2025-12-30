package ai.intentchain.core.classifiers;

import lombok.NonNull;

import java.util.List;

/**
 * Intent Cache
 */
public interface IntentCache {
    void set(@NonNull String key, @NonNull List<String> value);

    void del(@NonNull String key);
}
