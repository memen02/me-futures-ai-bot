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
