# TEST_PLAN.md

## Prinsip

Tidak ada klaim "selesai" tanpa bukti uji. Setiap fase punya kriteria uji
sendiri (lihat PROJECT_MASTER.md §Rencana 12 Fase). Checklist di bawah ini
**wajib lulus semua** sebelum LIVE MODE dibuka untuk pengguna. **Catatan
2026-11-08**: mekanisme pengunci LIVE MODE di level kode (`BOT_MODE=live`)
ada di versi Node.js lama (`backend/`, referensi, tidak dipakai lagi) dan
**belum dibangun ulang di Kotlin** — lihat SECURITY.md §LIVE MODE. Checklist
di bawah tetap wajib terpenuhi sebelum LIVE MODE dibuka, terlepas dari
mekanisme penguncinya.

## Checklist Sebelum LIVE MODE (Fase 12)

- [ ] Audit kode menyeluruh
- [ ] Backtest
- [ ] Paper Trading (`BOT_MODE=paper`)
- [ ] Binance Futures Testnet (`BOT_MODE=testnet`)
- [ ] Validasi TP/SL (via Algo Order, lihat API_SPEC.md) benar-benar tercatat di Binance
- [ ] Validasi anti-duplicate (Position Lock, Symbol Lock, idempotency)
- [ ] Validasi risk management (limit harian, max drawdown, cooldown, emergency stop)
- [ ] Validasi pemulihan setelah restart (reconciliation sebelum entry baru)
- [ ] Uji kegagalan jaringan
- [ ] Uji API error dari Binance
- [ ] Uji pemulihan database

## Fase 1 — Hasil Uji (Project Foundation)

| Uji | Hasil |
|---|---|
| `node --check` semua file inti | Lulus |
| `node --test` (7 unit test) | 7/7 lulus |
| `node src/index.js --selftest` | Boot & keluar bersih, exit code 0 |
| `npm install` di Termux | Terverifikasi — lihat Fase 2 |

## Fase 2 — Hasil Uji (Binance Futures Data Engine)

| Uji | Hasil |
|---|---|
| `node --check` semua file (exchange/, data/, scripts/) | Lulus |
| `node --test` (55 unit test kumulatif) | 55/55 lulus |
| `node src/index.js --selftest` | Boot & keluar bersih, exit code 0 |
| Signer HMAC-SHA256 vs test vector independen | Cocok |
| REST client retry/backoff (status transient, network error) | Diuji dengan mock `fetch`, lulus |
| WS client reconnect logic | Diuji dengan `WebSocket` palsu, lulus |
| Parsing `exchangeInfo`, filter PERPETUAL, validasi order vs filter | Diuji dengan fixture realistis |
| REST/WS koneksi nyata ke Binance (testnet) | **Belum** — lingkungan Claude tanpa jaringan. Jalankan `npm run check-connectivity` di Termux (lihat TODO.md) |

## Fase 10 — Hasil Uji (Android Mandiri, Increment 2: Signer + Auto-Reconnect)

| Uji | Hasil |
|---|---|
| `gradle test` (9 unit test JUnit, JVM murni) | **9/9 lulus** — bukti: GitHub Actions run **42554105921** |
| `gradle assembleDebug` | **Sukses** — APK ter-upload sebagai artifact, run yang sama |
| Signer HMAC-SHA256 vs test vector independen | Cocok (test vector sama dengan versi Node.js) |
| Backoff reconnect (`computeBackoffMs`) — naik seiring attempt, dibatasi maxMs | Lulus |
| OkHttpClient singleton (perbaikan audit #5) | Lulus — `assertSame` membuktikan instance identik, lihat `OkHttpSingletonTest.kt` |
| Install APK & buka di device nyata (Redmi Note 7, crDroid) | **Terverifikasi sekali** (sebelum 2 bug compileSdk/Kotlin-plugin diperbaiki) — **belum diverifikasi ulang** sejak itu |
| Reconnect bertahan saat app di-minimize/layar mati | **Belum ada** — perlu Foreground Service (lihat AUDIT_REPORT.md Roadmap F) |
| Auto-start setelah HP reboot | **Belum ada** — perlu Boot Receiver |
| REST/WS koneksi nyata ke Binance dari dalam APK | **Belum diuji otomatis** — MainActivity konek manual ke stream publik saat dibuka, bukan bagian test suite |

Dua bug nyata ditemukan & diperbaiki selama proses ini (bukan klaim tanpa
bukti): `compileSdk` 35→36 dan Kotlin plugin 2.0.21→2.2.21, keduanya terbukti
dari pesan error build Gradle sungguhan, dicatat di `CHANGELOG.md`.

## Strategi Pengujian per Lapisan

- **Unit test** — logika murni (indikator, config, risk sizing) via `node --test`, tanpa koneksi jaringan.
- **Integration test** — terhadap Binance Futures **Testnet**, bukan live.
- **Backtest / out-of-sample / walk-forward** — sebelum parameter strategi baru dipakai live (Fase 7).
- **Paper trading** — simulasi penuh tanpa order sungguhan, sebagai jembatan sebelum Testnet.
