# Batch 13 — Security & Integration Audit

Status: IN PROGRESS; no release approval.

## Changes committed
- a16bedf / 86ae494: role-specific protected Activity checks.
- 9156951: protected owner license/device dialogs and confirmation checks.
- a4d18b8: close owner host on pause and recreate on resume.
- b4ad0fa: bind owner controller to original session generation.

## Outstanding
- Verify regression tests and Android CI against latest code commit; connector only lists PR-triggered workflow runs. Push workflow configured, result not confirmed.
- Worker cancellation cannot undo server-accepted mutations. Verify server authorization, revocation and reconciliation; do not blindly retry uncertain mutations.
- Audit logout/session expiry and new order authorization independently of UI access; preserve protective exits and reconcile active positions.
- Audit license expiry and single-device enforcement on backend.
- Device tests and documentation reconciliation remain pending.

No Mainnet execution policy or TP/SL behavior changed in these commits.

## Step 6 follow-up
- f985224: guard authentication UI actions.
- c6e0b6f: handle revocation exceptions and reject invalid grants locally.
- Remaining: failed revocation clears local token, so a subsequent login may proceed without confirmation of old token revocation. Backend session replacement/expiry must be verified; do not mark this resolved.
- Concurrent login/logout, background login completion, and regression tests remain open. CI not confirmed.
- cd4424c: reuse owner host cleanup on logout and handle expiry during resume.
- 55628f9: guard owner mutations and pagination using existing protected UI gate.
- 8a48104: resume account entry after App Lock unlock.
- c11ff9b: suppress login navigation while protected UI is inactive; session installation is not yet guarded.
- d8c0e87: serialize owner controller requests within the process; server-side completion remains independent.
- f806981: centralize immediate local owner logout in AuthenticationRuntime; other logout paths still need review.

## Next audit checkpoint
- Verify all constructor call sites and compile/test the latest branch; no CI success claimed.
- 3f56f6d: conditional session installation using existing generation rejects grants if session invalidation occurs after revision capture. cf152b7: atomically clear and capture revision before remote revocation. 658a7bf: capture expected revision at UI submission and reject stale queued login before clearing session. Remaining: UI closure, non-UI callers, and regression tests.
- Verify backend revocation/session replacement and uncertain mutation reconciliation; no automatic mutation retries.
- Review existing logout paths before further edits. Preserve immediate local logout and protective trading exits.
- Add regression coverage for App Lock, owner actions, concurrent requests and session invalidation.
- Only advance to trading authorization audit after documenting verified results. No release approval.

## Authentication view closure follow-up
- 2f85908: check originating view closure before login and conditional installation.
- Serialize closure flag with session installation using the existing session monitor. This orders closure and installation, but does not revoke a session installed before closure or address background pause. Verify lifecycle behavior and regression tests before approval.

- Cancel pending login on Startup pause/close, conditionally clear an installed grant using its expected revision. Review revision ownership assumptions, biometric navigation lifecycle, remote revocation and tests; not yet verified.

- Distinguish individual UI login attempts by object identity, not revision alone, so a canceled callback cannot match a later attempt with the same revision. Remaining: explicit installed-grant ownership instead of revision arithmetic, biometric lifecycle, revocation, logout call sites and CI.

## Face unlock integration
- Existing automatic App Lock prompt supports Android strong face biometrics where exposed by the OS. Enable device credential fallback on API 30+ without weakening biometric class. No custom camera or face storage. Android 28–29 retain existing fingerprint path. Verify on actual devices, including face availability and cancellation; no guarantee of face-only selection.

## Owner approval release gate
- d81b43e: align App Lock prompt authenticator mask with availability check.
- 8bdd417: require backend account in non-dev builds even when AUTH_ORIGIN is blank; dev-only bypass retained. Release must have deployed trusted HTTPS backend and server-side approval, license, device binding and revocation enforcement. Verify configured and unconfigured build variants and direct activity entry. CI and device tests pending.

- Replace cancellation revision arithmetic with explicit grant ownership by login attempt; stale queued runtime login rejected before locking access gate. Verify existing positional callers and regression tests; biometric handoff and revocation remain open.

- Require active protected UI before biometric navigation unlocks account access; cancel navigation prompt on Startup stop. Device lifecycle compatibility and regression tests pending.
- Future iOS target: reuse backend approval/licensing/device contracts, separate native client and platform-specific biometric adapter. No iOS implementation in current Android batch.

## Future cross-platform Owner access
- One authoritative Owner account and shared backend for Android and iOS administration. Proposed Owner-only exception: two individually approved devices (Android and iOS), independently authenticated and revocable; ordinary USER remains limited to one device. Confirm device-limit policy before implementing. No client-side role bypass or shared device keys. iOS implementation deferred until Android validation.

- Owner clarification: replace the proposed two-device exception with migration of the same Owner account from Android to iOS (or reverse). Retain one active approved device; require secure recovery/transfer and revoke old device after verified migration. Shared backend data remains intact. This supersedes the two-device proposal; implementation deferred.

- Owner panel host now starts only after protected UI is unlocked, using existing unlock lifecycle hook. No directory refresh while App Lock blocks entry. Regression and device tests pending.

