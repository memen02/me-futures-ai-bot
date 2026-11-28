# Distribusi dan izin ME Project

APK distribusi tetap satu paket Android. Sesudah instalasi, aplikasi menampilkan kode perangkat. Kirim kode tersebut langsung kepada pemilik melalui saluran yang Anda pilih. Pengelolaan izin ada di GitHub milik pemilik. Aplikasi mengambil keputusan baca-saja melalui HTTPS tanpa token. Kode berasal dari sidik jari kunci publik instalasi Android Keystore. Menghapus data atau memasang ulang aplikasi dapat menghasilkan kode baru.

## Bagi pemilik

Masuk ke GitHub sebagai memen02. Di [editor izin](https://github.com/memen02/me-futures-ai-bot/releases/edit/izin-perangkat-v1), pertahankan baris pertama `ME-PROJECT-ACCESS-V1`. Tambahkan kode perangkat sepanjang 64 karakter heksadesimal kecil pada baris tersendiri lalu simpan pembaruan release. Untuk mencabut, hapus baris perangkat dan simpan. Biarkan tag, penulis dan release aslinya tetap sama. Gunakan daftar kosong jika seluruh perangkat harus terkunci. Editor hanya dapat dipakai oleh akun dengan izin tulis. Pada pendataan 28 November 2026, hanya memen02 yang memiliki akses tersebut. Pertahankan pengaturan kolaborator ini agar administrasi tetap eksklusif.

Daftar baca publik hanya berisi sidik jari. Simpan nama, nomor telepon dan catatan pemilik secara privat. Kesalahan format membuat verifikasi gagal tertutup. Pada aplikasi, ketuk **Periksa izin** setelah pemilik menyimpan perubahan. Pengecekan manual dibatasi agar tidak menghabiskan kuota.

## Perilaku APK resmi

- Proses baru dimulai terkunci. Membuka dasbor dari notifikasi atau pemulihan aktivitas juga memerlukan izin.
- Saat aktif, aplikasi mengecek ulang setelah sekitar empat menit. Izin terakhir hanya berlaku maksimal lima menit menurut jam monotonik perangkat. Pemeriksaan yang gagal langsung mengunci akses setelah kegagalan diketahui. Cache GitHub dan latensi jaringan dapat menambah jeda sebelum data terbaru tersedia.
- Setelah akses dicabut, aplikasi memblokir entry Paper otomatis dan entry Demo baru. Rekonsiliasi serta perlindungan posisi lama tetap tersedia untuk layanan yang memantaunya.
- GitHub membatasi permintaan API anonim per alamat IP. Banyak perangkat pada satu jaringan dapat terkena pembatasan. APK menampilkan status verifikasi gagal dan bisa mencoba kembali.
- Verifikasi ada di klien APK resmi. Versi lama yang sudah beredar dan salinan yang dimodifikasi berada di luar kendali izin ini. Kunci penandatanganan APK dan kredensial Binance tetap terpisah dari sistem izin.

GitHub Actions menguji sumber dan membuat artefak Android. APK publik untuk instalasi ditandatangani dengan sertifikat distribusi yang sama dan diverifikasi sebelum diunggah ke GitHub Releases. Tidak ada private key penandatanganan di repository.
