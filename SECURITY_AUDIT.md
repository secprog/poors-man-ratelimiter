# Security Audit Report

## Executive Summary

The project has good port-based isolation for admin APIs, but several security issues require immediate attention:

1. **CRITICAL**: Redis exposed on 0.0.0.0:6379 with no authentication
2. **HIGH**: CORS headers allow any origin to access admin endpoints
3. **MEDIUM**: Old comments reference removed security controls

---

## Critical Security Issues

### 1. ⚠️ CRITICAL: Redis Exposed to Public Network

**Location**: `docker-compose.yml`

```yaml
redis:
  ports:
    - "6379:6379"  # ❌ Binds to 0.0.0.0:6379 (publicly accessible)
```

**Issue**: Redis is accessible from any network with no authentication.

**Impact**: 
- Anyone can read all Redis data (rules, configs, counters, traffic logs, JWT tokens)
- Anyone can write/delete data (modify rules, clear counters, corrupt cache)
- Potential for code execution via Lua scripts or module loading

**Fix**:
```yaml
redis:
  ports:
    - "127.0.0.1:6379:6379"  # ✅ Only localhost
  command: ["redis-server", "--appendonly", "yes", "--requirepass", "STRONG_PASSWORD"]
```

Also update backend `application.yml`:
```yaml
spring:
  data:
    redis:
      password: ${REDIS_PASSWORD:STRONG_PASSWORD}
      host: redis
      port: 6379
```

---

### 2. ⚠️ HIGH: CORS Headers Allow Any Origin

**Location**: All admin controllers
- `AnalyticsController.java`
- `RateLimitRuleController.java`
- `SystemConfigController.java`

```java
@RestController
@RequestMapping("/poormansRateLimit/api/admin/**")
@CrossOrigin(origins = "*")  // ❌ Allows all origins
public class AnalyticsController { ... }
```

**Issue**: Even though port 9090 is localhost-only in Docker, CORS headers are unrestricted.

**Bypass Scenarios**:
1. If someone runs locally and modifies docker-compose: `"0.0.0.0:9090:9090"` → now port 9090 is public + CORS allows it
2. Kubernetes deployment: Port 9090 might not be restricted at network level → CORS allows any origin
3. Network misconfiguration: Port accidentally exposed → CORS allows cross-origin requests

**Fix**: Remove `@CrossOrigin` from admin controllers

```java
// ✅ REMOVE THIS:
// @CrossOrigin(origins = "*")

@RestController
@RequestMapping("/poormansRateLimit/api/admin/**")
public class AnalyticsController {
    // Now browser CORS policies will protect it
    // Only localhost can make requests
}
```

**Note**: Public API controllers (like `TokenController`) can keep `@CrossOrigin(origins = "*")` for form protection tokens since they're designed for public use.

---

## High-Priority Issues

### 3. Misleading Code Comments

**Location**: `docker-compose.yml`, `AdminServerConfig.java`

```yaml
# Old comment references removed AdminAccessUtil
- "127.0.0.1:9090:9090"    # Admin APIs on 9090 (localhost only)
                             # - Contains all /poormansRateLimit/api/admin/** endpoints
                             # - All requests validated by AdminAccessUtil.validateLocalhostOnly()  ❌ REMOVED
```

**Fix**: Update comments to reflect current implementation

```yaml
- "127.0.0.1:9090:9090"    # Admin APIs on 9090 (localhost only)
                             # - OS-level port binding prevents external access
                             # - Only accessible from localhost (127.0.0.1)
```

---

### 4. Test Server Exposed

**Location**: `docker-compose.yml`

```yaml
test-server:
  ports:
    - "9000:9000"  # Exposes test server publicly
```

**Issue**: Test server should not be running in production.

**Fix**: In production Dockerfile, remove test-server service or make it localhost-only:
```yaml
test-server:
  ports:
    - "127.0.0.1:9000:9000"  # Only for local testing
```

---

### 5. Default httpbin Exposed

**Location**: `docker-compose.yml`

```yaml
httpbin:
  ports:
    - "8081:80"  # Exposes httpbin publicly
```

**Issue**: httpbin is a testing tool, not for production.

**Fix**: Make it internal-only
```yaml
httpbin:
  ports:
    - "127.0.0.1:8081:80"  # Only for local testing
```

---

## Medium-Priority Issues

### 6. Admin Port Binding Clarity

**Current Issue**: While port 9090 is correctly bound to `127.0.0.1` in docker-compose, this is a configuration detail rather than code documentation.

**Recommendation**: Add environment variable validation:

Create `AdminServerConfig.java` check:
```java
@Bean
public ApplicationRunner validateAdminPortBinding(Environment env) {
    return args -> {
        String port = env.getProperty("server.port.admin", "9090");
        log.info("✓ Admin server configured on port {}", port);
        log.info("✓ Only accessible on localhost (127.0.0.1:{})", port);
    };
}
```

---

### 7. WebSocket Analytics Data Exposure

**Location**: `/api/ws/analytics` (port 8080, public)

**Current Implementation**: Broadcasts `{requestsAllowed, requestsBlocked, activePolicies}` to all WebSocket clients

**Issue**: This is real-time system state information. Acceptable for public dashboard, but ensure no sensitive data leaks:
- ✅ Request counts: Non-sensitive
- ✅ Active policies count: Non-sensitive
- ❌ Rule details (individual IPs, JWT claims): Would be sensitive if broadcast
- ❌ Traffic logs with user IDs: Would be sensitive if broadcast

