# Decay — Android (core pipeline slice)

Hands-free gesture scrolling. This first slice implements the **end-to-end core
loop** only:

```
CameraX (front, headless)
  → ML Kit face detection (on-device eye-open probabilities)
    → BlinkClassifier (long-blink vs natural blink, debounce/cooldown, distance/yaw guards)
      → AccessibilityService.dispatchGesture() (inject an upward swipe = "next")
```

The camera pipeline only runs while a **whitelisted app** (Instagram, TikTok,
YouTube by default) is in the foreground — detected via the accessibility
service's window-change events. Everything is on-device; no frame is stored or
sent anywhere.

## What's in this slice

| Area | File |
|------|------|
| Long-blink state machine | `core/BlinkClassifier.kt` |
| Frame → face signal (ML Kit) | `camera/FaceAnalyzer.kt` |
| Headless camera foreground service | `service/DetectionService.kt` |
| Foreground-app gating + swipe injection | `accessibility/DecayAccessibilityService.kt` |
| Shared state hub | `core/DecayCore.kt` |
| Target-app list (defaults + prefs) | `whitelist/AppWhitelist.kt` |
| Minimal setup + live-status UI | `ui/MainActivity.kt` |

## Not built yet (next slices)

Guided calibration, gesture-mapping screen, whitelist editor UI, floating
overlay bubble, wink/double-blink gestures, stats/wellbeing, full onboarding,
sensitivity slider. The classifier already exposes tunable thresholds
(`longBlinkMs`, `closedThreshold`, etc.) so calibration can drive them later.

## Prerequisites

This machine has **no Android tooling installed** (no JDK, SDK, adb, or Gradle),
so the project can't be built here. Install **Android Studio** (bundles the SDK,
adb, and a JDK 17) on the machine with the Motorola G7 attached.

## Build & deploy to the Motorola G7

1. On the G7: **Settings → About phone → tap Build number 7×** to unlock
   Developer options, then **Settings → System → Developer options → enable USB
   debugging**. Plug in via USB and accept the "Allow USB debugging?" prompt.
2. **Android Studio → Open** → select this `decay` folder. Let Gradle sync
   (it downloads Gradle 8.9 and the dependencies on first run). If it prompts
   about a missing Gradle wrapper, accept generating/using the wrapper.
3. Pick the **G7** in the device dropdown (top toolbar) and press **Run ▶**.
   The app installs and launches on the phone.

Command-line alternative (after `adb` + SDK are on PATH): `.\gradlew assembleDebug`
then `adb install -r app\build\outputs\apk\debug\app-debug.apk`. The Gradle
wrapper JAR isn't checked in — run `gradle wrapper` once (or let Android Studio
generate it) before using `gradlew`.

## First-run setup on the phone

Open Decay and complete the four setup rows:

1. **Camera** — grant when prompted.
2. **Accessibility service** — opens Settings; enable **Decay** under
   Downloaded/Installed apps. (Android requires this to be enabled manually.)
3. **Notifications** — grant (Android shows the camera-active badge).
4. **Display over other apps** — allow. This also grants the exemption that lets
   detection start while another app is already foreground.

Then flip **Detection ON**, switch to Instagram/TikTok/YouTube, and **hold a
blink for ~0.7s** to advance the feed.

## Things to expect (not bugs)

- **Green camera dot**: Android 12+ shows a status-bar dot whenever the camera is
  active. It cannot be hidden — it's a system privacy indicator.
- Detection is intentionally **idle until a whitelisted app is foreground**.
- Default trigger is a fixed ~700ms hold; per-user calibration comes in the next
  slice. Tune `BlinkClassifier` constructor defaults if it feels off meanwhile.

## Tuning knobs (`core/BlinkClassifier.kt`)

| Field | Default | Meaning |
|-------|---------|---------|
| `closedThreshold` | 0.4 | eye-open prob below this = closed |
| `longBlinkMs` | 700 | hold duration that triggers a scroll |
| `cooldownMs` | 1200 | quiet window after a trigger |
| `minFaceWidthFraction` | 0.18 | ignore when phone is too far |
| `maxYawDegrees` | 25 | ignore when head turned away |

Swipe direction/speed live in `DecayAccessibilityService.performScrollNext()`.
