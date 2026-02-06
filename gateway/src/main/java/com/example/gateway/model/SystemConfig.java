package com.example.gateway.model;

import org.springframework.data.annotation.Id;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfig {
    @Id
    private String configKey;
    private String configValue;
}
