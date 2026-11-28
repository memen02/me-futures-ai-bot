# Batch 12 authentication audit — implementation checkpoint, not release approval

Backend primitives exist for account approval, family/monthly entitlements, expiry, renewal, device binding and replacement, P-256 proof and single-use challenge consumption. AuthService requires proof for USER login. OWNER remains password-only. Device replacement and entitlement/status changes revoke sessions and challenges.

## Outstanding release blockers
- Verify CI on the final commit; previous green eight-test run predates integration.
- Retire internal password-only USER login and migrate tests; invalidate legacy sessions before deployment.
- Make authenticated challenge issuance atomic with account and entitlement checks.
- Add owner reauthentication/MFA, recovery, admin audit review and abuse-resistant rate limiting.
- Pin and audit cryptography dependencies.
- Implement HTTPS adapter, bounded request parsing, deployment, database migrations/backups and session renewal.
- Integrate Android Keystore signing, registration, login, biometric local unlock, OWNER panel and authorization gates.
- Test concurrent challenge consumption, replacement, revocation, expiry and failure recovery.
- Verify device runtime and prevent unauthorized new trading entries while preserving existing position protection.

No deployment, APK integration, real trading unlock or security certification is claimed. Batch 12 is not complete until outstanding integration and validation are resolved.
