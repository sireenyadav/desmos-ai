#!/bin/bash
set -e

REPO_URL="https://raw.githubusercontent.com/sireenyadav/desmos-ai/main"
WORK_DIR="$HOME/desmos-ai-build"

echo "[1/8] Setting up workspace..."
mkdir -p "$WORK_DIR/app/src/com/desmosai"
mkdir -p "$WORK_DIR/libs"
cd "$WORK_DIR"

echo "[2/8] Downloading DesmosAI.java from your repo..."
curl -sL "$REPO_URL/DesmosAI.java" -o app/src/com/desmosai/DesmosAI.java

echo "[3/8] Asking for Groq API Key..."
read -p "Paste your Groq API Key here: " API_KEY
if [ -z "$API_KEY" ]; then
    echo "API Key cannot be empty. Exiting."
    exit 1
fi

# Inject API Key
sed -i "s|%%GROQ_API_KEY%%|$API_KEY|g" app/src/com/desmosai/DesmosAI.java

echo "[4/8] Installing Termux dependencies..."
pkg install -y openjdk-17 aapt apksigner d8 ecj wget unzip >/dev/null 2>&1

echo "[5/8] Downloading official Android SDK from Google..."
if [ ! -f libs/android.jar ] || ! unzip -t libs/android.jar >/dev/null 2>&1; then
    # Downloading directly from Google's official repository
    wget -q "https://dl.google.com/android/repository/platform-28_r06.zip" -O platform-28.zip
    unzip -q platform-28.zip -d platform-28
    cp platform-28/android-28/android.jar libs/android.jar
    rm -rf platform-28 platform-28.zip
fi

# Verify android.jar is valid
if ! unzip -t libs/android.jar >/dev/null 2>&1; then
    echo "❌ Error: Failed to download a valid android.jar. Check internet connection."
    exit 1
fi

echo "[6/8] Generating AndroidManifest.xml..."
cat << 'EOF' > app/AndroidManifest.xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.desmosai">
    <uses-permission android:name="android.permission.INTERNET" />
    <application
        android:label="Desmos AI"
        android:icon="@android:drawable/ic_dialog_info"
        android:theme="@android:style/Theme.Material.NoActionBar">
        <activity
            android:name=".DesmosAI"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF

echo "[7/8] Compiling and Packaging..."
mkdir -p build/obj
mkdir -p build/dex_out

ecj -d build/obj -classpath libs/android.jar app/src/com/desmosai/DesmosAI.java
d8 --output build/dex_out build/obj/com/desmosai/*.class

aapt package -f -M app/AndroidManifest.xml -I libs/android.jar -F build/app.unsigned.apk
cd build
aapt add app.unsigned.apk dex_out/classes.dex >/dev/null 2>&1
cd ..

echo "[8/8] Signing APK..."
if [ ! -f debug.jks ]; then
    keytool -genkey -v -keystore debug.jks -keyalg RSA -keysize 2048 -validity 10000 -alias debug -storepass password -keypass password -dname "CN=DesmosAI" >/dev/null 2>&1
fi
zipalign -f 4 build/app.unsigned.apk build/app.aligned.apk
apksigner sign --ks debug.jks --ks-pass pass:password --key-pass pass:password --min-sdk-version 21 build/app.aligned.apk

cp build/app.aligned.apk DesmosAI.apk
cp DesmosAI.apk /storage/emulated/0/Download/

echo "✅ Build Success! DesmosAI.apk moved to Downloads."
