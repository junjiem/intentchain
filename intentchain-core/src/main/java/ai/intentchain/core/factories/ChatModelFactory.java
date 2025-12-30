package ai.intentchain.core.factories;

import ai.intentchain.core.configuration.ReadableConfig;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;

public interface ChatModelFactory extends Factory {
    ChatModel create(ReadableConfig config);
}
