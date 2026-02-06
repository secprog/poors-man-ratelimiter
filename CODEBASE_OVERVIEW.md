# Rate Limiter Gateway — Codebase Overview

## Architecture: Microservices with Redis as Single Source of Truth

**Purpose:** API gateway with configurable rate limiting, anti-bot defenses, real-time analytics, and admin UI.

**Design Pattern:** Microservices architecture with clear separation of concerns:
- **Gateway Service** (port 8080): Public-facing, handles rate limiting and anti-bot protection
- **Admin Service** (port 9090): Isolated, localhost-only, provides admin APIs and analytics
- **Redis**: Central state store - services communicate **only** through Redis (no direct HTTP between services)
- **Frontend**: React UI served via Nginx, proxies to both services

## Service Breakdown

### Gateway Service (`/gateway/`)
**Stack:** Spring Cloud Gateway 3, WebFlux (reactive), Caffeine caches, Java 21  
**Port:** 8080 (public)  
**Responsibilities:**
- Enforce rate limits (token bucket + leaky bucket)
- Apply anti-bot protections (honeypot, timing, tokens)
- Route requests to upstream services
- Write request counters and traffic logs to Redis
- Read rules and config from Redis

**Key Components:**
- `filter/RateLimitFilter.java`: Token bucket + leaky bucket enforcement with `Mono.delay()` for queueing
- `filter/AntiBotFilter.java`: Honeypot, time-to-submit, form tokens (Caffeine cache, 10min TTL), idempotency keys (1hour TTL)
- `controller/TokenController.java`: Form tokens + challenge pages (meta-refresh, JS, Preact)  
- `service/RateLimiterService.java`: Core rate limit logic with CAS-based atomic queue depth tracking (`ConcurrentHashMap<String, AtomicInteger>`)
- `service/JwtService.java`: JWT claim extraction without signature verification (assumes upstream auth)
- `service/ConfigurationService.java`: Cached Redis config access  
- `util/BodyFieldExtractor.java`: Multi-format body parsing (JSON, form URL-encoded, XML, multipart)
- `store/RateLimitRuleStore.java`: Read rules from Redis
- `store/RequestCounterStore.java`: Write/update request counters
- `store/TrafficLogStore.java`: Append traffic logs

**Data Flow:**  
```
Request → RateLimitFilter → AntiBotFilter → Route → Upstream
             ↓                    ↓
        Redis (read)         Redis (write)
```

### Admin Service (`/backend/`)
**Stack:** Spring Boot 3 WebFlux + WebSocket, Reactive Redis, Java 21  
**Port:** 9090 (localhost-only via docker-compose: `127.0.0.1:9090:9090`)  
**Responsibilities:**
- Provide REST APIs for CRUD operations on rules andconfig
- Aggregate analytics from Redis counters/logs
- Broadcast real-time analytics via WebSocket (every 2 seconds)

**Key Components:**
- `controller/RateLimitRuleController.java`: CRUD + `PATCH /{id}/queue` for queue settings
- `controller/SystemConfigController.java`: System settings management
- `controller/AnalyticsController.java`: Analytics REST API (summary, timeseries)
- `service/RateLimitRuleStore.java`: Redis CRUD for rules
- `service/SystemConfigStore.java`: Redis config management
- `service/AnalyticsService.java`: Aggregate stats from Redis counters/logs, call broadcaster
- `websocket/AnalyticsWebSocketHandler.java`: WebSocket handler, wraps messages in `{type, payload}` format
- `websocket/AnalyticsBroadcaster.java`: Reactive Flux broadcaster with scheduled task (2-second intervals)
- `config/RedisConfig.java`: Reactive Redis + ObjectMapper bean
- `config/WebSocketConfig.java`: Map WebSocket endpoint `/poormansRateLimit/api/admin/ws/analytics`
- `model/`: Domain entities (RateLimitRule, SystemConfig, AnalyticsUpdate)
- `dto/WebSocketMessage.java`: Wrapper for WebSocket messages with `type` and `payload` fields

**Data Flow:**  
```
Admin API Call → Controller → Store → Redis
                                ↓
                            Analytics aggregation (scheduled)
                                ↓
                        WebSocket broadcast → Frontend
```

