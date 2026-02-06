# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

API gateway with configurable rate limiting, anti-bot defenses, analytics, and admin UI. **Microservices architecture** with gateway service (public) and isolated admin service (localhost-only).

**Architecture:**
- **Gateway Service** (port 8080): Spring Cloud Gateway 3 with WebFlux, handles public traffic, rate limiting, and anti-bot protection
- **Admin Service** (port 9090): Spring Boot 3 with WebFlux + WebSocket, provides admin APIs and real-time analytics
- **Redis**: Central state store and single source of truth for rules, config, counters
- **Frontend**: React 18 + Vite + Tailwind CSS, served via Nginx with reverse proxy to both services

**Communication Model:**
- Gateway reads rules from Redis, writes counters/logs
- Admin Service provides CRUD on rules/config, aggregates analytics
- No direct HTTP between services - Redis is the only shared interface
- Frontend consumes admin APIs + WebSocket for real-time updates

**Stack:**
- Backend: Spring Boot 3.2.1 / Spring Cloud Gateway (reactive WebFlux), Reactive Redis (Lettuce), Caffeine caches, Java 21
- Frontend: React 18 + Vite + Tailwind CSS, Recharts, served via Nginx
- Infrastructure: Docker Compose (redis, test-server, httpbin, gateway, backend, frontend)

## Common Commands

### Development with Docker (recommended)
```bash
# Start all services
docker compose up --build

# Rebuild specific service
docker compose build gateway      # Public gateway (was 'backend')
docker compose build backend      # Admin service (was 'admin-service')
docker compose build frontend
docker compose up -d <service-name>

# View logs
docker compose logs -f gateway
docker compose logs -f backend
docker compose logs frontend --tail 20

# Clean rebuild
docker compose down -v
docker builder prune -af
docker compose up --build
```

### Gateway Service Development
```bash
cd gateway

# Build (skip tests)
mvn package -DskipTests

# Build with tests
mvn clean package

# Run locally (requires Redis on localhost:6379)
java -jar target/*.jar
# Listens on port 8080
```

### Admin Service Development
```bash
cd backend  # Note: folder renamed from 'admin-service' to 'backend'

# Build (skip tests)
mvn package -DskipTests

# Run locally (requires Redis on localhost:6379)
java -jar target/*.jar
# Listens on port 9090 (localhost-only binding)

# Development server (requires Node 18+)
npm run dev
# Runs on http://localhost:5173 (Vite dev server)

# Production build
npm run build
# Output to dist/ directory (served by Nginx in Docker)

# Preview production build locally
npm run preview
```

### Testing
```bash
# Automated test suite
python run-tests.py              # Cross-platform
.\Run-Tests.ps1                  # Windows PowerShell

# Individual test files
python test-gateway.py           # Main rate limiting + anti-bot tests
python test-jwt-rate-limit.py   # JWT-based rate limiting
python test-body-content-types.py # Body field extraction

# Manual testing
python test-server.py            # Terminal 1 - test backend server
python test-gateway.py           # Terminal 2 - run tests
```

## Project Structure

### Gateway Service (`/gateway/`)
Public-facing Spring Cloud Gateway service (was `/backend/` folder).

**Key Files:**
- `GatewayApplication.java` - Main application class
- `filter/RateLimitFilter.java` - Token bucket + leaky bucket (queueing) enforcement
- `filter/AntiBotFilter.java` - Honeypot, time-to-submit, form tokens, idempotency keys
- `controller/TokenController.java` - Form tokens and challenge pages (meta-refresh/JS/Preact)
- `service/RateLimiterService.java` - Core rate limiting logic with CAS-based queue management
- `service/JwtService.java` - JWT claim extraction (no signature verification)
- `service/ConfigurationService.java` - Cached Redis config access
- `store/RateLimitRuleStore.java` - Redis CRUD for rules (read-only from gateway perspective)
- `store/RequestCounterStore.java` - Request counter persistence
- `util/BodyFieldExtractor.java` - Multi-format body parsing (JSON, form, XML, multipart)
- `model/` - Domain entities shared with admin service (RateLimitRule, RequestCounter, etc.)

**Default port:** 8080 (public)

### Admin Service (`/backend/`)
Isolated admin API and analytics service (was `/admin-service/` folder).

