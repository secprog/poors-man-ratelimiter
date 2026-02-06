package com.example.gateway.store;

import com.example.gateway.model.RequestCounter;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RequestCounterStore {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private ReactiveValueOperations<String, String> valueOps() {
        return redisTemplate.opsForValue();
    }

    public Mono<RequestCounter> findByRuleIdAndClientIp(UUID ruleId, String clientIp) {
        String key = RedisKeys.requestCounterKey(ruleId, clientIp);
        return valueOps().get(key)
                .flatMap(this::deserializeCounter);
    }

    public Mono<Void> save(RequestCounter counter, Duration ttl) {
        String key = RedisKeys.requestCounterKey(counter.getRuleId(), counter.getClientIp());
        return serializeCounter(counter)
                .flatMap(json -> valueOps().set(key, json, ttl))
                .then();
    }

    public Mono<Long> deleteByRuleId(UUID ruleId) {
        String pattern = RedisKeys.requestCounterPatternForRule(ruleId);
        return redisTemplate.keys(pattern)
                .collectList()
                .flatMap(keys -> keys.isEmpty()
                        ? Mono.just(0L)
                        : redisTemplate.delete(reactor.core.publisher.Flux.fromIterable(keys)));
    }

    private Mono<String> serializeCounter(RequestCounter counter) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(counter));
    }

    private Mono<RequestCounter> deserializeCounter(String json) {
        return Mono.fromCallable(() -> objectMapper.readValue(json, RequestCounter.class))
                .onErrorResume(error -> {
                    log.warn("Failed to deserialize request counter: {}", error.getMessage());
                    return Mono.empty();
                });
    }
}
