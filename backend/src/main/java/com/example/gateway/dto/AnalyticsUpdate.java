package com.example.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonProperty;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsUpdate {
    @JsonProperty("requestsAllowed")
    private long requestsAllowed;
    
    @JsonProperty("requestsBlocked")
    private long requestsBlocked;
    
    @JsonProperty("activePolicies")
    private long activePolicies;
    
    @JsonProperty("timestamp")
    private long timestamp;
}
