package com.example.gateway.store;

import com.example.gateway.model.RateLimitPolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitPolicyStore {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private ReactiveHashOperations<String, String, String> hashOps() {
        return redisTemplate.opsForHash();
    }

    private ReactiveValueOperations<String, String> valueOps() {
        return redisTemplate.opsForValue();
    }

    public Flux<RateLimitPolicy> findAll() {
        return hashOps()
                .values(RedisKeys.RATE_LIMIT_POLICIES_HASH)
                .flatMap(this::deserializePolicy);
    }

    public Mono<RateLimitPolicy> findById(Long id) {
        return hashOps()
                .get(RedisKeys.RATE_LIMIT_POLICIES_HASH, String.valueOf(id))
                .flatMap(this::deserializePolicy);
    }

    public Mono<RateLimitPolicy> save(RateLimitPolicy policy) {
        Mono<Long> idSource = policy.getPolicyId() == null
                ? nextPolicyId()
                : Mono.just(policy.getPolicyId());

        return idSource.flatMap(id -> {
            policy.setPolicyId(id);
            if (policy.getCreatedAt() == null) {
                policy.setCreatedAt(Instant.now());
            }
            return serializePolicy(policy)
                    .flatMap(json -> hashOps()
                            .put(RedisKeys.RATE_LIMIT_POLICIES_HASH, String.valueOf(id), json)
                            .thenReturn(policy));
        });
    }

    public Mono<Void> deleteById(Long id) {
        return hashOps()
                .remove(RedisKeys.RATE_LIMIT_POLICIES_HASH, String.valueOf(id))
                .then();
    }

    private Mono<Long> nextPolicyId() {
        return valueOps().increment(RedisKeys.POLICY_ID_SEQUENCE);
    }

    private Mono<String> serializePolicy(RateLimitPolicy policy) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(policy));
    }

    private Mono<RateLimitPolicy> deserializePolicy(String json) {
        return Mono.fromCallable(() -> objectMapper.readValue(json, RateLimitPolicy.class))
                .onErrorResume(error -> {
                    log.warn("Failed to deserialize rate limit policy: {}", error.getMessage());
                    return Mono.empty();
                });
    }
}
