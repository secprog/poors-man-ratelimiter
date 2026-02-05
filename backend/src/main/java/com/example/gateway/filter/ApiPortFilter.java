package com.example.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Filter that rejects /api/** requests on the main gateway server (port 8080).
 * API requests should only be handled by the admin server on port 9090.
 * 
 * This filter runs with order -2 (before RateLimitFilter at -1)
 * to fail fast and prevent any API processing on the gateway.
 */
@Component
@Slf4j
public class ApiPortFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        
        // Reject /poormansRateLimit/api/admin/** requests on the main gateway
        if (path.startsWith("/poormansRateLimit/api/admin/")) {
            log.debug("Rejecting admin API request on gateway port 8080: {}", path);
            log.debug("(This request should come to admin server on port 9090)");
            
            exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
            return exchange.getResponse().setComplete();
        }
        
        // Allow all other requests (gateway routes, public endpoints)
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // Run before AdminIpFilter (-2) and RateLimitFilter (-1) to fail fast
        return HIGHEST_PRECEDENCE;
    }
}