**Key Files:**
- `AdminApplication.java` - Main application class with CORS config
- `controller/RateLimitRuleController.java` - CRUD operations on rules + queue config PATCH endpoint
- `controller/SystemConfigController.java` - System settings management
- `controller/AnalyticsController.java` - Analytics REST API (summary, timeseries)
- `service/RateLimitRuleStore.java` - Redis CRUD for rules
- `service/SystemConfigStore.java` - Redis config management
- `service/AnalyticsService.java` - Analytics aggregation from Redis counters/logs
- `websocket/AnalyticsWebSocketHandler.java` - WebSocket connection handler, wraps messages in `{type, payload}` format
- `websocket/AnalyticsBroadcaster.java` - Reactive Flux broadcaster (scheduled every 2 seconds)
- `config/RedisConfig.java` - Reactive Redis configuration + ObjectMapper bean
- `config/WebSocketConfig.java` - WebSocket endpoint mapping
- `model/` - Domain entities (RateLimitRule, SystemConfig, AnalyticsUpdate)
- `dto/WebSocketMessage.java` - WebSocket message wrapper

**Default port:** 9090 (localhost-only via docker-compose port binding: `127.0.0.1:9090:9090`)

### Frontend (`/frontend/`)
React admin UI with Nginx reverse proxy.

**Key Files:**
- `src/App.jsx` - Router with sidebar navigation (Dashboard, Analytics, Policies, Settings)
- `src/pages/Dashboard.jsx` - Real-time analytics with WebSocket, connection indicator
- `src/pages/Analytics.jsx` - Recharts visualizations, time series charts
- `src/pages/Policies.jsx` - Rate limit rule CRUD with anti-bot headers and queue config
- `src/pages/Settings.jsx` - System settings UI (challenge type, honeypot, etc.)
- `src/utils/websocket.js` - WebSocket client (connects to `/poormansRateLimit/api/admin/ws/analytics`)
- `src/utils/formProtection.js` - Anti-bot token helpers for form submissions
- `src/api.js` - Axios client (proxied via Nginx to backend services)
- `src/admin-api.js` - Direct localhost:9090 API calls (development)
- `nginx.conf` - **Critical**: Reverse proxy configuration
  - `/poormansRateLimit/api/admin/**` → `http://backend:9090`
  - `/ws/**` → WebSocket proxy to backend
  - Serves static assets from `/usr/share/nginx/html`

**Default port:** 3000 (localhost-only)


## Architecture & Communication Flow

### Microservices Separation

**Gateway Service** (public, port 8080):
- Handles all incoming HTTP traffic
- Enforces rate limits via `RateLimitFilter`
- Applies anti-bot protections via `AntiBotFilter`
- **Reads** rules and config from Redis
- **Writes** request counters and traffic logs to Redis
- **Never calls** admin service directly

**Admin Service** (isolated, port 9090):
- Provides REST APIs for CRUD operations on rules/config
- Broadcasts real-time analytics via WebSocket
- **Reads** counters and logs from Redis for analytics
- **Writes** rules and config to Redis
- **Never called** by gateway service

**Redis** (single source of truth):
- Stores rate limit rules, system config
- Stores request counters (with TTL)
- Stores traffic logs for analytics
- Both services communicate **only** through Redis

### Data Flow

```
Public Traffic:
Internet → gateway:8080 → RateLimitFilter → AntiBotFilter → Upstream Services
                              ↓                    ↓
                          Redis (read/write counters/logs)

Admin Operations:
Browser → frontend:3000 (Nginx) → backend:9090 → Redis (CRUD rules/config)
                                        ↓
                                   WebSocket (real-time analytics)

Analytics Aggregation:
backend:9090 (scheduled task) → Redis (read counters/logs) → aggregate → broadcast via WebSocket
```

### Redis Key Schema

| Key Pattern | Used By | Purpose | TTL |
|-------------|---------|---------|-----|
| `rate_limit_rules:*` | Both | Rate limit rule configurations | No |
| `system_config:*` | Both | System settings | No |
| `request_counter:<rule>:<id>` | Gateway (write), Admin (read) | Per-identifier request counts | Window duration |
| `traffic_logs` | Gateway (write), Admin (read) | Request audit log (list) | No (manually trimmed) |
| `request_stats:<timestamp>` | Admin (write) | Aggregated analytics | No |
| `request_stats:index` | Admin (write/read) | Time series index (zset) | No |

