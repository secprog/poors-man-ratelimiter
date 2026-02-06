package com.example.gateway.store;

import com.example.gateway.model.SystemConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class SystemConfigStore {

    private final ReactiveStringRedisTemplate redisTemplate;

    private ReactiveHashOperations<String, String, String> hashOps() {
        return redisTemplate.opsForHash();
    }

    public Flux<SystemConfig> findAll() {
        return hashOps()
                .entries(RedisKeys.SYSTEM_CONFIG_HASH)
                .map(entry -> new SystemConfig(entry.getKey(), entry.getValue()));
    }

    public Mono<SystemConfig> findById(String key) {
        return hashOps()
                .get(RedisKeys.SYSTEM_CONFIG_HASH, key)
                .map(value -> new SystemConfig(key, value));
    }

    public Mono<SystemConfig> save(SystemConfig config) {
        return hashOps()
                .put(RedisKeys.SYSTEM_CONFIG_HASH, config.getConfigKey(), config.getConfigValue())
                .thenReturn(config);
    }

    public Mono<Boolean> putIfAbsent(String key, String value) {
        return hashOps().putIfAbsent(RedisKeys.SYSTEM_CONFIG_HASH, key, value);
    }

    public Mono<Long> putDefaults(Map<String, String> defaults) {
        return Flux.fromIterable(defaults.entrySet())
                .flatMap(entry -> putIfAbsent(entry.getKey(), entry.getValue()))
                .count();
    }
}
