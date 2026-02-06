package com.example.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequestCounter {
    private UUID ruleId;
    private String clientIp;
    private int requestCount;
    private LocalDateTime windowStart;
}
