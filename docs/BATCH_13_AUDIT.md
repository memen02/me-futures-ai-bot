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
