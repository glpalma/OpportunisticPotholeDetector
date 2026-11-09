# ADR-0015: Dashboard accounts and sessions

## Status
Accepted.

## Context
The POC framing in `grill-progress.md` decided "no auth" for the Client
and said nothing about the dashboard. As written, anyone who can reach the
Backend could browse full frames of city streets, which conflicts with
ADR-0013.

The dashboard users are a small team. They also mark Sightings as false
positives (ADR-0016), and those marks need to say who made them.

The device API key cannot be reused: it would end up in the browser, and it
does not identify a person.

## Decision

### Accounts
- A `users` table: `id`, `email` (unique), `password_hash` (bcrypt),
  `display_name`, `enabled`, `created_at`.
- **No sign-up page and no password recovery.** A small Spring command-line
  runner creates users, disables them (`enabled = false`) and resets
  passwords.
- One role. Every enabled user can view and review.

### Login and sessions
- Spring Security with a JSON login: `POST /app/api/login` returns `200` or
  `401` (no redirects), `POST /app/api/logout`, `GET /app/api/me`.
- Sessions stored in Postgres with **Spring Session JDBC**, so redeploying
  the app does not log everyone out.
- Session cookie: `HttpOnly`, `Secure`, `SameSite=Lax`. Expires after
  **8 hours** of inactivity. No "remember me".
- **CSRF protection on**, using Spring's cookie token repository
  (`XSRF-TOKEN` cookie). Angular's `HttpClient` reads it and sends
  `X-XSRF-TOKEN` automatically for same-origin requests.
- Failed logins count toward the failed-auth rate limit (ADR-0014).

### Two security chains
- `/v1/**` — Client contract, `X-API-Key` (ADR-0009). No sessions, no CSRF.
- `/app/api/**` — dashboard, session cookie + CSRF.

A path never accepts both schemes.

### Same origin
The Angular app and `/app/api` are served from the same domain by Caddy
(ADR-0014). No CORS configuration, and cookies stay first-party.

## Consequences

### Positive
- Images are only visible to logged-in team members (ADR-0013).
- Every review mark has an author.
- Little custom code: Spring Security, Spring Session and Angular's CSRF
  support do most of the work.

### Negative
- Account management requires server access to run the CLI.
- No roles: anyone on the team can change any review mark.

### Mitigations
- Fine for a small, trusted team. An admin role or an account screen can be
  added when the team grows.
