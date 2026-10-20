# CasThor RTSP 🦫⚡

**CasThor RTSP 1.0.2 (Security Edition)** is a professional, immersive, edge-to-edge Android RTSP streaming application optimized for modern devices (like the Moto G85). It transforms your Android device into a high-performance RTSP security camera.

## 🚀 Features

*   **Security Profile Streaming:** Optimized for surveillance with a GOP (I-Frame interval) of 1 second for fast recovery during packet loss.
*   **On-Screen Display:** Professional HUD showing the live RTSP URL with real-time IP updates, ready to copy into VLC, OBS Studio or an NVR.
*   **Hardware FPS Enforcement:** Guarantees precise frame rates by explicitly restarting the camera sensor during stream initialization.
*   **True Edge-to-Edge UI:** Immersive landscape mode utilizing `FLAG_LAYOUT_NO_LIMITS` for a seamless experience without black bars.
*   **Stealth Mode / Dimmer:** One-tap screen brightness reduction (5%) to save battery and reduce visibility during continuous recording.
*   **High-Quality Audio:** Transmits at 44.1kHz, 128kbps with built-in Echo Cancellation and Noise Suppression (toggled via hardware mute).
*   **Persistent Configuration:** Remembers your stream settings (Resolution, FPS, Port) across sessions. Credentials are deliberately *not* stored.
*   **Credential Safety:** RTSP credentials are never written to disk, and `android:allowBackup="false"` blocks plain-text credential extraction via ADB backups.

## 🛠️ Build Instructions

1.  Clone the repository:
    ```bash
    git clone https://github.com/washaka81/CasThor-RTSP.git
    ```
2.  Open the project in **Android Studio** (Koala or newer recommended).
3.  Ensure you have the Android SDK 34 installed.
4.  Build and run the project on your device:
    ```bash
    ./gradlew assembleDebug
    ```

### Release Signing
To build the signed release version, ensure you have generated your `casthor.keystore` and placed it in the root directory. Update the `app/build.gradle` file with your specific keystore passwords, then run:
```bash
./gradlew assembleRelease
```

## 📡 Usage (VLC / NVR)

Once the app is running and broadcasting, you can connect to the stream using any RTSP-compatible client (like VLC Media Player, OBS Studio, or a hardware NVR).

*   **RTSP URL:** `rtsp://[user]:[pass]@[ip]:[port]/stream`

The server publishes a single video track and a single audio track, and it does not discriminate on the request path, so `/live` is accepted as well and resolves to the same stream.

*Example:* `rtsp://admin:1234@192.168.1.100:8554/stream`

## 🛡️ Security Audit
This project has been audited to prevent plain-text credential extraction via ADB backups (`android:allowBackup="false"`). RTSP credentials are held in memory only and are never persisted to disk; if a user name is set without a password, the app refuses to start the stream instead of publishing it unauthenticated.

## 💖 Support the development
[ko-fi.com/washaka81](https://ko-fi.com/washaka81)

---
*Built with power, designed for security.*