## Key Implementation Details

### Rate Limiting (Gateway Service)

**Token Bucket Algorithm:**
- `RateLimiterService.isAllowed()` checks Redis counter keys
- Counter structure: `{ ruleId, clientIp, requestCount, windowStart }`
- Atomic increment with compare-and-set semantics
- Automatic expiry via Redis TTL

**Leaky Bucket (Queueing):**
- Enabled with `queueEnabled: true`, `maxQueueSize`, `delayPerRequestMs`
- Queue depth tracked atomically: `ConcurrentHashMap<String, AtomicInteger>`
- CAS loop ensures thread-safe queue operations: `queueDepth.compareAndSet(current, current+1)`
- Background cleanup every 60 seconds removes zero-depth entries
- `RateLimitFilter` applies delays using `Mono.delay(Duration.ofMillis(delayMs))`
- Response headers: `X-RateLimit-Queued: true`, `X-RateLimit-Delay-Ms: <ms>`

**JWT-Based Rate Limiting:**
- `JwtService.extractClaims()` parses JWT from Authorization header
- Supports standard claims (sub, iss, aud) and custom claims
- Concatenates multiple claims with configurable separator
- **Does NOT validate signatures** (assumes upstream auth)
- Falls back to IP-based limiting if JWT extraction fails

### Anti-Bot Protection (Gateway Service)

**Flow:**
1. Client: `GET /poormansRateLimit/api/tokens/form` → receives UUID token + timestamp
2. Server: Stores token in Caffeine cache (10min TTL)
3. Client: Submits form with headers: `X-Form-Token`, `X-Time-To-Submit`, `X-Idempotency-Key`, honeypot field
4. Server: Validates in `AntiBotFilter`:
   - Token exists and not used (15min blacklist for used tokens)
   - Time-to-submit > configured minimum
   - Honeypot field empty
   - Idempotency key not seen (1hour TTL)

**Challenge Types:**
- `metarefresh`: HTML meta tag with `http-equiv="refresh"` (no JS required)
- `javascript`: Client-side token generation and validation  
- `preact`: Lightweight React alternative with auto-reload

### Analytics (Admin Service)

**Aggregation:**
- `AnalyticsService` reads request counters and traffic logs from Redis
- Aggregates allowed/blocked counts, active policies
- Scheduled task runs every 2 seconds via `AnalyticsBroadcaster.scheduledBroadcast()`

**WebSocket Broadcasting:**
- `AnalyticsWebSocketHandler` wraps updates in `WebSocketMessage` format:
  ```json
  {
    "type": "snapshot",  // or "summary"
    "payload": {
      "requestsAllowed": 0,
      "requestsBlocked": 0,
      "activePolicies": 1
    }
  }
  ```
- Initial connection receives `snapshot` message
- Subsequent updates every 2 seconds are `summary` messages
- `AnalyticsBroadcaster` maintains Flux sink for all connected clients

## Common Tasks

### Adding a New Rate Limit Rule
```bash
# Via Admin Service API
curl -X POST http://localhost:9090/poormansRateLimit/api/admin/rules \
  -H "Content-Type: application/json" \
  -d '{
    "pathPattern": "/api/**",
    "allowedRequests": 100,
    "windowSeconds": 60,
    "active": true,
    "priority": 1
  }'

# Changes are immediately available to gateway via Redis
```

### Enabling Queueing on Existing Rule
```bash
curl -X PATCH http://localhost:9090/poormansRateLimit/api/admin/rules/{rule-id}/queue \
  -H "Content-Type: application/json" \
  -d '{
    "queueEnabled": true,
    "maxQueueSize": 10,
    "delayPerRequestMs": 500
  }'
```

### Adding JWT-Based Rate Limiting
```bash
curl -X PUT http://localhost:9090/poormansRateLimit/api/admin/rules/{rule-id} \
  -H "Content-Type: application/json" \
  -d '{
    "pathPattern": "/api/tenant/**",
    "allowedRequests": 100,
    "windowSeconds": 60,
    "active": true,
    "jwtEnabled": true,
    "jwtClaims": "[\"sub\", \"tenant_id\"]",
    "jwtClaimSeparator": ":"
  }'
```

