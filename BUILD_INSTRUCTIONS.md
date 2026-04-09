# SPH HelpDesk — Android App Build Instructions

## What this is
A WebView wrapper that loads `https://helpdesk.uphsph.edu.ng` inside a native Android shell.
Students install the APK directly — no Play Store needed.

## Features
- Full helpdesk functionality inside native app
- SPH HelpDesk icon on home screen
- UniPort navy blue splash screen
- Gold progress bar while pages load
- File picker for ticket attachments
- Deep linking — tap helpdesk links in email/WhatsApp → opens in app
- Offline screen if no internet (branded, with Retry button)
- Back button navigates page history

## Build with Android Studio (Recommended)

1. Download and install [Android Studio](https://developer.android.com/studio)
2. Open Android Studio → **Open an existing project** → select this folder
3. Wait for Gradle sync to finish
4. Connect an Android phone OR use the emulator
5. Click **Run ▶** — app installs directly

**To build a release APK:**
1. Build → Generate Signed Bundle/APK → APK
2. Create a keystore (do this once, keep it safe)
3. Select release variant → Finish
4. APK saved to `app/release/SPH-HelpDesk-v1.0.0.apk`

## Build online (no Android Studio needed)

### Option A — GitHub Actions (free)
1. Push this project to a GitHub repo
2. Create `.github/workflows/build.yml`:
```yaml
name: Build APK
on: [push]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with: { java-version: '17', distribution: 'temurin' }
      - run: chmod +x gradlew && ./gradlew assembleDebug
      - uses: actions/upload-artifact@v3
        with:
          name: SPH-HelpDesk-debug
          path: app/build/outputs/apk/debug/*.apk
```
3. Go to Actions tab → download the APK artifact

### Option B — Appetize.io (test in browser)
Upload the APK to https://appetize.io to test before distributing.

## Distribute the APK

**Option 1 — Direct download link**
Upload the APK to your server at:
`https://helpdesk.uphsph.edu.ng/download/SPH-HelpDesk.apk`

Add this to your landing page:
```html
<a href="/download/SPH-HelpDesk.apk" download>
  Download Android App
</a>
```

**Option 2 — WhatsApp / Telegram**
Send the APK file directly to students via WhatsApp or Telegram.

**Option 3 — Google Play Store**
Submit to Play Store for professional distribution (requires $25 one-time developer fee).

## Update the app URL
If your helpdesk moves to a new domain, edit one line in:
`app/src/main/java/ng/edu/uniport/sph/helpdesk/MainActivity.java`

```java
private static final String HELPDESK_URL = "https://helpdesk.uphsph.edu.ng";
```
Change the URL, rebuild, redistribute APK.

## App details
- Package: ng.edu.uniport.sph.helpdesk
- Min Android: 5.0 (API 21) — covers 99%+ of devices in Nigeria
- Target Android: 14 (API 34)
- Size: ~4MB APK
