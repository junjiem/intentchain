package ai.intentchain.classifier.redis;

import ai.intentchain.core.classifiers.IntentCache;
import ai.intentchain.core.classifiers.IntentClassifier;
import ai.intentchain.core.classifiers.data.Intent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClientConfig;
import redis.clients.jedis.JedisPooled;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Intent classifier using the redis cache
 */
@Slf4j
public class RedisIntentClassifier implements IntentClassifier, IntentCache {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final String name;

    private final JedisPooled client;
    private final String prefix;
    private final Integer maxTextLength;

    @Builder
    public RedisIntentClassifier(@NonNull String name,
                                 String uri, String host, Integer port,
                                 String user, String password,
                                 JedisPooled jedisPooled,
                                 JedisClientConfig clientConfig,
                                 String prefix,
                                 Integer maxTextLength) {
        this.name = name;
        if (uri != null) {
            this.client = new JedisPooled(uri);
        } else {
            JedisClientConfig actualConfig = Optional.ofNullable(clientConfig)
                    .orElse(DefaultJedisClientConfig.builder()
                            .user(user)
                            .password(password)
                            .build());
            this.client = Optional.ofNullable(jedisPooled)
                    .orElse(new JedisPooled(new HostAndPort(host, port), actualConfig));
        }
        this.prefix = Optional.ofNullable(prefix).orElse("intentchain:");
        this.maxTextLength = Optional.ofNullable(maxTextLength).orElse(128);
    }

    @Override
    public String classifierName() {
        return name;
    }

    @Override
    public List<Intent> classify(@NonNull String text) {
        log.debug("Redis - Start get cache content.");
        List<String> labels = client.lrange(prefix + text, 0, -1);
        if (labels == null || labels.isEmpty()) {
            log.debug("Redis - Cache miss fallback.");
            return Collections.emptyList();
        }
        List<Intent> intents = labels.stream().map(Intent::from).toList();
        try {
            log.debug("Redis - Return the intents: " + JSON_MAPPER.writeValueAsString(intents));
        } catch (JsonProcessingException e) {
            //
        }
        return intents;
    }

    @Override
    public void set(@NonNull String key, @NonNull List<String> value) {
        log.debug("Redis - Start set the cache.");
        if (key.length() > maxTextLength) {
            log.debug("Redis - The key length is greater than the " + maxTextLength + ", not be write to the cache.");
            return;
        }
        log.debug("Redis - Set key: " + key + ", and value: [" + String.join(",", value) + "]");
        String keyStr = prefix + key;
        client.del(keyStr);
        client.lpush(keyStr, value.toArray(new String[0]));
        log.debug("Redis - The cache has been completed.");
    }

    @Override
    public void del(@NonNull String key) {
        log.debug("Redis - Start delete cache the key: " + key);
        client.del(prefix + key);
        log.debug("Redis - The cache has been deleted.");
    }
}
