package ai.intentchain.core.factories;

import ai.intentchain.core.configuration.ConfigOption;

import java.util.Set;

public interface Factory {
    String factoryIdentifier();

    Set<ConfigOption<?>> requiredOptions();

    Set<ConfigOption<?>> optionalOptions();
}
