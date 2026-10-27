package io.github.filipopov.gateway.application.feature;

import java.util.Arrays;
import java.util.Optional;

/** Capabilities that can be switched on or off per tenant without a code change. */
public enum Feature {
    TRANSFERS("transfers"),
    TRANSACTION_SEARCH("transaction-search");

    private final String key;

    Feature(String key) {
        this.key = key;
    }

    /** The key used in configuration and in problem responses. */
    public String key() {
        return key;
    }

    public static Optional<Feature> fromKey(String key) {
        return Arrays.stream(values()).filter(feature -> feature.key.equals(key)).findFirst();
    }
}