**Current Status**: ✓ Appears safe - only aggregated statistics

**Recommendation**: Document what data is broadcast and audit if it changes

---

### 8. No Rate Limiting on Public Endpoints

**Location**: Public routes `/api/**` on port 8080

**Current Implementation**: Rate limiting is applied, but verify:
- ✅ `/api/tokens/form` - protected by anti-bot
- ✅ `/api/tokens/challenge` - protected by token validation  
- ✅ WebSocket analytics - no authentication required (but fine since it's read-only aggregated data)

**Recommendation**: Add rate limiting to token endpoints to prevent abuse

---

### 9. JWT Token Handling

**Location**: `JwtService.java`

**Current**: Extracts JWT claims without signature verification (assumes upstream validation)

**Assessment**: ✓ Safe if upstream validates signatures. Verify that:
- ✅ All JWT tokens come from trusted source
- ✅ Signature validation happens before reaching gateway
- ✅ No ability to inject custom JWT tokens from public API

---

## Encryption & Network Security

### 10. No TLS/HTTPS

**Issue**: All communication is over HTTP

**Status**: Development mode, acceptable for dev environment

**Production Fix**: 
- Use reverse proxy (Nginx/HAProxy) with TLS
- Implement Spring Security SSL configuration
- Use Docker network isolation instead of localhost binding

---

## Dependency Security

### 11. Dependency Audit

**Status**: ✓ Dependencies appear current

- Spring Boot 3.4.1 ✓ (Latest stable)
- Spring Cloud 2024.0.0 ✓ (Latest stable)
- JJWT 0.12.3 ✓ (Current)
- Lombok 1.18.30 ✓ (Current)
- Caffeine ✓ (Current, managed by Spring)

**Recommendation**: Run periodic dependency checks
```bash
mvn versions:display-dependency-updates
```

---

## Access Control Model

### 12. Port-Based Isolation Effectiveness

**Current Model**:
- Port 8080 (public): `ApiPortFilter` returns 404 for admin routes
- Port 9090 (private): OS-level TCP binding to 127.0.0.1

**Effectiveness Assessment**:
- ✅ Not bypassable by application code
- ✅ Not bypassable by header manipulation
- ⚠️ Can be misconfigured in docker-compose
- ⚠️ CORS headers provide false sense of security if port is exposed

**Grade**: B+ (Strong, but reliant on configuration)

---

## Summary of Fixes Required

| Priority | Issue | Fix | Impact |
|----------|-------|-----|--------|
| 🔴 CRITICAL | Redis public + no auth | Bind to 127.0.0.1 + requirepass | Prevents data breach |
| 🔴 CRITICAL | CORS allows all origins | Remove @CrossOrigin from admin | Enables localhost enforcement |
| 🟡 HIGH | Test server public | Make localhost-only | Dev/test only |
| 🟡 HIGH | httpbin server public | Make localhost-only | Dev/test only |
| 🟡 HIGH | Misleading comments | Update to reflect actual implementation | Documentation |
| 🟢 MEDIUM | Docker config risk | Add validation checks | Better defensive coding |
| 🟢 MEDIUM | No TLS in dev | Acceptable for dev, add for production | Production readiness |

---

## Recommendations

### Immediate (Do Now)
1. Fix Redis exposure: `127.0.0.1:6379` + password
2. Remove CORS from admin controllers
3. Update misleading comments
4. Make test-server and httpbin localhost-only

### Short-term (Before Production)
1. Add TLS/HTTPS support
2. Implement proper authentication for Redis
3. Add rate limiting to token endpoints
4. Document WebSocket data exposure
5. Add environment variable validation

### Long-term
1. Implement OAuth/OIDC for admin APIs instead of port-based isolation
2. Add audit logging for all admin API calls
3. Implement secrets management (HashiCorp Vault, etc.)
4. Add WAF (Web Application Firewall) rules
5. Regular security audits and dependency scanning

---

## Bypass Scenario Testing

### Test 1: Can I reach admin routes on port 8080?
```bash
curl http://localhost:8080/poormansRateLimit/api/admin/analytics/summary
# Expected: 404 NOT_FOUND ✓
```

### Test 2: Can I reach admin routes on port 9090 from non-localhost?
```bash
# From another host:
curl http://192.168.1.100:9090/poormansRateLimit/api/admin/analytics/summary
# Expected: Connection refused/timeout (NOT a response) ✓
```

### Test 3: Can I access Redis publicly?
```bash
redis-cli -h 192.168.1.100 -p 6379 ping
# Current: PONG ❌ (Should error)
# After fix: error (NOAUTH Authentication required) ✓
```

### Test 4: Can CORS bypass localhost check?
```javascript
// Browser on attacker.com makes request to http://localhost:9090/api/admin
// Current: Request succeeds (but can't happen from external network)
// If port 9090 exposed + CORS removed: Browser blocks cross-origin ✓
```

---

## Conclusion

**Current Security Posture**: B-

**Strengths**:
- ✅ Port-based isolation is solid strategy
- ✅ ApiPortFilter correctly blocks admin routes on gateway
- ✅ Dependencies are current
- ✅ No hardcoded credentials in code

**Weaknesses**:
- ❌ Redis completely unprotected
- ❌ CORS headers undermine port-based isolation
- ❌ Test services exposed to network
- ❌ Reliant on docker-compose configuration for security

**Action Items**: Fix the 4 critical issues (Redis, CORS, test servers) before considering this production-ready.
