# Setup guide: Scroll Meter on a new laptop

This guide takes you from a blank laptop to a working Scroll Meter build, and then to the app running on a phone. There are two routes:

- **Route A: install the ready-made APK.** Quickest. No programming tools needed.
- **Route B: build from the source code.** Needed if you want to change the app or rebuild it.

Read [Before you start](#before-you-start) first, whichever route you choose.

---

## Before you start

### What the project needs

| Item | Version | Needed for |
| --- | --- | --- |
| Git | any recent | Cloning the code |
| JDK (Java) | 17 or 21 | Building |
| Android SDK | Platform 35, Build-Tools 35 | Building |
| Gradle | 8.10.2 | Building (downloaded automatically by `gradlew`, nothing to install) |
| A phone | Android 11 or newer | Running the app |
| `adb` (optional) | any | Installing the APK from a computer |

Gradle, the Kotlin compiler and all libraries are downloaded automatically on the first build.

### Files that are NOT in GitHub

The repository deliberately leaves these out. You must bring them to the new laptop yourself:

| File or folder | What it is | How to get it |
| --- | --- | --- |
| `signing/debug.keystore` | The key that signs the APKs | Copy it from the old laptop (USB stick, or a private cloud folder). **Never upload it to GitHub or a public place.** |
| `dist/*.apk` | Ready-made APKs | Download from the GitHub **Releases** page instead |
| `local.properties` | Tells Gradle where the Android SDK is on this laptop | Created on each laptop (Step B4) |
| `app/build/`, `.gradle/` | Build output | Created by the build; never copied |

**Without `signing/debug.keystore`** a new build is signed with a different key. The phone will refuse to update the app that is already installed, and you would have to uninstall it first, which deletes the recorded data. Route A (the release APK from GitHub) does not need the key.

---

## Route A: install the ready-made APK

1. Open the repository on GitHub, click **Releases**, and open **v1.0.0**.
2. Download `ScrollMeter-1.0.0-release.apk` (for everyday use) or `ScrollMeter-1.0.0-debug.apk` (it logs what the accessibility service receives, useful for the first tests).
3. Copy it to the phone and open it, or install it from a computer:

   ```bash
   adb install -r ScrollMeter-1.0.0-release.apk
   ```

4. Go to **Phone setup** below.

---

## Route B: build from the source code

### Step B1: install Git and Java

**macOS** (with [Homebrew](https://brew.sh)):

```bash
brew install git openjdk@21
```

**Windows:** install [Git for Windows](https://git-scm.com/download/win) and [Microsoft OpenJDK 21](https://learn.microsoft.com/java/openjdk/download). Then open a new terminal and check:

```bash
git --version
java -version
```

**Linux (Debian or Ubuntu):**

```bash
sudo apt update && sudo apt install -y git openjdk-21-jdk
```

### Step B2: install the Android SDK

The easiest way is **Android Studio**, which installs everything in this step:

1. Download and install [Android Studio](https://developer.android.com/studio).
2. Open it, go to **More Actions > SDK Manager**.
3. On the **SDK Platforms** tab, tick **Android 15.0 (API 35)**.
4. On the **SDK Tools** tab, tick **Android SDK Build-Tools**, **Android SDK Platform-Tools** and **Android SDK Command-line Tools**. Click **Apply** and accept the licence.

The SDK location is:

| System | Default path |
| --- | --- |
| macOS | `/Users/<you>/Library/Android/sdk` |
| Windows | `C:\Users\<you>\AppData\Local\Android\Sdk` |
| Linux | `/home/<you>/Android/sdk` |

Without Android Studio you can install the same packages from the command line with `sdkmanager`, but the Studio route is much less work.

### Step B3: get the code

Pick the address from the repository's green **Code** button, then:

```bash
git clone https://github.com/<your-github-username>/ScrollMeter.git
cd ScrollMeter
```

### Step B4: copy the signing key

Copy the file `signing/debug.keystore` from the old laptop into the **same folder** on the new one, so the result is:

```
ScrollMeter/
└── signing/
    └── debug.keystore
```

Create the `signing` folder first if it does not exist. Check the file size: it should be about 2.6 KB.

### Step B5: tell Gradle where the SDK is

Create a file called `local.properties` in the `ScrollMeter` folder. If you opened the project in Android Studio, it creates this file for you. Otherwise write it by hand.

**macOS / Linux:**

```properties
sdk.dir=/Users/<you>/Library/Android/sdk
```

**Windows** (note the doubled backslashes):

```properties
sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk
```

This file is git-ignored and must stay on the laptop it was made on.

### Step B6: first build and tests

Run these from inside the `ScrollMeter` folder. The first run downloads Gradle and the libraries, which takes several minutes and needs an internet connection.

**macOS / Linux:**

```bash
chmod +x gradlew
./gradlew testDebugUnitTest
./gradlew assembleDebug assembleRelease
```

**Windows (Command Prompt or PowerShell):**

```bat
gradlew.bat testDebugUnitTest
gradlew.bat assembleDebug assembleRelease
```

Expected results:

- The tests end with `BUILD SUCCESSFUL`. There are 94 tests and none should fail.
- The APKs appear here:

  | File | Path |
  | --- | --- |
  | Debug | `app/build/outputs/apk/debug/app-debug.apk` |
  | Release | `app/build/outputs/apk/release/app-release.apk` |

To open the project in Android Studio instead, choose **File > Open** and select the `ScrollMeter` folder, let it sync, then use **Build > Build APK(s)**.

### Step B7: check the signature

The APK must be signed with the same key as the one already on the phone:

```bash
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
```

`apksigner` is in `<SDK>/build-tools/35.0.0/`. The `SHA-256` line must match the one from the old laptop. Run the same command there to compare.

---

## Testing on the Android Studio emulator

Use this section to try the app on a computer before a phone. The emulator can't reproduce
Realme battery behaviour, so the phone checklist still matters.

### E1. Create the emulator

1. In Android Studio, open **Tools > Device Manager** (or **More Actions > Virtual Device Manager**).
2. Click **Create Virtual Device**, choose **Pixel 6**, then **Next**.
3. Pick the **Android 11 (API 30)** image first, because it is the minimum version the app supports. Click **Download**, then **Next**, then **Finish**.
   - Apple Silicon Mac: choose the **arm64-v8a** image.
   - Intel PC or Mac: choose **x86_64**.
   - To install Facebook or Instagram later, choose an image marked **Google Play**.
4. Press the play button to start it. The first start can take a few minutes.

Hardware acceleration must be on: it is automatic on macOS, needs KVM on Linux, and needs
Windows Hypervisor Platform on Windows.

### E2. Build and run the app

**From Android Studio:** open the `ScrollMeter` folder, wait for Gradle sync, choose the
emulator in the device dropdown, then press **Run ▶**.

**From a terminal** (with the emulator running):

```bash
cd ScrollMeter
./gradlew installDebug                 # Windows: gradlew.bat installDebug
adb devices                            # the emulator must be listed
adb shell am start -n com.scrollmeter.app/.MainActivity
```

### E3. Turn on the permissions

The fastest way is with `adb`:

```bash
# Accessibility service
adb shell settings put secure enabled_accessibility_services com.scrollmeter.app/com.scrollmeter.app.service.ScrollAccessibilityService
adb shell settings put secure accessibility_enabled 1

# Display over other apps (HUD fallback)
adb shell appops set com.scrollmeter.app SYSTEM_ALERT_WINDOW allow
```

On Android 13 and newer you may first need **Settings > Apps > Scroll Meter > ⋮ > Allow
restricted settings**. Then reopen the app. The Setup screen should tick rows as you go.

### E4. What to check

| # | Action | Expected result |
| --- | --- | --- |
| 1 | Launch Scroll Meter | Setup opens with the count, for example "3 of 5 done" |
| 2 | Turn on accessibility, return to the app | The accessibility row ticks |
| 3 | Open Facebook, Instagram or Facebook Lite | Two green counters appear at the top: `0` on the left, `0m` on the right, within about 0.3 s |
| 4 | Swipe down several times | The left counter rises, one stroke per swipe |
| 5 | Swipe **up** | Nothing counts |
| 6 | Go to the home screen | Both counters disappear |
| 7 | Tap near the counters | Taps reach the app underneath |
| 8 | Lock the screen while in the app | Counters disappear and the session ends |
| 9 | Unlock with the app still open | Counters come back |
| 10 | Rotate the emulator (**Ctrl+←** or the rotate button) | Counters move to the new top corners |
| 11 | Open **Today** | Distance and session list show your test scrolling |
| 12 | Open **Day** and **Weekly report** | Numbers match what you scrolled |
| 13 | Settings > Floating counter: change size or colour | The counters restyle at once |

To send a swipe from your computer to any watched app:

```bash
adb shell input swipe 500 1600 500 600 300
```

To see what the service receives, run this in a second terminal:

```bash
adb logcat -s ScrollMeter
```

Each scroll shows its numbers and whether it was counted. Use this when a count looks wrong.

### E5. What the emulator cannot test

- Battery optimisation and background killing (Realme-specific).
- How the real Facebook and Instagram apps report scrolling; these can change without notice.
- Auto-launch after a reboot.

### E6. Quick checklist

- [ ] Emulator runs the **API 30** image.
- [ ] Setup opens and the accessibility row ticks.
- [ ] A swipe in a watched app moves the counter.
- [ ] The counter disappears when you leave the app.
- [ ] `adb logcat -s ScrollMeter` shows scroll events.

---

## Phone setup (Android 11 or newer)

Do this once, on the phone.

1. **Install the app.** Use `adb install -r <file>.apk`, or copy the APK to the phone and open it. You may need to allow "Install unknown apps" for your file manager.
2. **Allow restricted settings (Android 13 and newer).** Open **Settings > Apps > Scroll Meter > ⋮ > Allow restricted settings**. Without this, Android will not let you switch on the accessibility service.
3. **Open Scroll Meter.** The Setup screen opens by itself. Work down the five rows, tapping **Allow** on each:
   - Allow restricted settings
   - Accessibility service (switch on "Scroll Meter")
   - Display over other apps
   - Auto launch
   - Battery: allow background
4. **Lock the app in recents.** Open the recents screen and pull down on the Scroll Meter card (on Realme phones this is the lock option).
5. **Test it.** Open Facebook, swipe through 10 reels, and check that the counter on the left reads about 10.

Menu names differ between phone makers and Android versions. If a step is not where this guide says, search the phone's settings for the name in bold.

---

## Troubleshooting

| Problem | Likely cause and fix |
| --- | --- |
| `SDK location not found` | `local.properties` is missing or the path is wrong (Step B5). |
| `Unsupported class file major version` or `JAVA_HOME` errors | Use JDK 17 or 21 (Step B1). Check with `java -version`. |
| `Failed to install ... INSTALL_FAILED_UPDATE_INCOMPATIBLE` | The phone has the app signed with a different key. Check that `signing/debug.keystore` is the original. Only as a last resort, uninstall the app first (this deletes its data). |
| Gradle download is very slow or fails | Check the internet connection, then run the build again. Gradle resumes where it stopped. |
| `Platform 35 not found` | Install Platform 35 in the SDK Manager (Step B2). |
| The tests fail on the new laptop | Run `./gradlew clean testDebugUnitTest` and send the first error lines to the person who maintains the project. |
| Counter does not appear over Facebook | Check all five Setup rows are ticked, and that Facebook is switched on under **Apps to watch**. |
| Counter stops after a while | Check the battery row is "allowed", and that the app is still locked in recents. |

---

## Working on the project

**Get the latest changes:**

```bash
cd ScrollMeter
git pull
```

**Build and test before changing anything:**

```bash
./gradlew testDebugUnitTest assembleDebug
```

**Where things are:**

| What | Where |
| --- | --- |
| Accessibility service and HUD | `app/src/main/java/com/scrollmeter/app/service/` |
| Counting logic (strokes, metres, sessions) | `app/src/main/java/com/scrollmeter/app/service/ScrollTracker.kt` |
| Screens | `app/src/main/java/com/scrollmeter/app/ui/` |
| Database and settings | `app/src/main/java/com/scrollmeter/app/data/` |
| Tests | `app/src/test/` |
| Requirements and design | `Scroll Meter — Product Requirements.md`, `Scroll Meter — Design Spec.md` |
| Full flow and feature list | `FLOW_AND_FEATURES.md` |

**Before you publish a new version:** change `versionCode` and `versionName` in `app/build.gradle.kts`, run the tests, build both APKs, and attach them to a new GitHub release. Keep the signing key unchanged.

---

## Keeping the signing key safe

- Store a backup of `signing/debug.keystore` in two places (for example, an encrypted USB stick and a password manager's file storage).
- Never commit it, paste it into a chat or email it unencrypted.
- If it is lost, every future build must be installed by uninstalling the app first, which erases the recorded data.
