# Poor Man Rate Limiter Gateway

Production-ready API gateway with advanced rate limiting, anti-bot defenses, real-time analytics, and comprehensive admin UI. Microservices architecture with isolated admin service for security and scalability.

## 🏗️ Architecture

**Microservices Design:**
- **Gateway Service** (port 8080): Public-facing API gateway handling rate limiting, anti-bot protection, and request routing
- **Admin Service** (port 9090): Isolated admin API and real-time analytics WebSocket server (localhost-only access)
- **Redis**: Central state store and single source of truth for configuration and analytics
- **Frontend**: React admin UI served via Nginx, proxying to both services

**Communication:**
- Gateway ↔ Redis: Read rules, write request counters and traffic logs
- Admin Service ↔ Redis: CRUD operations on rules/config, analytics aggregation
- Frontend ↔ Admin Service: REST APIs + WebSocket for real-time updates
- Gateway **never calls** Admin Service directly - Redis is the only shared interface

## 🚀 Key Features

### Rate Limiting
- **Token Bucket** algorithm with configurable replenish rates and burst capacity
- **Leaky Bucket (Queueing)** - delays requests instead of rejecting them during traffic spikes
  - Configurable queue size and per-request delay
  - Atomic queue depth tracking prevents race conditions
  - Automatic queue cleanup and monitoring
- **JWT-Based Rate Limiting** - rate limit by JWT token claims (user ID, tenant ID, custom claims)
  - Supports standard and custom JWT claims
  - Multi-claim concatenation for complex identifiers
  - Automatic fallback to IP-based limiting
- **Header/Cookie/Body-Based Limiting**
  - Header or cookie identifiers with optional `IP:value` combination
  - Body field extraction supports JSON, form URL-encoded, XML, and multipart
- **Multiple Limit Types**: IP, JWT, header, cookie, body
- **Per-route Configuration**: Different limits for different endpoints
- **Real-time Updates**: Changes propagate instantly via Redis pub/sub

### Anti-Bot Protection
- **Honeypot Fields** - Hidden inputs that trap bots
- **Time-to-Submit Analysis** - Detects suspiciously fast form submissions
- **One-time Form Tokens** - Prevents replay attacks with Caffeine cache
- **Idempotency Keys** - Prevents duplicate submissions
- **Challenge Responses** - Three types:
  - **Meta Refresh**: HTML-only delay (no JavaScript required)
  - **JavaScript**: Client-side token generation and validation
  - **Preact**: Lightweight React alternative with auto-refresh
- **Configurable Protection Levels**: Enable/disable features per environment

### Real-time Analytics
- **WebSocket Live Updates**: Dashboard refreshes automatically as requests flow
- **Traffic Logging**: Persistent logs with timestamp, path, IP, status, and allow/block decision
- **Time Series Data**: Visualizations powered by Recharts
- **Request Counters**: Per-rule, per-IP tracking with automatic window cleanup
- **Summary Stats**: Allowed/blocked counts, active rules, queue depth

### Admin UI
- **Dashboard**: Real-time monitoring with WebSocket connection indicator
- **Policies**: CRUD interface for rate limit rules with live validation
- **Analytics**: Charts and graphs showing traffic patterns over time
- **Settings**: System configuration with immediate apply (no restart required)

## � Stack

### Gateway Service
- Spring Cloud Gateway 3 with WebFlux (Reactive, non-blocking)
- Reactive Redis for shared state
- Caffeine caching for performance
- Spring Boot 3.2.1, Java 21

### Admin Service  
- Spring Boot 3.2.1 WebFlux (Reactive HTTP + WebSocket)
- Reactive Redis for data persistence
- Real-time analytics broadcasting
- Scheduled analytics aggregation (2-second intervals)
- Java 21

### Frontend
- React 18 with Vite
- Tailwind CSS for styling
- Recharts for data visualization
- Lucide icons
- Nginx reverse proxy

### Infrastructure
- Redis 7 with AOF persistence and password protection
- Docker Compose orchestration
- Multi-stage Docker builds for optimization

## 🏃 Quick Start

### Docker (Recommended)
```bash
docker compose up --build

# Access Points:
# - Admin UI:        http://localhost:3000
# - Gateway (public): http://localhost:8080
# - Admin API:       http://localhost:9090/poormansRateLimit/api/admin/*
# - Redis:           localhost:6379
```

