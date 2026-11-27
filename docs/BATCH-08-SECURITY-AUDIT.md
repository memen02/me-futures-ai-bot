# Batch 08 security audit — development checkpoint, not release approval

Android domain validation, session memory, repository, admin/reset preflight, HTTPS adapter and Python SQLite account/session primitives exist. Android unit tests and backend primitive tests passed at earlier checkpoints; new adapter requires CI and integration tests.

## HTTP contract (not deployed)
Trusted deployment-controlled HTTPS origin only. POST /v1/auth/register accepts username/password and returns result ACCEPTED/REJECTED/UNAVAILABLE. POST /v1/auth/login returns accountId, username, role, status, accessToken (64 lowercase hex), lifetimeSeconds on success; generic failure otherwise. POST /v1/auth/logout accepts bearer token and empty JSON, returns result APPLIED only after revocation. Never log credentials, request bodies or tokens. No HTTP listener or production origin exists. Android adapter is not wired to startup or trading.

## Blocking findings
- Implement actual HTTP service with bounded request sizes, TLS, strict JSON schema, generic errors, trusted proxy handling, deployment configuration, persistent protected database and backup/restore.
- Current login holds a SQLite write transaction during scrypt and skips hashing for unknown users: mitigate timing enumeration and lock contention, recheck account state atomically before issuing session. Bound concurrent hashing and storage. Global fixed-window limits allow denial of service; implement layered abuse protection.
- Bootstrap owner only via protected operator access, no default credentials. Admin listing, reauthentication, verified recovery channel and atomic single-use password reset are absent. Server authorization is mandatory for all privileged actions.
- Session renewal, revocation checks, process restart and reboot handling are absent. Token expiry is not license expiry. Android password serialization creates temporary immutable strings that cannot be reliably erased; minimize lifetime and prevent logging.
- Integrate startup, login/register/admin UI and service entry authorization. Local role checks are not security boundaries. On auth loss block new entries while preserving safe position reconciliation/protection; do not blindly stop risk management. Existing trading paths are not gated by these new modules.
- Test actual HTTPS transport, malformed/oversized responses, concurrency, expired/revoked sessions, recovery, admin abuse, offline and reboot behavior. No live trading release until execution and authentication gates pass.
- Hosting selection and deployment require owner decision. No production account or credentials created.

Batch target completion is a planning metric, not application readiness.