- Distribution target: trading client and Owner panel available on Android and iOS; shared backend, server-enforced roles and device migration. Separate Owner app packaging is a future design option, not implemented.

- Validate login input before clearing existing session or sending revocation. Invalid local credentials must not destroy an existing session. Runtime access gate still locks on attempted login; test pending. Failed server revocation persistence remains open.

## CI regression repair
- d0d627b: DeskNavigationTest uses approved USER fixture and clears local session after tests; production account gate unchanged. Five failed runs (46168623045, 46168895372, 46169001067, 46169319227, 46169447156) each had 2 Debug navigation failures and 932 passing DEV tests. Await fresh CI on repair commit; do not rerun old commits as validation.

## Step 6 — verified CI checkpoint
- 09eb625: Real mode navigation fixture; run 46171909694 SUCCESS.
- ba24689: validate login input before locking local gate; run 46172676165 SUCCESS.
- 659751c: regression test invalid/stale login preserves approved session and wipes password; run 46172796248 SUCCESS.
- Remaining: failed remote revocation handling across role changes and process restart; backend expiry/device enforcement; Owner panel lifecycle audit and device tests. Do not treat local logout as confirmed server revocation.

## Step 6 — lifecycle follow-up
- Track cancelled login grants before releasing session lock; guard owner refresh and UI callbacks; skip closed worker requests; recheck cancellation after revocation I/O.
- Static review only; CI and regression coverage pending. In-flight requests cannot be undone by UI closure. Pending revocations remain memory-only; server expiry and deployment verification remain required.

## Revocation regression checkpoint
- Runs 46174126594, 46174234529, 46174244487 passed.
- Added repository tests for cross-role/shared-session revocation, logout failure, cancellation during I/O, invalid input. New CI pending.
- Process restart and deployed server verification remain open; no persistent token storage introduced.

## Server session boundary coverage
- Added tests for exact expiry, failed owner login, expired admin mutation, service recreation after revocation.
- These tests exercise the local backend database, not Android process death or deployed HTTPS. Owner device binding and offline revocation across restart remain release blockers. CI pending.

## HTTP adapter follow-up
- Reject nonstandard JSON constants and invalid route types; regression coverage for malformed logout payload and storage failure propagation.
- Runs 46174912685 and 46174912720 passed. New tests pending CI. Hosting layer must map unexpected storage failures to non-success responses without leaking details.

## Account storage follow-up
- Runs 46175063428 and 46175063451 passed. Reject malformed stored password hashes before scrypt; cover invalid entitlement expiry and owner entitlement isolation. Remove unused import.
- New CI pending; no schema or production credential migration. Owner device proof and Android restart revocation remain open.

## Atomic revocation follow-up
- Session replacement and repository logout now retain token under the session lock before network I/O. Rejected grants retained before revoke; added clearAndRemember regression test.
- Run 46175248721 passed. New Android CI pending. Role adapter rejection and process-death persistence remain open; no plaintext token persistence introduced.

## Role rejection follow-up
- Runtime role adapters retain rejected tokens before revoke. Conditional installation fails while pending revocations exist. Added two regression tests.
- Android process restart still loses memory-only pending tokens; owner device proof and deployed HTTPS remain open. CI pending.

## Owner device proof foundation
- Existing binding/challenge/proof path supports enrolled Owner; password-only login denied once bound. Regression covers proof and nonce replay.
- No production Owner enrollment or recovery endpoint yet; Android Owner client still needs verified route before enrollment. Do not manually enroll production Owner until client and recovery are ready. Unenrolled Owner password-only path remains for bootstrap compatibility. CI pending.

## Owner recovery foundation
- Runs 46176548576 and 46176548495 passed. Added hashed random recovery credential, trusted offline provisioning and atomic recovery (binding replacement, session/challenge revocation, credential consumption, audit).
- Offline primitive only: caller must verify new key possession; no HTTP endpoint, self-service UI, rate-limited remote recovery or secure operator deployment yet. Do not provision production Owner until verified Android login and recovery workflow are ready. New CI pending.

## Recovery rate limit follow-up
- Runs 46176742541 and 46176742484 passed. Offline recovery now uses persistent global five-attempt/300-second limit; tests cover denial without mutation and credential rotation. New CI pending.
- Offline operator must still verify new key possession. Remote recovery challenge, Android UI and secure provisioning remain open. Global limit is deliberately conservative for the single Owner; remote deployment requires denial-of-service review.

## Recovery challenge foundation
- Prior runs 46176907895 and 46176907902 passed. Added isolated 60-second challenge, signature validation, invalidation on rotation and successful recovery, and regression test. New CI pending.
- Offline compatibility without challenge remains; never expose remotely. Mandatory proof, separate signature domain, Android integration and deployment remain open.

## Mandatory recovery proof
- Removed unsigned offline recovery compatibility; all successful recovery now requires issued unexpired challenge and matching signature. Updated existing tests and added missing/expired challenge regressions. New CI pending. Separate signature domain, Android integration and deployment remain open.

## Recovery signature domain separation
- Runs 46177304016 and 46177303988 passed. Recovery uses ME-PROJECT-OWNER-RECOVERY-V1, login retains existing domain; shared verifier avoids duplicate crypto. Tests cover both directions. New CI pending. Android recovery signer and transport not yet integrated.