### Development Setup

#### Gateway Service
```bash
cd gateway
mvn clean package -DskipTests
java -jar target/*.jar
# Runs on port 8080
```

#### Admin Service
```bash
cd backend  
mvn clean package -DskipTests
java -jar target/*.jar
# Runs on port 9090
```

#### Frontend
```bash
cd frontend
npm install
npm run dev  # Requires Node 18+
# Runs on port 5173 (dev) or served via Nginx (production)
```

## 📚 Core Concepts

### Rate Limiting Modes

#### 1. Token Bucket (Immediate Rejection)
Traditional rate limiting that rejects requests when limits are exceeded:
```json
{
  "pathPattern": "/api/**",
  "allowedRequests": 100,
  "windowSeconds": 60,
  "queueEnabled": false
}
```

#### 2. Leaky Bucket (Request Queueing)
Delays excess requests instead of rejecting them:
```json
{
  "pathPattern": "/api/**",
  "allowedRequests": 100,
  "windowSeconds": 60,
  "queueEnabled": true,
  "maxQueueSize": 10,
  "delayPerRequestMs": 500
}
```

**Queueing Behavior**:
- First 100 requests in 60s: immediate ✅
- Next 10 requests: delayed (500ms, 1000ms, 1500ms...) ⏱️
- Beyond queue: rejected with 429 ❌

**Response Headers**:
```
X-RateLimit-Queued: true
X-RateLimit-Delay-Ms: 1500
```

#### 3. JWT-Based Rate Limiting
Rate limit based on JWT token claims instead of IP addresses:
```json
{
  "pathPattern": "/api/tenant/**",
  "allowedRequests": 100,
  "windowSeconds": 60,
  "jwtEnabled": true,
  "jwtClaims": "[\"sub\", \"tenant_id\"]",
  "jwtClaimSeparator": ":"
}
```

**Features**:
- Extract standard (sub, iss, aud) and custom JWT claims
- Concatenate multiple claims for unique identifiers (e.g., `user-123:tenant-xyz`)
- Automatic fallback to IP-based limiting if JWT is missing/invalid
- No signature verification required (assumes upstream authentication)

**Example JWT Payload**:
```json
{
  "sub": "user-12345",
  "tenant_id": "acme-corp",
  "user_role": "admin"
}
```

With claims `["sub", "tenant_id"]` and separator `:`, the rate limit identifier becomes: `user-12345:acme-corp`

**See detailed documentation:** [JWT_RATE_LIMITING.md](JWT_RATE_LIMITING.md)

**Test script:** `python test-jwt-rate-limit.py`

#### 4. Header, Cookie, and Body-Based Identifiers
Use non-IP identifiers directly from request metadata or payload fields:

```json
{
  "pathPattern": "/api/orders/**",
  "allowedRequests": 50,
  "windowSeconds": 60,
  "headerLimitEnabled": true,
  "headerName": "X-API-Key",
  "headerLimitType": "combine_with_ip"
}
```

Body-based limiting supports JSON, form URL-encoded, XML, and multipart fields. See [BODY_BASED_RATE_LIMITING.md](BODY_BASED_RATE_LIMITING.md) for details.

### Anti-Bot Challenge Flow

1. **Client requests form token**: `GET /api/tokens/form`
2. **Server generates token** with timestamp, stores in cache
3. **Client submits form** with:
  - `X-Form-Token` header
  - `X-Form-Load-Time` (milliseconds since page load)
  - `X-Idempotency-Key` (optional, for duplicate prevention)
  - `X-Honeypot` (should be empty)
4. **Server validates**:
   - Token exists and not used
   - Submit time > configured minimum (default: 2000ms)
  - Honeypot header is empty
   - Idempotency key not seen before
5. **Accept or reject** based on validation results

### Challenge Types

Configure via Settings UI or `system_config` table:

| Type | Description | Use Case |
|------|-------------|----------|
| `metarefresh` | HTML meta tag delay | No-JS clients, accessibility |
| `javascript` | Client-side token generation | Modern web apps |
| `preact` | Lightweight React challenge | Progressive enhancement |

