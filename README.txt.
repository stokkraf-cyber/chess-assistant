Chess Assistant Android Project (Siap Pakai & Build APK)
===========================================================

Cara paling mudah memakai aplikasi ini:
1. Build APK via GitHub Actions:
   - Upload seluruh isi folder/ZIP ini ke repository GitHub baru.
   - Buka tab Actions -> pilih workflow "Build APK" -> klik "Run workflow".
   - Setelah selesai (~2-4 menit), unduh artifact `app-debug.apk` lalu instal di HP Android Anda.

2. Cara pakai di HP (Layar Mengambang di atas game catur):
   - Buka aplikasi "Chess Assistant".
   - Berikan izin "Tampil di atas aplikasi lain" (Overlay) jika diminta.
   - Tekan "Mulai Layar Mengambang" dan klik "Mulai sekarang" pada dialog tangkap layar.
   - Buka aplikasi game catur Anda (misal Chess.com / Lichess). Kartu layar mengambang & panah
     akan muncul di atas layar. Tekan lama pada kartu mengambang untuk menutupnya.

Catatan penting:
- Untuk analisis bidak nyata dari layar dan hitungan mesin Stockfish asli:
  * Taruh model TensorFlow Lite di: app/src/main/assets/chess_piece_classifier.tflite
  * Taruh binary Stockfish ARM64 di: app/src/main/jniLibs/arm64-v8a/libstockfish.so
- Jika kedua file tersebut belum ditambahkan, aplikasi tetap bisa di-build menjadi APK dan
  menampilkan layar mengambang + panah saran langkah di atas layar.