### Frontend (`/frontend/`)
**Stack:** React 18, Vite, Tailwind CSS, Recharts, Nginx reverse proxy  
**Port:** 3000 (localhost-only)  
**Responsibilities:**
- Admin UI for managing rate limit rules and system config
- Real-time analytics dashboard with WebSocket connection
- Route management interface
- System settings UI

**Key Components:**
- `src/App.jsx`: Router with sidebar navigation (Dashboard, Analytics, Policies, Settings)
- `src/pages/Dashboard.jsx`: Real-time stats with WebSocket, green dot connection indicator
- `src/pages/Analytics.jsx`: Recharts visualizations (line charts, time series)
- `src/pages/Policies.jsx`: Rate limit rule CRUD, queue config, JWT settings, body limiting
- `src/pages/Settings.jsx`: System config UI (challenge type, honeypot fields, difficulty sliders)
- `src/utils/websocket.js`: WebSocket client wrapper, auto-reconnect, subscribe/unsubscribe pattern
- `src/utils/formProtection.js`: Anti-bot token helpers (`getFormToken()`, `getAntiBotHeaders()`)
- `src/api.js`: Axios client with `baseURL: '/api'` (proxied by Nginx)
- `src/admin-api.js`: Direct localhost:9090 API calls (development mode)
- `nginx.conf`: **Critical** - Reverse proxy rules:
  - `/poormansRateLimit/api/admin/**` → `http://backend:9090`
  - `/ws/**` → WebSocket proxy to backend:9090
  - Static assets served from `/usr/share/nginx/html`

## Communication Model

**Redis as Single Source of Truth:**
- Gateway writes counters/logs, reads rules/config
- Admin service writes rules/config, reads counters/logs for analytics
- No direct HTTP calls between services

**WebSocket Real-time Updates:**
- Admin service broadcasts analytics every 2 seconds
- Frontend subscribes to WebSocket at `/poormansRateLimit/api/admin/ws/analytics`
- Message format: `{"type": "snapshot"|"summary", "payload": {requestsAllowed, requestsBlocked, activePolicies}}`

## Redis Schema

| Key Pattern | Owner | Purpose | TTL |
|-------------|-------|---------|-----|
| `rate_limit_rules:<uuid>` | Admin (write), Gateway (read) | Rate limit rule configs | No |
| `system_config:<key>` | Admin (write), Gateway (read) | System settings | No |
| `request_counter:<rule>:<id>` | Gateway (write), Admin (read) | Per-identifier request counts | Window duration |
| `traffic_logs` | Gateway (write), Admin (read) | Request audit log (list) | Manual trim |
| `request_stats:<timestamp>` | Admin (write/read) | Aggregated analytics (hash) | No |
| `request_stats:index` | Admin (write/read) | Time series index (zset) | No |

## Rate Limiting Modes

### 1. Token Bucket (Immediate Rejection)
- `allowedRequests`, `windowSeconds`, `queueEnabled: false`
- Rejects excess requests with 429 status
- Redis counters with atomic increment

### 2. Leaky Bucket (Request Queueing)
- `queueEnabled: true`, `maxQueueSize`, `delayPerRequestMs`
- Delays excess requests instead of rejecting
- Queue depth: `ConcurrentHashMap<String, AtomicInteger>` with CAS loops
- Background cleanup every 60 seconds (removes zero-depth entries)
- Response headers: `X-RateLimit-Queued: true`, `X-RateLimit-Delay-Ms: <ms>`

### 3. JWT-Based Rate Limiting
- `jwtEnabled: true`, `jwtClaims: "[\"sub\", \"tenant_id\"]"`, `jwtClaimSeparator: ":"`
- Extracts claims from Authorization header without signature verification
- Concatenates multiple claims for unique identifier
- Falls back to IP-based limiting if JWT missing/invalid

### 4. Header/Cookie/Body-Based Identifiers
- `headerLimitEnabled: true`, `headerName: "X-API-Key"`, `headerLimitType: "combine_with_ip"`
- `cookieLimitEnabled: true`, `cookieName: "session_id"`
- `bodyLimitEnabled: true`, `bodyFieldPath: "user_id"`, `bodyContentType: "application/json"`
- Body extraction supports JSON, form URL-encoded, XML, multipart

