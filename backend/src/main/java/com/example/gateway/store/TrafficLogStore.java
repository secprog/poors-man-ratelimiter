package com.example.gateway.store;

import com.example.gateway.model.TrafficLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveListOperations;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrafficLogStore {

    private static final long MAX_LOG_ENTRIES = 10000;

    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private ReactiveListOperations<String, String> listOps() {
        return redisTemplate.opsForList();
    }

    public Mono<Void> append(TrafficLog logEntry) {
        return serializeLog(logEntry)
                .flatMap(json -> listOps().leftPush(RedisKeys.TRAFFIC_LOG_LIST, json))
                .flatMap(count -> listOps().trim(RedisKeys.TRAFFIC_LOG_LIST, 0, MAX_LOG_ENTRIES - 1))
                .then()
                .onErrorResume(error -> {
                    log.warn("Failed to append traffic log: {}", error.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<String> serializeLog(TrafficLog logEntry) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(logEntry));
    }
}