### Debugging Rate Limits
```bash
# Check gateway logs for rule loading
docker compose logs gateway | grep "Loaded.*rate limit rules"

# Inspect Redis counters
docker exec -it rate-limiter-gateway-redis-1 redis-cli -a dev-only-change-me
> KEYS request_counter:*
> GET request_counter:<rule-id>:<ip>

# Check queue depth (gateway logs)
docker compose logs gateway | grep "Delaying request"
```

### Debugging WebSocket Issues
```bash
# Check admin service logs
docker compose logs backend | grep WebSocket

# Test WebSocket connection
wscat -c ws://localhost:9090/poormansRateLimit/api/admin/ws/analytics

# In browser DevTools: Network tab → WS filter → check message format
# Should see: {"type": "snapshot", "payload": {...}}
```

## Code Style & Patterns

### Spring Boot Best Practices
- Use **reactive types** (`Mono`, `Flux`) throughout - avoid blocking operations
- Leverage **Reactive Redis** (ReactiveRedisTemplate) for non-blocking I/O
- Use **Caffeine caches** for high-performance in-memory caching (anti-bot tokens)
- Apply **Lombok** annotations to reduce boilerplate (@Data, @RequiredArgsConstructor, @Slf4j)

### Logging
- **DEBUG**: Detailed tracing, JWT extraction, cache operations
- **INFO**: Key events (rules loaded, WebSocket connections, rate limit decisions)
- **ERROR**: Failures (Redis connection errors, serialization issues)

### Thread Safety
- Use `ConcurrentHashMap` + `AtomicInteger` for queue depth tracking
- CAS loops for atomic updates: `atomicInt.compareAndSet(expected, newValue)`
- Avoid shared mutable state - prefer reactive Flux/Mono chains

## Deployment & Docker

### Service Names in docker-compose.yml
- `gateway` - Gateway service (builds from `./gateway/` folder)
- `backend` - Admin service (builds from `./backend/` folder)
- `frontend` - Nginx + React UI
- `redis` - Redis 7
- `test-server` - Python Flask test server
- `httpbin` - External service for testing

**Note**: Service names changed from original architecture:
- Old `backend` → New `gateway` (folder renamed: `/backend/` → `/gateway/`)
- Old `admin-service` → New `backend` (folder renamed: `/admin-service/` → `/backend/`)

### Port Bindings
- Gateway: `8080:8080` (public)
- Admin Service: `127.0.0.1:9090:9090` (localhost-only via OS-level binding)
- Frontend: `127.0.0.1:3000:80` (localhost-only)
- Redis: `127.0.0.1:6379:6379` (localhost-only)

## Testing Strategy

### Unit Tests
- Service-level tests (RateLimiterService, JwtService, ConfigurationService)
- Store-level tests (Redis interactions)
- Utility tests (BodyFieldExtractor with multiple content types)

### Integration Tests
- Full Docker stack (`docker compose up`)
- Python test suite hits real gateway endpoints
- Validates rate limiting, queueing, anti-bot, JWT, analytics

### Test Files
- `test-gateway.py` - Main test suite (24+ tests)
- `test-jwt-rate-limit.py` - JWT-specific tests
- `test-body-content-types.py` - Body parsing tests (JSON, form, XML, multipart)
- `run-tests.py` - Test runner with server management
- `Run-Tests.ps1` - Windows PowerShell test runner

## Documentation Files
- **README.md** - User-facing documentation
- **CLAUDE.md** - This file (AI assistant guide)
- **CODEBASE_OVERVIEW.md** - Architectural summary
- **QUEUEING_IMPLEMENTATION.md** - Leaky bucket technical deep-dive
- **JWT_RATE_LIMITING.md** - JWT-based rate limiting guide
- **BODY_BASED_RATE_LIMITING.md** - Body field extraction guide
- **ADMIN_API_SECURITY.md** - Security architecture
- **SECURITY_AUDIT.md** - Security review findings
- **AUTOMATED_QUEUEING_TESTS.md** - Queueing test specifications
- **TEST-README.md** - Testing instructions
- **.github/copilot-instructions.md** - GitHub Copilot guidelines

**Data Layer:**
- Redis stores for reactive/non-blocking data access
- Defaults seeded on startup (`RedisBootstrapService`) and config defaults
- Redis connection configured in `application.yml` and `docker-compose.yml`

### Frontend Core Components

