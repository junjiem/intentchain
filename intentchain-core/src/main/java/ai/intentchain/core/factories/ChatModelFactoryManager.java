package ai.intentchain.core.factories;

import java.util.Set;

public class ChatModelFactoryManager {
    private static final FactoryManager<ChatModelFactory> factoryManager;

    static {
        factoryManager = new FactoryManager<>(ChatModelFactory.class, "LLM (chat model)");
    }

    public static ChatModelFactory getFactory(String identifier) {
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
