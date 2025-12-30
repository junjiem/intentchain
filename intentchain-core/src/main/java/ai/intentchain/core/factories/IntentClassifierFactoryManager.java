package ai.intentchain.core.factories;

import java.util.Set;

public class IntentClassifierFactoryManager {
    private static final FactoryManager<IntentClassifierFactory> factoryManager;

    static {
        factoryManager = new FactoryManager<>(IntentClassifierFactory.class, "intent classifier");
    }

    public static IntentClassifierFactory getFactory(String identifier) {
        return factoryManager.getFactory(identifier);
    }

    public static String getDescription() {
        return factoryManager.getDescription();
    }

    public static Set<String> getSupports() {
        return factoryManager.getSupports();
    }

    public static boolean isSupported(String identifier) {
        return factoryManager.isSupported(identifier);
    }
}
