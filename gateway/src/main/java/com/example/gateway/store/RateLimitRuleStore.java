package com.example.gateway.store;

import com.example.gateway.model.RateLimitRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitRuleStore {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private ReactiveHashOperations<String, String, String> hashOps() {
        return redisTemplate.opsForHash();
    }

    public Flux<RateLimitRule> findAll() {
        return hashOps()
                .values(RedisKeys.RATE_LIMIT_RULES_HASH)
                .flatMap(this::deserializeRule);
    }

    public Flux<RateLimitRule> findByActiveTrue() {
        return findAll().filter(RateLimitRule::isActive);
    }

    public Mono<RateLimitRule> findById(UUID id) {
        return hashOps()
                .get(RedisKeys.RATE_LIMIT_RULES_HASH, id.toString())
                .flatMap(this::deserializeRule);
    }

    public Mono<RateLimitRule> save(RateLimitRule rule) {
        if (rule.getId() == null) {
            return Mono.error(new IllegalArgumentException("Rate limit rule id must be set"));
        }
        return serializeRule(rule)
                .flatMap(json -> hashOps()
                        .put(RedisKeys.RATE_LIMIT_RULES_HASH, rule.getId().toString(), json)
                        .thenReturn(rule));
    }

    public Mono<Void> deleteById(UUID id) {
        return hashOps()
                .remove(RedisKeys.RATE_LIMIT_RULES_HASH, id.toString())
                .then();
    }

    private Mono<String> serializeRule(RateLimitRule rule) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(rule));
    }

    private Mono<RateLimitRule> deserializeRule(String json) {
        return Mono.fromCallable(() -> objectMapper.readValue(json, RateLimitRule.class))
                .onErrorResume(error -> {
                    log.warn("Failed to deserialize rate limit rule: {}", error.getMessage());
                    return Mono.empty();
                });
    }
}
