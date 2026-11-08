# SECURITY.md

## Aturan Kredensial

- API Key/Secret Binance **milik pengguna sendiri** — tidak pernah ada di source code, APK, atau repo publik.
- Produksi: API Key dimasukkan lewat APK Android, disimpan terenkripsi di perangkat (`EncryptedSharedPreferences`/Android Keystore) — mulai Fase 10.
- Pengembangan lokal: boleh diisi sementara di `.env` (bukan `.env.example`), yang **sudah** masuk `.gitignore`.
- Komunikasi APK ↔ backend: localhost saja, token unik per instalasi — tidak ada port yang dibuka ke internet.
- Audit log tidak pernah mencatat nilai API Secret/token secara utuh (lihat `describeConfigForLog` di `src/core/config`).

## Apa yang TIDAK BOLEH masuk repo publik

API Key/Secret, password, token autentikasi, file `.env` berisi rahasia,
data pribadi pengguna, database trading tanpa enkripsi. Lihat `.gitignore`.

## Backup Data Sensitif

Backup database trading (jika sudah ada, mulai Fase 9) wajib terenkripsi
(mis. AES-256 dengan passphrase pengguna) dan disimpan di repo/storage
**privat**, terpisah dari repo kode publik. Tidak pernah diunggah ke repo publik.

## LIVE MODE

`BOT_MODE=live` diblokir di level kode sampai seluruh checklist Fase 12
(`TEST_PLAN.md`) dinyatakan lulus dan pengguna menyetujui aktivasi secara
eksplisit.

## Melaporkan Kerentanan

Proyek belum punya saluran pelaporan publik (repo belum dipublikasikan).
Sebelum repo publik, catat temuan keamanan langsung di `TODO.md`/issue
tracker privat. Bagian ini akan diisi (alamat kontak/proses disclosure)
sebelum Fase 12 (rilis publik).

## Disclaimer Risiko

Tidak ada jaminan profit atau win rate tertentu. Pengguna bertanggung jawab
penuh atas API Key, dana, dan keputusan trading miliknya sendiri.