**Settings**:
- `antibot-challenge-type`: Challenge implementation
- `antibot-metarefresh-delay`: Delay in seconds (meta refresh only)
- `antibot-preact-difficulty`: Delay in seconds (Preact only)

## 🔌 API Reference

### Admin Service Endpoints (port 9090)

**Base Path**: `/poormansRateLimit/api/admin`

#### Rate Limit Rules
```bash
# List all rules
GET /poormansRateLimit/api/admin/rules

# Get specific rule
GET /poormansRateLimit/api/admin/rules/{id}

# Create rule
POST /poormansRateLimit/api/admin/rules
Content-Type: application/json

{
  "pathPattern": "/api/**",
  "allowedRequests": 100,
  "windowSeconds": 60,
  "active": true,
  "priority": 1
}

# Update rule
PUT /poormansRateLimit/api/admin/rules/{id}

# Update queue settings only
PATCH /poormansRateLimit/api/admin/rules/{id}/queue
{
  "queueEnabled": true,
  "maxQueueSize": 10,
  "delayPerRequestMs": 500
}

# Delete rule
DELETE /poormansRateLimit/api/admin/rules/{id}

# Refresh rule cache
POST /poormansRateLimit/api/admin/rules/refresh
```

#### System Configuration
```bash
# Get all config
GET /poormansRateLimit/api/admin/config

# Update config
POST /poormansRateLimit/api/admin/config/{key}
Content-Type: text/plain

{value}
```

#### Analytics
```bash
# Get summary stats
GET /poormansRateLimit/api/admin/analytics/summary
# Returns: { requestsAllowed, requestsBlocked, activePolicies }

# Get time series data
GET /poormansRateLimit/api/admin/analytics/timeseries?from={ISO-date}&to={ISO-date}
```

#### WebSocket (Real-time Analytics)
```javascript
// Connect to WebSocket
const ws = new WebSocket('ws://localhost:9090/poormansRateLimit/api/admin/ws/analytics');

// Receive updates every 2 seconds
ws.onmessage = (event) => {
  const data = JSON.parse(event.data);
  // { type: 'snapshot' | 'summary', payload: { requestsAllowed, requestsBlocked, activePolicies } }
};
```

### Gateway Endpoints (port 8080)

#### Anti-Bot Tokens
```bash
# Get form protection token
GET /poormansRateLimit/api/tokens/form
# Returns: { token: "uuid", timestamp: 1234567890 }

# Get challenge page
GET /poormansRateLimit/api/tokens/challenge?type=metarefresh|javascript|preact
```

#### Proxied Routes
```bash
# Routes configured in application.yml
# Example: /httpbin/** → https://httpbin.org/
```



## 🧪 Testing

### Automated Test Suite
```bash
# Cross-platform (Python)
python run-tests.py

# Windows (PowerShell)
.\Run-Tests.ps1

# Run specific test files
python test-gateway.py
python test-jwt-rate-limit.py
python test-body-content-types.py
```

### Test Coverage
- ✅ Gateway routing and proxying (httpbin integration)
- ✅ Rate limiting (token bucket, rapid requests, burst handling)
- ✅ Queueing/leaky bucket (delay timing, queue overflow)
- ✅ Anti-bot (honeypot, timing, tokens, idempotency)
- ✅ Challenges (meta refresh, JavaScript, Preact)
- ✅ JWT-based rate limiting (claim extraction, fallback)
- ✅ Header/Cookie/Body-based identifiers (multi-content-type support)
- ✅ Admin API (CRUD for rules, configs)
- ✅ Analytics (summary, time series, WebSocket updates)

**24+ automated tests** covering all features. See:
- `test-gateway.py` - Main test suite
- `test-jwt-rate-limit.py` - JWT rate limiting tests
- `test-body-content-types.py` - Body field extraction tests
- `AUTOMATED_QUEUEING_TESTS.md` - Queueing test details
- `TEST-README.md` - Testing guide

### Manual Testing with Docker
```bash
# Start all services
docker compose up --build

# Test gateway routing
curl http://localhost:8080/httpbin/get

# Test rate limiting (burst requests)
for i in {1..110}; do curl http://localhost:8080/httpbin/anything; done

# Test admin API
curl http://localhost:9090/poormansRateLimit/api/admin/rules

# Test frontend
open http://localhost:3000
```

## 📁 Architecture Overview

