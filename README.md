# 31-Band Equalizer for Android

This project implements a 31-band, 1/3 octave equalizer using the `DynamicsProcessing` API.

## Features
- **31 Bands**: Configurable from 20Hz to 20kHz, with a ±12 dB range per band.
- **Preamp & Auto-Headroom**: Master input gain slider (-12 dB to +12 dB) with an optional Auto-Headroom toggle that automatically lowers preamp gain to prevent digital clipping when bands are boosted above 0 dBFS.
- **Peak Limiter (Anti-Clipping)**: Integrated `DynamicsProcessing.Limiter` brickwall peak stage preventing audio distortion.
- **Gesture "Draw Curve" Mode**: Quickly swipe your finger across the equalizer to sculpt curves across all 31 bands in one continuous motion, complete with real-time linear interpolation and edge auto-scrolling.
- **Curve Smoothing**: One-tap 3-point weighted FIR filter to soften hand-drawn curves into smooth acoustic profiles.
- **Global Control**: Attempts to apply EQ globally to Session 0 (on supported devices).
- **Session Tracking**: Automatically hooks into media players (Spotify, YouTube Music) that broadcast `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` to apply the EQ directly to their audio stream.
- **Preset Management**: Built-in genre presets plus ability to save, rename, and delete custom presets.
- **Foreground Service**: Ensures the equalizer stays alive in the background while music is playing.

## How to Build
1. Open this directory (`/home/ekolp/workspace/android-eq`) in **Android Studio**.
2. Sync the Gradle project.
3. Build and Run on your Android device (requires Android 9.0+ / API 28+).
