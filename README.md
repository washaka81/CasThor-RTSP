# CastThor - RTSP Camera Streamer

Android app that transforms your phone into an RTSP server to stream your camera.

## Features

- **RTSP Server** - Streams your camera live over the network
- **Codec Selection** - H.264, H.265/HEVC, MPEG-4 SP
- **Resolution Options** - VGA, HD, Full HD
- **Custom Port** - Configurable (default: 8554)
- **Live Logs** - Real-time connection and transfer logs
- **Stats** - Connected clients + data sent
- **Camera Preview** - See what's being streamed
- **Dark Theme** - Clean and easy on the eyes

## Build

```bash
cd CastThor
./gradlew assembleDebug
```

APK at: `app/build/outputs/apk/debug/app-debug.apk`

## Usage

1. Open the app, grant camera permissions
2. Pick codec, resolution, port
3. Tap **Iniciar servidor**
4. Copy the RTSP URL
5. Open in VLC: `rtsp://YOUR_IP:8554/stream`

## Connect from VLC

Media > Open Network Stream > paste URL

## Connect from FFmpeg

```bash
ffplay rtsp://YOUR_IP:8554/stream
```