**Pages:**
- `pages/Dashboard.jsx` - Overview and quick stats
- `pages/Analytics.jsx` - Recharts visualizations + live WebSocket updates
- `pages/Policies.jsx` - Rate limit policy CRUD with anti-bot headers
- `pages/Settings.jsx` - System config including anti-bot challenge types and difficulty

**Utilities:**
- `api.js` - Centralized axios client for gateway API calls (points to `/api`, proxied by Nginx)
- `utils/websocket.js` - Real-time analytics WebSocket client (`/api/ws/analytics`)
- `utils/formProtection.js` - Token-based anti-bot form helpers

**Infrastructure:**
- Nginx config in `frontend/nginx.conf` proxies `/poormansRateLimit/api/` to backend:9090 and `/api/` + `/api/ws/` to backend:8080
- Build artifacts in `frontend/dist` served by Nginx container

### Rate Limiting & Queueing (Leaky Bucket)

**Token Bucket Implementation:**
- Enforced in `RateLimitFilter.java` via `RateLimiterService.checkRateLimit()`
- Per-policy/rule limits with configurable window and request count
- Caffeine caches for token buckets and rate limit counters

**Queueing Support:**
- When rate limit exceeded and queueing enabled, requests are delayed instead of rejected
- Queue depth tracked per rule+IP using `ConcurrentHashMap<String, AtomicInteger>`
- Thread-safe queue checking/incrementing via atomic compare-and-set (CAS) loop
- Delays calculated as `position * delayPerRequestMs`
- Background cleanup task removes stale queue entries every 60 seconds
- Response headers: `X-RateLimit-Queued: true`, `X-RateLimit-Delay-Ms: {delay}`

**Admin API for Queue Configuration:**
```bash
# Update queue settings for a rule (admin API on 9090)
PATCH /api/admin/rules/{id}/queue
{
  "queueEnabled": true,
  "maxQueueSize": 5,
  "delayPerRequestMs": 300
}

# Full rule CRUD also available
GET    /api/admin/rules          # List all
GET    /api/admin/rules/active   # Active only
POST   /api/admin/rules          # Create
PUT    /api/admin/rules/{id}     # Update
DELETE /api/admin/rules/{id}     # Delete
POST   /api/admin/rules/refresh  # Reload from DB
```

See `QUEUEING_IMPLEMENTATION.md` for detailed design notes.

### Anti-Bot Defenses

**Challenge Types** (configured in Settings UI):
- `metarefresh` - HTML meta refresh with configurable delay
- `javascript` - JavaScript-based token verification
- `preact` - Lightweight Preact challenge that sets cookie and reloads

**Protection Mechanisms:**
- Honeypot field detection (hidden form fields that bots fill)
- Time-to-submit validation (too fast = likely bot)
- One-time form tokens (prevents replay attacks)
- Idempotency keys (prevents duplicate submissions)
- Challenge cookies validated by `AntiBotFilter`

**Configuration Keys:**
- `antibot-challenge-type` - metarefresh|javascript|preact
- `antibot-metarefresh-delay` - seconds
- `antibot-preact-difficulty` - seconds before auto-refresh

### Reactive Architecture Notes

**Critical: Avoid blocking in request paths**
- This is a Spring WebFlux application; filters and services must be non-blocking
- Use reactive types: `Mono<T>`, `Flux<T>`
- Redis reactive APIs for async data access
- When adding delays, use `Mono.delay(Duration.ofMillis(x))` not `Thread.sleep()`
- Chain operations with `.flatMap()`, `.map()`, `.filter()` etc.

### Testing Infrastructure

**Test Suite** (`test-gateway.py`):
- 24 comprehensive tests covering rate limiting, anti-bot, queueing, routing
- Tests 21-24 are automated queueing tests (see `AUTOMATED_QUEUEING_TESTS.md`)
- Requires running gateway (http://localhost:8080) and test-server (http://localhost:9000)

**Test Server** (`test-server.py`):
- Simple Flask server exposing test endpoints
- Automatically started by `run-tests.py` or `docker-compose.yml`
- Listens on port 9000

## Service Ports

- Frontend: 3000 (Nginx serving React app)
- Gateway: 8080 (public routes)
- Admin API: 9090 (admin endpoints)
- Redis: 6379
- Test Server: 9000 (Flask)

## Security Considerations

- CORS configured at controller level
- Redis connection config in `application.yml` and `docker-compose.yml`
- Form tokens stored in Caffeine caches (in-memory, ephemeral)