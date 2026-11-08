# TEST_PLAN.md

## Prinsip

Tidak ada klaim "selesai" tanpa bukti uji. Setiap fase punya kriteria uji
sendiri (lihat PROJECT_MASTER.md §Rencana 12 Fase). Checklist di bawah ini
**wajib lulus semua** sebelum `BOT_MODE=live` dibuka untuk pengguna (saat ini
diblokir di level kode, lihat SECURITY.md).

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

## Strategi Pengujian per Lapisan

- **Unit test** — logika murni (indikator, config, risk sizing) via `node --test`, tanpa koneksi jaringan.
- **Integration test** — terhadap Binance Futures **Testnet**, bukan live.
- **Backtest / out-of-sample / walk-forward** — sebelum parameter strategi baru dipakai live (Fase 7).
- **Paper trading** — simulasi penuh tanpa order sungguhan, sebagai jembatan sebelum Testnet.