### Microservices Structure

```
rate-limiter-gateway/
├── gateway/                      # Public-facing gateway service (port 8080)
│   └── src/main/java/com/example/gateway/
│       ├── GatewayApplication.java
│       ├── filter/
│       │   ├── RateLimitFilter.java      # Token bucket + leaky bucket enforcement
│       │   └── AntiBotFilter.java        # Honeypot, timing, form token validation
│       ├── controller/
│       │   └── TokenController.java      # Form tokens & challenge pages
│       ├── service/
│       │   ├── RateLimiterService.java   # Core rate limit logic + queue management
│       │   ├── ConfigurationService.java # Cached Redis config access
│       │   ├── JwtService.java           # JWT claim extraction (no signature verification)
│       │   └── RouteSyncService.java     # Dynamic route updates from Redis
│       ├── store/
│       │   ├── RateLimitRuleStore.java   # Redis CRUD for rules
│       │   ├── RequestCounterStore.java  # Request counter persistence
│       │   └── TrafficLogStore.java      # Audit log storage
│       ├── util/
│       │   └── BodyFieldExtractor.java   # Multi-format body parsing
│       ├── model/                        # Domain entities (shared with backend)
│       └── dto/                          # Data transfer objects
│
├── backend/                      # Isolated admin service (port 9090, localhost-only)
│   └── src/main/java/com/example/admin/
│       ├── AdminApplication.java
│       ├── controller/
│       │   ├── RateLimitRuleController.java  # CRUD + queue config endpoints
│       │   ├── SystemConfigController.java   # Settings management
│       │   └── AnalyticsController.java      # Stats REST API
│       ├── service/
│       │   ├── RateLimitRuleStore.java       # Redis rule persistence
│       │   ├── SystemConfigStore.java        # Redis config management
│       │   └── AnalyticsService.java         # Stats aggregation from Redis
│       ├── websocket/
│       │   ├── AnalyticsWebSocketHandler.java    # WebSocket connection handler
│       │   └── AnalyticsBroadcaster.java         # Reactive broadcast (2s intervals)
│       ├── config/
│       │   ├── RedisConfig.java              # Reactive Redis + ObjectMapper
│       │   └── WebSocketConfig.java          # WebSocket endpoint mapping
│       ├── model/                            # Domain entities
│       └── dto/
│           └── WebSocketMessage.java         # {type, payload} wrapper
│
├── frontend/                     # React admin UI (port 3000, Nginx)
│   ├── src/
│   │   ├── pages/
│   │   │   ├── Dashboard.jsx         # Real-time stats (WebSocket)
│   │   │   ├── Analytics.jsx         # Charts (Recharts)
│   │   │   ├── Policies.jsx          # Rate limit rule management
│   │   │   └── Settings.jsx          # System configuration
│   │   ├── utils/
│   │   │   ├── websocket.js          # WebSocket client
│   │   │   └── formProtection.js     # Anti-bot helpers
│   │   ├── api.js                    # Axios (proxied /api → backends)
│   │   ├── admin-api.js              # Direct localhost:9090 calls
│   │   └── App.jsx                   # React Router
│   └── nginx.conf                    # Reverse proxy config
│       # /poormansRateLimit/api/admin/** → http://backend:9090
│       # /ws/** → WebSocket proxy
│
└── docker-compose.yml
    # Services: redis, test-server, httpbin, gateway, backend, frontend
```

### Data Flow

#### Request Flow (Public Traffic)
```
Internet → gateway:8080 → RateLimitFilter → AntiBotFilter → Routes → Upstream Services
                    ↓                             ↓
                Redis (read rules)         Redis (write counters/logs)
```

#### Admin Operations
```
Browser → frontend:3000 → Nginx → backend:9090 → Redis
                                        ↓
                                   WebSocket (analytics)
```

#### Analytics Aggregation
```
gateway → writes counters/logs to Redis
    ↓
backend → reads Redis → aggregates stats → broadcasts via WebSocket → frontend
    (scheduled every 2 seconds)
```

### Communication Patterns

1. **Gateway → Redis**: 
   - **Read**: Rate limit rules, system config
   - **Write**: Request counters, traffic logs
   - **No direct HTTP calls** to admin service

