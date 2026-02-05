package com.example.gateway.model;

import org.springframework.data.annotation.Id;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitPolicy {
    @Id
    private Long policyId;
    private String routePattern;
    private String limitType; // IP_BASED, USER_BASED, SESSION_BASED, API_KEY, GLOBAL
    private Integer replenishRate;
    private Integer burstCapacity;
    private Integer requestedTokens;
    private Instant createdAt;
    
    // Per-policy configuration overrides (optional)
    private String headerName;           // For USER_BASED, API_KEY, IP_BASED - e.g., "X-User-Id", "X-API-Key", "X-Forwarded-For"
    private String sessionCookieName;    // For SESSION_BASED - e.g., "JSESSIONID"
    private Boolean trustProxy;          // For IP_BASED - whether to trust X-Forwarded-For header
}
