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
- 3f56f6d: conditional session installation using existing generation rejects grants if session invalidation occurs after revision capture. cf152b7: atomically clear and capture revision before remote revocation. Remaining: invalidation before worker starts, UI closure, and regression tests.
- Verify backend revocation/session replacement and uncertain mutation reconciliation; no automatic mutation retries.
- Review existing logout paths before further edits. Preserve immediate local logout and protective trading exits.
- Add regression coverage for App Lock, owner actions, concurrent requests and session invalidation.
- Only advance to trading authorization audit after documenting verified results. No release approval.
