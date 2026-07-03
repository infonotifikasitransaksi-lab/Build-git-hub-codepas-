#!/bin/bash
# Script untuk membangun APK secara lokal atau di lingkungan seperti Codespaces

echo "Memulai proses build APK..."

# Memastikan gradlew memiliki izin eksekusi jika ada
if [ -f "./gradlew" ]; then
    chmod +x ./gradlew
    ./gradlew assembleDebug
else
    # Menggunakan perintah gradle sistem jika gradlew tidak ditemukan
    gradle assembleDebug
fi

if [ $? -eq 0 ]; then
    echo "=================================================="
    echo "Build Berhasil!"
    echo "APK Anda dapat ditemukan di:"
    echo "app/build/outputs/apk/debug/app-debug.apk"
    echo "=================================================="
else
    echo "=================================================="
    echo "Build Gagal! Silakan periksa kesalahan di atas."
    echo "=================================================="
    exit 1
fi
