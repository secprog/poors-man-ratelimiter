package com.example.gateway.controller;

import com.example.gateway.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    @GetMapping("/summary")
    public Mono<AnalyticsService.StatsSummary> getSummary() {
        return analyticsService.getSummary();
    }
    
    @GetMapping("/timeseries")
    public Mono<List<AnalyticsService.TimeSeriesDataPoint>> getTimeSeries(
            @RequestParam(defaultValue = "24") int hours) {
        return analyticsService.getTimeSeries(hours);
    }
}
