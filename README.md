# 31-Band Equalizer for Android

This project implements a 31-band, 1/3 octave equalizer using the `DynamicsProcessing` API.

## Features
- **31 Bands**: Configurable from 20Hz to 20kHz, with a ±12 dB range per band.
- **Global Control**: Attempts to apply EQ globally to Session 0 (on supported devices).
- **Session Tracking**: Automatically hooks into media players (Spotify, YouTube Music) that broadcast `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` to apply the EQ directly to their audio stream.
- **Foreground Service**: Ensures the equalizer stays alive in the background while music is playing.

## How to Build
1. Open this directory (`/home/ekolp/workspace/android-eq`) in **Android Studio**.
2. Sync the Gradle project.
3. Build and Run on your Android device (requires Android 9.0+ / API 28+).
