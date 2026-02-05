# Testing the Rate Limiter Gateway

This directory contains comprehensive testing tools for the Rate Limiter Gateway.

## Test Components

### 1. Test Server (`test-server.py`)
A simple Python Flask server that exposes test endpoints for validating the gateway functionality.

**Endpoints:**
- `GET /api/hello` - Returns a hello world JSON response
- `POST/PUT/PATCH /api/echo` - Echoes back the request data and headers
- `GET /api/status` - Server status and request count
- `GET /health` - Health check endpoint

### 2. Test Suite (`test-gateway.py`)
A comprehensive test script that validates all gateway features.

**Tests Included:**
1. **Direct Server Access** - Verifies test server is running
2. **Gateway Routing** - Tests basic gateway routing functionality
3. **Basic GET Request** - Tests simple request pass-through
4. **Rate Limiting** - Validates rate limiting with rapid requests
5. **Anti-Bot: Valid Submission** - Tests legitimate form submission
6. **Anti-Bot: Honeypot Detection** - Tests honeypot field bot detection
7. **Anti-Bot: Timing Check** - Tests time-to-submit validation
8. **Anti-Bot: Token Reuse** - Tests one-time token enforcement
9. **Idempotency Key** - Tests duplicate submission prevention
10. **Meta Refresh Challenge** - Tests non-JS challenge mechanism

## Setup

### Prerequisites
- Python 3.7+ installed
- Docker and Docker Compose installed
- Gateway running via `docker compose up --build`

### Install Python Dependencies
```bash
pip install -r test-requirements.txt
```

Or manually:
```bash
pip install flask requests
```

## Running Tests

### Step 1: Start the Gateway
```bash
docker compose up --build
```

Wait for the gateway to be fully started (check logs for "Started GatewayApplication").

### Step 2: Start the Test Server
In a new terminal:
```bash
python test-server.py
```

The server will start on `http://localhost:9000`.

### Step 3: Run the Test Suite
In another terminal:
```bash
python test-gateway.py
```

## Gateway Configuration

The gateway is configured to route requests to the test server:

- **Gateway URL:** `http://localhost:8080`
- **Test Server Direct:** `http://localhost:9000`
- **Through Gateway:** `http://localhost:8080/test/...`

Example:
- Direct: `http://localhost:9000/api/hello`
- Via Gateway: `http://localhost:8080/test/api/hello`

## Features Tested

### Rate Limiting
The test suite sends 20 rapid requests to verify rate limiting kicks in. Depending on your configured rate limit policies, some requests should be blocked with `429 Too Many Requests`.

### Anti-Bot Protection

#### 1. Honeypot Fields
Hidden fields that legitimate users don't fill but bots do. The test fills the honeypot field to simulate bot behavior.

#### 2. Time-to-Submit Checks
Validates that forms aren't submitted too quickly (default: 2000ms minimum). Tests both valid (>2s) and invalid (<2s) submission times.

#### 3. One-Time Form Tokens
Each form submission requires a unique token obtained from `/api/tokens/form`. The test validates:
- Valid tokens are accepted
- Used tokens are rejected on reuse
- Invalid tokens are rejected

#### 4. Idempotency Keys
Prevents duplicate submissions using `X-Idempotency-Key` header. The test verifies:
- First submission succeeds
- Duplicate submission with same key returns `409 Conflict`

#### 5. Meta Refresh Challenge
No-JavaScript challenge mechanism that uses HTML meta refresh. The test verifies:
- Challenge returns HTML with meta refresh tag
- Challenge sets a cookie with the form token

## Manual Testing

You can also test manually using curl:

### Get a Form Token
```bash
curl http://localhost:8080/api/tokens/form
```

### Valid Submission (wait >2 seconds after getting token)
```bash
TOKEN="<token from above>"
LOAD_TIME="<loadTime from above>"

curl -X POST http://localhost:8080/test/api/echo \
  -H "Content-Type: application/json" \
  -H "X-Form-Token: $TOKEN" \
  -H "X-Form-Load-Time: $LOAD_TIME" \
  -H "X-Honeypot: " \
  -d '{"message": "Hello from curl"}'
```

### Trigger Honeypot Detection
```bash
curl -X POST http://localhost:8080/test/api/echo \
  -H "X-Honeypot: bot@spam.com" \
  -d '{"message": "I am a bot"}'
```

### Test Rate Limiting
```bash
for i in {1..20}; do
  curl http://localhost:8080/httpbin/get?seq=$i
  echo ""
done
```

### Get Meta Refresh Challenge
```bash
curl http://localhost:8080/api/tokens/challenge
```

## Troubleshooting

### Test Server Not Accessible
- Ensure `test-server.py` is running
- Check that port 9000 is not in use
- Verify firewall allows localhost connections

### Gateway Not Accessible
- Ensure Docker containers are running: `docker compose ps`
- Check logs: `docker compose logs gateway`
- Verify port 8080 is not in use

### Tests Failing
- Check that both test server and gateway are running
- Review gateway logs for errors
- Ensure database is initialized (check Postgres container)
- Verify rate limit policies in the database allow requests

### Network Issues (Docker)
If running on Windows/Mac, ensure Docker Desktop is configured to allow `host.docker.internal` to work properly. This allows containers to access services running on the host machine.

On Linux, you may need to use `--add-host=host.docker.internal:host-gateway` when running Docker, or modify the gateway configuration to use a different host resolution method.

## Expected Results

A successful test run should show:
- ✓ All infrastructure tests pass (server access, gateway routing)
- ✓ Basic requests work
- ✓ Valid anti-bot submissions are accepted
- ✓ Invalid submissions (honeypot, timing, reused tokens) are blocked
- ⚠ Rate limiting may or may not trigger depending on configured policies

Success rate should be 90-100% depending on your rate limit configuration.

## Customizing Tests

You can modify `test-gateway.py` to:
- Change the number of rate limit test requests
- Adjust timing thresholds
- Add custom test scenarios
- Test specific endpoints or data patterns

## Integration with CI/CD

These tests can be integrated into your CI/CD pipeline:

```bash
# Start services
docker compose up -d
sleep 10  # Wait for services to be ready

# Run tests
python test-server.py &
SERVER_PID=$!
sleep 2

python test-gateway.py
TEST_RESULT=$?

# Cleanup
kill $SERVER_PID
docker compose down

exit $TEST_RESULT
```