2. **Admin Service → Redis**:
   - **Read**: Counters, logs for analytics aggregation
   - **Write**: Rules, system config (CRUD operations)
   - **No dependencies** on gateway service

3. **Frontend → Admin Service**:
   - **REST API**: CRUD operations on rules/configs
   - **WebSocket**: Real-time analytics updates

4. **Frontend → Gateway**:
   - **End-user traffic**: Proxied through gateway routes
   - **No admin operations**: Admin APIs isolated to backend service

### Redis Data Schema

#### Keys Used By Gateway
```
rate_limit_rules:<uuid>         # Rate limit rule configurations
system_config:*                 # System settings (honeypot config, etc.)
request_counter:<rule>:<id>     # Per-identifier request counts (TTL: window duration)
traffic_logs                    # Request audit log (list)
```

#### Keys Used By Admin Service
```
rate_limit_rules:*              # CRUD operations on rules
system_config:*                 # Settings management
request_stats:<timestamp>       # Aggregated analytics (hash)
request_stats:index             # Time series index (zset)
```

### Security Model

- **Gateway (port 8080)**: 
  - Publicly accessible
  - No admin operations exposed
  - Rate limiting and anti-bot enforcement only

- **Admin Service (port 9090)**:
  - Localhost-only binding via docker-compose
  - OS-level port security: `127.0.0.1:9090:9090`
  - Not accessible from outside host machine

- **Frontend (port 3000)**:
  - Localhost-only binding
  - Reverse proxy to both services
  - CORS configured for development (restrict in production)

- **Redis (port 6379)**:
  - Localhost-only binding
  - Password-protected
  - AOF persistence enabled

### Redis Keyspace
- `rate_limit_rules` (hash) - Path-based rules with queue config
- `rate_limit_state:<key>` (hash) - Token bucket state
- `request_counter:<ruleId>:<identifier>` (string JSON, TTL)
- `system_config` (hash) - Key-value settings
- `traffic_logs` (list) - Recent request log entries (JSON)
- `request_stats:<minute>` (hash) - Time-windowed aggregates
- `request_stats:index` (zset) - Minute buckets for time series

## 🔁 Identifier Priority

When multiple limit modes are configured, the gateway picks the identifier in this order:

1. Header
2. Cookie
3. Body field
4. JWT claims
5. IP address

Use `headerLimitType`, `cookieLimitType`, or `bodyLimitType` to combine with IP (`combine_with_ip`).

## 🔒 Security Considerations

### Configuration Keys
- **`antibot-enabled`**: Master switch for anti-bot features (default: `true`)
- **`analytics-retention-days`**: Days of time-series analytics to keep (default: `7`)
- **`traffic-logs-retention-hours`**: Hours to keep raw request logs (default: `24`)
- **`traffic-logs-max-entries`**: Maximum entries kept in the traffic log list (default: `10000`)

### CORS & Networking
- **Frontend (Nginx)** proxies:
  - `/poormansRateLimit/api/admin/**` → `http://backend:9090` (Admin Service)
  - `/ws/**` → `ws://backend:9090` (WebSocket)
  - All static assets served locally
- **CORS** configured at controller level via `@CrossOrigin` annotations
- **Production**: Restrict origins, enable TLS, use proper authentication

### Credentials & Secrets
- Redis password set via `REDIS_PASSWORD` environment variable (default: `dev-only-change-me`)
- Connection config in `application.yml` and `docker-compose.yml`
- **Production**: Use secrets management (AWS Secrets Manager, Vault, etc.), ACLs, and private networking

## 🐛 Troubleshooting

### Service Won't Start
```bash
# Check container status
docker compose ps

# View logs
docker compose logs gateway --tail 50
docker compose logs backend --tail 50
docker compose logs frontend --tail 20

# Check Redis connectivity
docker exec -it rate-limiter-gateway-redis-1 redis-cli -a dev-only-change-me PING
```

### Build Issues
```bash
# Clean rebuild all services
docker compose down -v
docker builder prune -af
docker compose up --build

# Rebuild specific service
docker compose build gateway
docker compose up -d gateway
```

### Frontend Not Loading
```bash
# Check Nginx logs
docker compose logs frontend

# Verify backend/gateway are running
curl http://localhost:9090/poormansRateLimit/api/admin/rules
curl http://localhost:8080/poormansRateLimit/api/tokens/form

# Test Nginx proxy
curl -v http://localhost:3000/poormansRateLimit/api/admin/config
```

