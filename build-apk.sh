#!/bin/bash
# Script untuk membangun APK secara lokal atau di lingkungan seperti Codespaces

echo "Memulai proses build APK..."

# 1. Deteksi apakah Android SDK tersedia di lingkungan (seperti GitHub Codespaces)
if [ -z "$ANDROID_HOME" ] && [ ! -d "$HOME/android-sdk" ] && [ ! -f "local.properties" ]; then
    echo "=================================================="
    echo "Android SDK tidak ditemukan di Codespaces Anda."
    echo "Menyiapkan Android SDK secara otomatis (Proses ini hanya sekali)..."
    echo "=================================================="
    
    # Buat direktori SDK
    mkdir -p "$HOME/android-sdk/cmdline-tools"
    
    # Unduh Android Command Line Tools
    echo "-> Mengunduh Android Command Line Tools..."
    curl -sS -o "$HOME/android-sdk/cmdline-tools.zip" https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
    
    # Ekstrak file
    echo "-> Mengekstrak berkas..."
    unzip -q "$HOME/android-sdk/cmdline-tools.zip" -d "$HOME/android-sdk/cmdline-tools-temp"
    
    # Pindahkan ke folder struktur standar (cmdline-tools/latest/)
    mv "$HOME/android-sdk/cmdline-tools-temp/cmdline-tools" "$HOME/android-sdk/cmdline-tools/latest"
    
    # Bersihkan file sementara
    rm -rf "$HOME/android-sdk/cmdline-tools-temp"
    rm -f "$HOME/android-sdk/cmdline-tools.zip"
    
    # Setujui semua lisensi agar Gradle dapat otomatis mengunduh SDK Platform yang diperlukan
    echo "-> Menyetujui lisensi resmi Android SDK..."
    yes | "$HOME/android-sdk/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$HOME/android-sdk" --licenses > /dev/null
    
    # Buat file local.properties
    echo "sdk.dir=$HOME/android-sdk" > local.properties
    echo "=================================================="
    echo "Android SDK berhasil dikonfigurasi secara otomatis!"
    echo "Gradle sekarang akan mengunduh SDK Platform & Build Tools secara otomatis."
    echo "=================================================="
fi

# Pastikan local.properties menunjuk ke SDK jika folder lokal ada dan file belum dibuat
if [ ! -f "local.properties" ] && [ -d "$HOME/android-sdk" ]; then
    echo "sdk.dir=$HOME/android-sdk" > local.properties
fi

# Memastikan gradlew memiliki izin eksekusi jika ada
if [ -f "./gradlew" ]; then
    chmod +x ./gradlew
    
    # Periksa apakah gradle-wrapper.jar rusak atau tidak valid
    JAR_FILE="gradle/wrapper/gradle-wrapper.jar"
    if [ ! -f "$JAR_FILE" ] || ! unzip -t "$JAR_FILE" >/dev/null 2>&1; then
        echo "=================================================="
        echo "gradle-wrapper.jar terdeteksi rusak atau tidak valid (Korupsi Git LFS)."
        echo "Mencoba memperbaiki Gradle Wrapper secara otomatis..."
        echo "=================================================="
        
        if command -v gradle >/dev/null 2>&1; then
            echo "-> Menggunakan perintah 'gradle wrapper' dari sistem untuk menghasilkan berkas asli..."
            gradle wrapper
        else
            echo "-> Mengunduh ulang gradle-wrapper.jar bersih dari GitHub..."
            mkdir -p gradle/wrapper
            curl -L -sS -o "$JAR_FILE" "https://raw.githubusercontent.com/gradle/gradle/v8.5.0/gradle/wrapper/gradle-wrapper.jar"
            if [ $? -ne 0 ] || ! unzip -t "$JAR_FILE" >/dev/null 2>&1; then
                echo "-> Mencoba URL alternatif..."
                curl -L -sS -o "$JAR_FILE" "https://github.com/gradle/gradle/raw/v8.5.0/gradle/wrapper/gradle-wrapper.jar"
            fi
        fi
        
        # Verifikasi akhir
        if unzip -t "$JAR_FILE" >/dev/null 2>&1; then
            echo "-> gradle-wrapper.jar berhasil diperbaiki!"
            echo "=================================================="
        else
            echo "-> PERINGATAN: Gagal memperbaiki gradle-wrapper.jar secara otomatis."
            echo "Silakan jalankan perintah 'gradle wrapper' di terminal Anda untuk memperbaikinya."
            echo "=================================================="
        fi
    fi
    
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

