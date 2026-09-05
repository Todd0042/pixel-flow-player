# Pixel Flow Player 🎬

A modern, fluid, full-screen video player tailored for Google Pixel phones running modern Android.

## Features

- **Stock Album / Folder Selector**: Displays on-device video albums with video thumbnails, total counts, and orientation breakdown (Portrait vs Landscape). Includes a button to browse any directory on the device or external storage using Android's Storage Access Framework.
- **Orientation-Specific Playlists**:
  - Filter applicable videos by **Portrait** (ideal for Shorts, Reels, phone-shot videos) or **Landscape** (for cinematic & widescreen clips).
  - Accurately computes display aspect ratios by combining MediaStore metadata and EXIF/video track rotation (90°, 270°).
- **Custom Play Order**:
  - **Newest First**: Play recent videos first.
  - **Oldest First**: Play in chronological order.
  - **Longest First**: Play the longest videos first.
  - **Random**: Shuffled playlist.
- **Auto-Advance Playback**: Seamlessly transitions to the next video when the current one finishes.
- **Pure Full-Screen Immersive Experience**:
  - Edge-to-edge playback extending into the camera cutout area (`LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`).
  - Uses `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` to avoid persistent system navigation and intrusive fullscreen exit tutorial bubbles.
- **Gesture Controls**:
  - **Orientation-Aware Swipes**:
    - **Portrait mode**: Swipe **Up** for Next video, Swipe **Down** for Previous video (TikTok / Reels style).
    - **Landscape mode**: Swipe **Left** for Next video, Swipe **Right** for Previous video.
  - **Double-Tap**: Cycles scaling & crop modes (**Fit to Screen** ↔ **Crop to Fill / Zoom** ↔ **Stretch to Fill**) with an animated HUD badge and haptic feedback.
  - **Long-Press**: Marks or un-marks the active video as a **Favorite** directly in Google Photos via `MediaStore.createFavoriteRequest` (Android 11+ / Pixel default Photos app integration).
  - **Single-Tap**: Toggles unobtrusive playback controls and scrubber bar, auto-fading after 3.5 seconds.

## Tech Stack

- **Kotlin 2.0.21** + **Jetpack Compose** (Material 3 with Pixel Dynamic Theming)
- **Jetpack Media3 ExoPlayer 1.5.1**
- **Coil 2.7.0** with hardware-accelerated video frame decoding
- **Android MediaStore & Storage Access Framework**

## Installation

Build and install on your Pixel device:

```bash
cd pixel-flow-player
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```