### WebSocket Not Connecting
```bash
# Check backend logs for WebSocket messages
docker compose logs backend | grep WebSocket

# Verify WebSocket endpoint
wscat -c ws://localhost:9090/poormansRateLimit/api/admin/ws/analytics
# (requires: npm install -g wscat)

# In browser DevTools, check Network tab → WS filter
```

### Rate Limits Not Working
```bash
# Verify rules loaded in gateway
docker compose logs gateway | grep "Loaded.*rate limit rules"

# Check Redis for rules
docker exec -it rate-limiter-gateway-redis-1 redis-cli -a dev-only-change-me
> KEYS rate_limit_rules:*
> HGETALL rate_limit_rules:<uuid>

# Manually refresh rules cache on gateway
curl -X POST http://localhost:9090/poormansRateLimit/api/admin/rules/refresh
```

## 📖 Additional Documentation

- **[QUEUEING_IMPLEMENTATION.md](QUEUEING_IMPLEMENTATION.md)** - Leaky bucket technical details
- **[JWT_RATE_LIMITING.md](JWT_RATE_LIMITING.md)** - JWT-based rate limiting guide
- **[BODY_BASED_RATE_LIMITING.md](BODY_BASED_RATE_LIMITING.md)** - Body field extraction docs
- **[AUTOMATED_QUEUEING_TESTS.md](AUTOMATED_QUEUEING_TESTS.md)** - Test specifications
- **[ADMIN_API_SECURITY.md](ADMIN_API_SECURITY.md)** - Security architecture
- **[SECURITY_AUDIT.md](SECURITY_AUDIT.md)** - Security review findings
- **[CODEBASE_OVERVIEW.md](CODEBASE_OVERVIEW.md)** - Architectural summary
- **[TEST-README.md](TEST-README.md)** - Testing guide
- **[CLAUDE.md](CLAUDE.md)** - AI assistant instructions

## 🚢 Deployment

### Service Ports
| Service | Port | Access | Purpose |
|---------|------|--------|---------|
| Gateway | 8080 | Public | Rate limiting, anti-bot, request routing |
| Admin Service | 9090 | Localhost-only | Admin APIs, WebSocket analytics |
| Frontend | 3000 | Localhost-only | Admin UI (Nginx reverse proxy) |
| Redis | 6379 | Localhost-only | Central state store |
| Test Server | 9000 | Localhost-only | Testing utility |

### Environment Variables

**Gateway Service:**
```bash
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=dev-only-change-me
TEST_SERVER_URL=http://test-server:9000
HTTPBIN_URL=http://httpbin:80
```

**Admin Service:**
```bash
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=dev-only-change-me
```

### Dockerfile Builds

Both services use multi-stage Docker builds:
1. **Build stage**: Maven build with dependencies cached
2. **Runtime stage**: Eclipse Temurin JRE 21, minimal surface area

### Production Checklist
- [ ] Secure Redis access (strong password, ACLs, private network)
- [ ] Configure CORS for specific frontend domain (no wildcard)
- [ ] Enable HTTPS with TLS certificates (Let's Encrypt, cert-manager)
- [ ] Set up Redis backups (AOF + RDB snapshots) and monitoring
- [ ] Tune Caffeine cache sizes in `AntiBotFilter.java` based on traffic
- [ ] Configure proper log aggregation for `traffic_logs` (max list size)
- [ ] Set up health checks and metrics (Spring Actuator, Prometheus)
- [ ] Implement proper authentication for admin APIs (OAuth2, JWT validation)
- [ ] Review and apply security recommendations from `SECURITY_AUDIT.md`
- [ ] Configure rate limit rules for your specific API endpoints
- [ ] Test failover scenarios (Redis down, service restarts)

## 🤝 Contributing

This is a demonstration/learning project. For production use, consider:
- Adding authentication/authorization to admin APIs
- Implementing distributed rate limiting (Redis Cluster, Hazelcast)
- Adding observability (OpenTelemetry, distributed tracing)
- Implementing proper secret management
- Adding CI/CD pipelines with security scanning

## 📄 License

Not specified - proprietary or custom license.

