# Batch 12 — Access integration audit

Verified source entry points: StartupActivity, MainActivity and TokenDetailActivity
all extend security/SecureActivity. Existing SecureActivity manages AppLock,
screen-off locking, protected dialogs and screenshot protection.

The new AuthenticationRuntime and LocalAccessGate are not yet connected to
these activities. Existing AppLock is not backend account authorization.

Integration requirements:
1. Establish one application-scoped authentication runtime with a trusted HTTPS origin.
2. Route startup to authentication before protected navigation; prevent direct
   MainActivity and TokenDetailActivity entry without a valid account session.
3. Recheck session on resume and while protected UI is visible. Keep local
   AppLock separate from account authorization; disabling optional AppLock
   must not disable account authorization.
4. Require authoritative account/license/device authorization before new
   trading entries. Local biometric unlock alone is insufficient.
5. On logout, expiry or revocation, deny new entries but preserve existing
   position reconciliation, cancellation and protective exit operations.

Do not enable REAL entries during this integration. Final release policy permits
eligible users to opt into DEMO or REAL without per-user mandatory demo duration.

Blockers: production HTTPS origin, session renewal/revocation protocol,
activity/service integration, CI and device tests. No release readiness claim.
