# ADR-0012: Hosting, rate limiting, backups and stress test

## Status
Accepted.

## Context
The POC needs one public HTTPS endpoint for the Client contract and the
dashboard, on a budget of GitHub Student Developer Pack credits, run by a
small team.

## Decision

### Server
- One **DigitalOcean droplet (~2 GB RAM)**, paid with the GitHub Student
  Developer Pack credit ($200 for one year).
- A domain from the Student Pack's free one-year domain offers (extension
  not chosen yet), used for HTTPS.
- **Docker Compose** with three services:
  - **Caddy** — automatic HTTPS; proxies `/v1/*` and `/app/api/*` to the
    Spring Boot app; serves the built Angular app (with fallback to
    `index.html` for client-side routes). It never serves the image folder.
  - **Spring Boot app**.
  - **Postgres**.
- Named volumes for the database and for images.
- Firewall: only 80, 443 and SSH open.

### Rate limiting
- A Spring servlet filter with **Bucket4j** (in-memory). Not in Caddy, which
  would need a custom build with a plugin.
- Per client IP: all `/v1/` requests 10/s with a burst of 20; failed
  authentication (`401` on `/v1/`, and failed dashboard logins) 10 per minute.
- Exceeding a limit returns `429` with `Retry-After`. The Client waits at
  least that long (ADR-0007).
- Spring reads the real client IP from Caddy's forwarded headers
  (`server.forward-headers-strategy=native`).
- Limits and an on/off switch are environment variables, so they can be
  relaxed for stress tests.

### Backups
- DigitalOcean weekly droplet backups only, for now.
- A nightly off-site copy (database dump + images) is postponed.

### Disk
- ADR-0002 estimates ~250 MB of images per bus per night; with 6-month
  retention (ADR-0011) that is ~45 GB, close to the droplet's disk.
- Measure real nightly volume first. If needed, attach a DigitalOcean block
  storage volume for images or shorten retention.

### Stress test
- **k6** run from a temporary droplet in the same region, against the real
  upload endpoint, with rate limits relaxed. Destroy the droplet afterwards.

## Consequences

### Positive
- One machine, one Compose file, HTTPS without manual certificates.
- Same-origin dashboard and API: no CORS, simple cookies (ADR-0013).
- Cost is covered by credits for the POC year.

### Negative
- Single point of failure; the Client simply retries the next night.
- Weekly backups can lose up to a week of data.
- Per-IP limits treat a whole depot behind one NAT as one client.

### Mitigations
- Nothing on the Client is deleted until the Backend acknowledges it
  (ADR-0003), so a Backend outage delays data rather than losing it.
- When more buses are added, key the request limit by API key /
  `client_id` instead of IP, and keep the per-IP limit for failed auth.
- The off-site nightly copy is the next step if the data becomes valuable.