## Anti-Bot Protection

**Flow:**
1. Client: `GET /poormansRateLimit/api/tokens/form` → `{token, timestamp}`
2. Server: Store token in Caffeine cache (10min TTL)
3. Client: Submit form with headers: `X-Form-Token`, `X-Time-To-Submit`, `X-Idempotency-Key`, honeypot field
4. Server: Validate in `AntiBotFilter`:
   - Token exists and not used (15min blacklist)
   - Submit time > configured minimum (default: 2000ms)
   - Honeypot field empty
   - Idempotency key not seen (1hour TTL)

**Challenge Types:**
- `metarefresh`: HTML meta tag delay (no JavaScript)
- `javascript`: Client-side token generation
- `preact`: Lightweight React challenge with cookie + auto-reload

## Testing

**Automated Test Suite:**
- `test-gateway.py`: Main suite (24+ tests) - rate limiting, queueing, anti-bot
- `test-jwt-rate-limit.py`: JWT-based rate limiting
- `test-body-content-types.py`: Body field extraction (JSON, form, XML, multipart)
- `run-tests.py`: Cross-platform test runner
- `Run-Tests.ps1`: Windows PowerShell test runner

**Coverage:**
- Gateway routing and proxying
- Token bucket + leaky bucket (queueing with delay validation)
- JWT-based rate limiting (claim extraction, fallback)
- Header/cookie/body-based identifiers
- Anti-bot (honeypot, timing, tokens, idempotency)
- Challenge types (meta-refresh, JS, Preact)
- Admin API (CRUD, queue config PATCH)
- Analytics (summary, timeseries, WebSocket)

## Docker Compose Services

| Service | Folder | Port | Access | Purpose |
|---------|--------|------|--------|---------|
| `gateway` | `./gateway/` | 8080 | Public | Rate limiting + anti-bot |
| `backend` | `./backend/` | 9090 | Localhost-only | Admin APIs + analytics |
| `frontend` | `./frontend/` | 3000 | Localhost-only | Admin UI (Nginx) |
| `redis` | (image) | 6379 | Localhost-only | State store |
| `test-server` | (inline) | 9000 | Localhost-only | Test endpoints |
| `httpbin` | (image) | 8081 | Localhost-only | Proxy target |

**Note on folder/service naming:**
- `gateway` service builds from `/gateway/` folder (was `/backend/` folder)
- `backend` service builds from `/backend/` folder (was `/admin-service/` folder)

## Security Model

- **Gateway (8080)**: Public, enforces rate limits, no admin operations
- **Admin Service (9090)**: Localhost-only binding (`127.0.0.1:9090:9090`), OS-level security
- **Redis (6379)**: Localhost-only, password-protected, AOF persistence
- **Frontend (3000)**: Localhost-only, reverse proxy to both services
- **CORS**: Configured for development (`*`), **restrict in production**

## Key Implementation Patterns

**Reactive Flows:**
- All services use WebFlux reactive stack (`Mono`, `Flux`)
- Reactive Redis client (Lettuce) for non-blocking I/O
- WebSocket with reactive Flux broadcasting

**Thread Safety:**
- Queue depth: `ConcurrentHashMap` + `AtomicInteger` with CAS loops
- Caffeine caches for token validation (thread-safe)
- No shared mutable state in request path

**Performance:**
- Caffeine caches: 100K entries, 10-15min TTL
- Redis counters with TTL match window durations
- Background cleanup tasks prevent memory leaks

## Documentation

- **README.md**: User-facing guide
- **CLAUDE.md**: AI assistant instructions
- **CODEBASE_OVERVIEW.md**: This file (architectural summary)
- **QUEUEING_IMPLEMENTATION.md**: Leaky bucket deep-dive
- **JWT_RATE_LIMITING.md**: JWT rate limiting guide
- **BODY_BASED_RATE_LIMITING.md**: Body extraction guide
- **ADMIN_API_SECURITY.md**: Security architecture
- **SECURITY_AUDIT.md**: Security review
- **AUTOMATED_QUEUEING_TESTS.md**: Test specs
- **TEST-README.md**: Testing guide
- **.github/copilot-instructions.md**: GitHub Copilot guidelines