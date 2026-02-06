# Feature-by-Feature Check

1) Route-tiered policies
Have: Per-route rules with `pathPattern`, `methods`, `hosts`, `priority`, and per-rule `allowedRequests`/`windowSeconds`. Multiple rules can match; global rules (`/**`) are supported. `gateway/src/main/java/com/example/gateway/model/RateLimitRule.java`, `gateway/src/main/java/com/example/gateway/service/RateLimiterService.java`, `README.md`, `CODEBASE_OVERVIEW.md`
Needed: Actual tiered rule definitions for those endpoint classes (search/listing/etc). The system supports it, but no built-in “tier presets” or bundled rules.

2) Concurrency limits (max in-flight / max connections per client)
Have: None found.
Needed: Explicit per-identifier in-flight limits and/or per-client connection caps. No tracking for concurrent in-flight requests or connections exists in gateway or config. `gateway/src/main/java/com/example/gateway/service/RateLimiterService.java`, `gateway/src/main/java/com/example/gateway/filter/RateLimitFilter.java`

3) Better keys than IP
Have: Header, cookie, body, and JWT-based identifiers with priority order `Header > Cookie > Body > JWT > IP`, plus `combine_with_ip` options and IP fallback. `gateway/src/main/java/com/example/gateway/service/RateLimiterService.java`, `JWT_RATE_LIMITING.md`, `BODY_BASED_RATE_LIMITING.md`, `UPDATE_HEADER_COOKIE_SUPPORT.md`
Needed: Client cert extraction (mTLS) is not present. API key/session id are supported if they’re exposed via header/cookie/body/JWT, but there’s no dedicated client-cert path.

4) Delay/queue mode, not only 429
Have: Leaky-bucket queueing with per-rule `maxQueueSize` and `delayPerRequestMs`. Queue is per rule+identifier and returns 429 when full. `QUEUEING_IMPLEMENTATION.md`, `gateway/src/main/java/com/example/gateway/service/RateLimiterService.java`, `gateway/src/main/java/com/example/gateway/filter/RateLimitFilter.java`
Needed: Explicit max-wait-time config and `Retry-After` header on 429s. Queue depth is in-memory per gateway instance (not shared across instances). There’s no “bounded wait time” separate from `maxQueueSize * delayPerRequestMs`.

5) Adaptive throttling + penalty box
Have: No adaptive scoring/penalty system. Anti-bot features exist (honeypot, time-to-submit, tokens) but they don’t dynamically adjust rate limits based on behavior. `README.md`, `CODEBASE_OVERVIEW.md`, `gateway/src/main/java/com/example/gateway/filter/AntiBotFilter.java`
Needed: Behavior scoring, dynamic rate reduction, and temporary bans.

6) DC stickiness (multi-DC)
Have: No data-center–aware routing or stickiness controls. The design assumes a shared Redis and a single gateway deployment. `README.md`, `CODEBASE_OVERVIEW.md`, `docker-compose.yml`
Needed: Consistent hashing / session affinity at the LB layer or per-request DC pinning guidance and/or a split-budget strategy.

7) Fast, predictable hot path
Have: Reactive gateway, in-memory rule list, CAS-based queue tracking. `gateway/src/main/java/com/example/gateway/service/RateLimiterService.java`, `QUEUEING_IMPLEMENTATION.md`
Needed: Hard timeouts, explicit fail-open/closed policy per route class, and constant-time decision guarantees. Rule evaluation is O(number of matched rules) and depends on Redis read/write for counters. No decision latency metrics are recorded.

8) Observability + controls
Have: Allowed/blocked counters, time-series analytics, traffic logs (path/method/status/allowed/queued), and a WebSocket live dashboard. `gateway/src/main/java/com/example/gateway/service/AnalyticsService.java`, `backend/src/main/java/com/example/admin/service/AnalyticsService.java`, `gateway/src/main/java/com/example/gateway/store/TrafficLogStore.java`, `gateway/src/main/java/com/example/gateway/model/TrafficLog.java`
Needed: Per-route hit rates, top keys by blocks/delays, false-positive indicators, and “why blocked” reason codes. Queue depth and decision latency aren’t surfaced.
