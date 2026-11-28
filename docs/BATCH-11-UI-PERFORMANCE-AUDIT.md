# Batch 11 — UI and performance audit

Implementation checkpoint, not release approval.

Research display uses an atomic status/monotonic refresh snapshot, a two-second refresh gate, and separate legacy SMA labeling. Refresh time is not signal generation time. Pilot BTC/ETH only. Direction is research, not an executable order.

Depth pending budget: 8192 levels per symbol, 64 deltas; overflow invalidates epoch. Liquidity cache invalidates on accepted delta and clear. Unchanged ticker rows and text may be reused. No measured CPU or RAM improvement is claimed.

Remaining gates: verify exact HEAD CI and APK; measure PSS and process CPU on Redmi Note 7 over 24–72h, screen on/off and reconnect; bound WebSocket callback backlog without losing sequence integrity; stop/restart research feeds safely; move expensive analysis off main thread; verify all tabs, accessibility, mode isolation and authentication. Batch 10 audit remains open. No new REAL entry authorization. Roadmap count is not production readiness.
