package com.example.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrafficLog {
    @Id
    private UUID id;
    private LocalDateTime timestamp;
    private String method;
    private String path;
    private String host;
    private String clientIp;
    private int statusCode;
    private boolean allowed;
    private boolean queued;
}
