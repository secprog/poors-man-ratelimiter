# Rate Limiter Gateway — Codex Overview

Purpose: API gateway with configurable rate limiting, anti-bot defenses, analytics, and admin UI.

Architecture:
- Backend: Spring Boot 3 / Spring Cloud Gateway, R2DBC + Postgres for config/state, Caffeine caches for tokens and counters.
- Frontend: React (Vite + Tailwind) admin console served via Nginx container.
- Observability: simple analytics service feeding dashboards and real-time WebSocket updates.

Backend key pieces:
- `filter/RateLimitFilter.java`: enforces token bucket limits per policy/rule.
- `filter/AntiBotFilter.java`: honeypot, time-to-submit, one-time form token, idempotency keys; can consume challenge cookie.
- `controller/TokenController.java`: issues form tokens and challenges (meta-refresh, JS, Preact).
- `controller/RateLimitRuleController.java`, `RateLimiterService.java`: CRUD and enforcement logic for rate-limit policies/rules.
- `service/ConfigurationService.java`: cached system config with DB backing (`system_config` table).
- Schema seeds defaults and a demo rule/policy in `src/main/resources/schema.sql`.

Frontend key pieces:
- `src/App.jsx` with sidebar navigation (Dashboard, Analytics, Policies, Settings).
- `pages/Analytics.jsx`: Recharts visualizations + live WebSocket stats.
- `pages/Policies.jsx`: CRUD for rate-limit policies with anti-bot headers and honeypot handling.
- `pages/Settings.jsx`: system settings including anti-bot challenge type (metarefresh/javascript/preact) and difficulty inputs.
- `utils/websocket.js`: real-time analytics client, browser-safe.
- `api.js`: axios client pointed at `/api` (Nginx proxied).

Deployment/build:
- Docker: `frontend/Dockerfile` builds Vite bundle then serves via Nginx; `backend` image built via Dockerfile (not shown here). `docker-compose.yml` wires postgres, test-server, backend, frontend.
- Default ports: backend `8080`, frontend `3000`, postgres `5432`, test-server `9000`.

Anti-bot challenges:
- Meta-refresh (HTML only), JS token, and new Preact challenge that sets `X-Form-Token-Challenge` cookie then reloads after configurable seconds (`antibot-preact-difficulty`).

Testing:
- Python test suite (`test-gateway.py`, helpers) and run scripts (`run-tests.py`, `Run-Tests.ps1`); 

Recent additions:
- Preact challenge flow added to `TokenController` and surfaced in Settings UI with difficulty slider; schema seeds `antibot-preact-difficulty`.
