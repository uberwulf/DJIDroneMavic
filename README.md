# Air Pilot

A clean, ad-free Android flight app for DJI drones, built on DJI Mobile SDK v4 / UX SDK 4.16.2
(the same SDK DJI Go Fly uses). Package name: `com.saulls.airpilot`.

The app opens to a start screen that registers with DJI and waits for the drone. When the drone connects it
goes straight to the fly screen: live video, telemetry, takeoff/land, return home and camera controls.

## Features

- **Simple mode (default):** live video, a big altitude / distance / speed / flight-time readout,
  takeoff, return home, shutter and the status bar. Nothing else in the way.
- **Pro mode:** one tap on the SIMPLE/PRO pill brings back ISO, shutter, aperture, EV, white balance,
  focus, lens controls and DJI's full dashboard.
- **Flight log:** every flight is recorded automatically (time, duration, max height, distance, speed).
  Tap a flight to open its takeoff spot in Maps.
- **Safety & Settings:** beginner mode, max altitude, distance limit, return-to-home altitude and
  low-battery warning, saved straight to the drone. Plus units (ft/m), grid lines and default mode.
- **Photos & Videos:** browse the drone's SD card, tap "Select new" and download to the phone's
  gallery (album "Air Pilot"). Already-saved files are marked so you don't download twice.
- **Smart moves:** Orbit (circle what the camera is pointing at) and Dronie (fly back and up while
  filming). Any stick movement, Pause or Return-to-Home cancels instantly; needs strong GPS.
- **ActiveTrack (DJI's own vision tracking):** draw a box around a person and the drone follows
  (behind or alongside), keeps the camera on them (Spotlight), or flies a QuickShot around them:
  Circle, Helix, Rocket, Dronie, Boomerang, Asteroid.
- **Auto-download after landing:** new photos and videos from the flight come straight to the phone.
- **Google Drive sync:** downloaded media (and flight logs) upload to a Drive folder in the background.
- Screen stays awake while the fly screen is open.

Mockups (drawn from the layouts, not real device screenshots):

| Simple mode | Pro mode |
|---|---|
| ![Simple](docs/mockups/2-fly-simple-mode.png) | ![Pro](docs/mockups/3-fly-pro-mode.png) |

More in [docs/mockups](docs/mockups).

## Roof scan missions

Plan on a computer with the **Air Pilot Planner** (https://uberwulf.github.io/DJIDroneMavic/),
which you can install as an app from Edge or Chrome.

- **Roof face sweep:** click the two ends of a roof's lower edge; the drone flies side to side in rows,
  stepping up, with the camera aimed at the tiles.
- **Orbit:** click the house corners; the drone circles (one or two loops) with the camera on the roof.

Send the plan with **Send to phone** (scan the QR code) or **Download plan file** into Google Drive
and use **Missions → Import** on the phone. In the air, tap **Start mission**. Moving a stick or
tapping STOP takes over. Photos are filed under the date and the mission name.

The planner site is published by the "Publish planner website" workflow. It needs GitHub Pages
turned on once: repo **Settings → Pages → Build and deployment → Source: GitHub Actions**.

## One-time setup

1. **DJI App Key.** At developer.dji.com go to Apps, then Create App. Choose Mobile SDK and enter the package
   name `com.saulls.airpilot` exactly. Copy the key.
2. **Add it to GitHub.** In this repo go to Settings → Secrets and variables → Actions → New repository secret.
   Name it `DJI_API_KEY` and paste the key. The key never goes in the code.

## Building

Every push builds automatically. You can also open Actions → **Build Air Pilot APK** → Run workflow.
When the run finishes, download **AirPilot-debug-apk** from the bottom of the run page.

## Installing on the phone

**Phone download link (always the newest build):**
https://github.com/uberwulf/DJIDroneMavic/releases/latest/download/AirPilot.apk

1. Open that link in Chrome on the phone, then open the downloaded `AirPilot.apk` and allow installing if asked.
2. The phone needs internet the first time the app opens, so it can register with DJI.
3. Plug the phone into the controller, power on the controller and then the drone.
   The app jumps to the fly screen once the drone connects. The **FLY** button opens it any time.

New builds install over the old one, so your settings are kept.

## Local build (optional)

Use JDK 11 and the Android SDK. Put `DJI_API_KEY=yourkey` in `local.properties`, then run `./gradlew assembleDebug`.

## Google Drive sync setup (one time, about 10 minutes, on a PC)

1. Go to https://console.cloud.google.com and create a project called **Air Pilot**.
2. **APIs & Services → Library**: search for **Google Drive API** and click **Enable**.
3. **APIs & Services → OAuth consent screen** (or "Google Auth Platform"): choose **External**, enter
   the app name *Air Pilot* and your email, and save. Under **Audience / Test users** add your Gmail
   address. Then click **Publish app** so access doesn't expire every 7 days. Air Pilot only asks for
   the `drive.file` permission, so Google doesn't need to review it.
4. **APIs & Services → Credentials → Create credentials → OAuth client ID**:
   - Application type: **Android**
   - Package name: `com.saulls.airpilot`
   - SHA-1 certificate fingerprint: `45:24:A7:0E:6F:6E:EF:51:66:DB:FF:50:68:2A:4E:C4:FB:C9:9E:6A`
5. On the phone: **Safety & Settings → Google Drive sync → Sign in with Google**, pick your account and allow access.

Air Pilot creates the Drive folder itself (default *Air Pilot*). With `drive.file` it can only see files
it created, never the rest of your Drive.
