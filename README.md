# 🛡️ ZenTube — Modern Ad-Free YouTube Client for Android

[![Build Android APK](https://github.com/IceBerg-coder/ads-free-youtube/actions/workflows/build-apk.yml/badge.svg)](https://github.com/IceBerg-coder/ads-free-youtube/actions/workflows/build-apk.yml)
![Platform](https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF?logo=kotlin&logoColor=white)
![UI](https://img.shields.io/badge/UI-Jetpack_Compose_Material_3-4285F4?logo=jetpackcompose&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue.svg)

**ZenTube** is a sleek, modern, and lightweight Android application engineered to provide a distraction-free, 100% ad-free YouTube experience. Built with **Jetpack Compose** and **Material Design 3**, ZenTube strips away video ads, sponsored banners, and promotional nag popups while offering background audio playback and native Picture-in-Picture (PiP).

---

## ✨ Features

- 🚫 **Zero Ads & Trackers:**
  - Network-level domain blocking for Google AdServices, DoubleClick, telemetry, and tracking endpoints.
  - In-player video ad engine that automatically mutes and fast-forwards ads instantly.
  - Automatic clicking of "Skip Ad" buttons.
  - Complete removal of YouTube "Open in App" nags, promoted shelves, and banner overlays.
- 🛡️ **Live Shield Indicator:**
  - Interactive top-bar shield pill showing live counter of blocked ads and trackers in real-time.
- 📱 **Picture-in-Picture (PiP):**
  - Seamless native Android PiP mode (`Rational(16, 9)`). Watch videos in a floating window while multitasking or using other apps.
- 🎵 **Background Audio Playback:**
  - Continuous audio stream that persists when locking the screen or switching between apps.
- 🌙 **AMOLED Pure Black Theme:**
  - Designed for OLED/AMOLED displays to reduce eye strain and maximize battery efficiency.
- 🖥️ **Desktop Mode Toggle:**
  - Instantly switch between mobile and full desktop YouTube layout with a single tap.
- 🔍 **Instant Search & Quick Launcher:**
  - Built-in search bar with quick URL paste and direct search queries.
- 🧭 **Material 3 Navigation:**
  - Bottom navigation bar (Home, Trending, Subscriptions, Library, Control Center).
- 🔄 **Fullscreen Video Controller:**
  - Automatic landscape rotation and immersive sticky fullscreen playback.

---

## 🛠️ Tech Stack & Requirements

| Component | Technology |
|---|---|
| **Language** | Kotlin 2.0+ |
| **UI Framework** | Jetpack Compose + Material Design 3 |
| **Web & Media Engine** | Android WebView with Hardware Acceleration & Custom WebChromeClient |
| **JDK Version** | OpenJDK 21 LTS |
| **Android SDK** | Compile SDK 35 (Android 15), Min SDK 26 (Android 8.0+) |
| **Build Tool** | Gradle 8.9 + Android Gradle Plugin 8.7+ |
| **CI/CD** | GitHub Actions (`ubuntu-latest`) with automatic APK artifact uploads |

---

## 🚀 Automated Cloud Builds (GitHub Actions)

Every push or pull request to the `main` branch automatically triggers the GitHub Actions workflow [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml).

### How to Download the Built APK:
1. Go to the **Actions** tab in this GitHub repository.
2. Select the latest workflow run under **Build Android APK**.
3. Scroll to the bottom to find the **Artifacts** section.
4. Download `ZenTube-Debug-APK` or `ZenTube-Release-APK` and install it on your Android phone!

---

## 💻 Local Build Instructions

If you wish to build the APK locally:

```bash
# Clone the repository
git clone https://github.com/IceBerg-coder/ads-free-youtube.git
cd ads-free-youtube

# Build Debug APK
./gradlew assembleDebug

# The APK will be generated at:
# app/build/outputs/apk/debug/app-debug.apk
```

---

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
