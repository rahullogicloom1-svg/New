# Calm Reminders

An offline Android reminder app for **drinking water**, **taking medicine**, or anything else.
No account, no internet, no ads. Everything is stored on the phone.

## Features

- **Water, Medicine or Custom** reminders with your own name and note
- **Every X hours** (e.g. every 2 h between 8 AM and 10 PM) **or at exact times**
- **Days:** every day, every other day, or selected weekdays
- **Rings like an alarm clock**: looping alarm sound on the alarm volume + vibration, wakes the screen and opens a full-screen Done / Snooze / Stop page over the lock screen
- **Test alarm** button on the Today screen (rings in 10 seconds)
- **Done / Snooze (10 min)** buttons right on the notification
- **Today screen** with daily progress ("5 of 8 glasses of water") – tap a time to tick it off
- **History & stats**: last-7-days chart, 7-day rate, activity log
- Works with the **app closed** and **after the phone restarts**
- Calm teal design, light and dark mode

## Get the APK

### Option A – build it in the cloud (nothing to install)

1. Create a free GitHub account and a new empty repository.
2. Upload the contents of this folder to it (drag & drop in the browser works).
3. Open the **Actions** tab → **Build APK** → **Run workflow**.
4. After ~5 minutes open the finished run and download **CalmReminders-apk**
   (a zip containing `app-debug.apk`).
5. Copy the APK to your phone.

### Option B – Android Studio

1. Install Android Studio and use **File → Open** on this folder.
2. Wait for Gradle sync, then **Build → Build APK(s)**.
3. The file is at `app/build/outputs/apk/debug/app-debug.apk`.

### Option C – command line

```
./gradlew assembleDebug
```
(needs JDK 17 and the Android SDK, platform 35)

## Installing on the phone

Open the APK on the phone and allow "Install unknown apps" when asked. On first launch, allow
notifications.

## Updating from an older build

Builds made on GitHub are signed with a different temporary key each time, so **uninstall the old
Calm Reminders first**, then install the new APK (old reminders are not carried over).

## Keeping reminders reliable

The app uses exact alarms and re-creates them at boot, but some phone makers aggressively stop
background apps. If a reminder is ever late or missing:

1. Tap **Fix** on the yellow card on the Today screen (battery optimisation → *Don't optimise*).
2. In phone Settings → Apps → Calm Reminders, turn on **Autostart** / **Run in background**
   (Xiaomi, Redmi, Oppo, Vivo, Realme, OnePlus, Samsung all hide this setting in different places).
3. Keep notifications for the "Reminders" channel set to **High / Alert**.

A reminder that was due while the phone was switched off is not replayed; the next one after
start-up fires normally.

## Project layout

```
app/src/main/java/com/calmremind/app/
  Models.kt      data classes + JSON
  Schedule.kt    which doses happen on which day, next-trigger maths
  Store.kt       offline storage
  Alarms.kt      exact alarm scheduling
  Notifier.kt    notification channel + notification
  AlarmService.kt  looping sound, vibration, full-screen notification
  AlarmActivity.kt full-screen ringing page
  Receivers.kt   alarm fired / Done+Snooze+Stop / boot receivers
  AppState.kt    observable UI state
  Screens.kt     Today, Reminders, History
  Editor.kt      create / edit screen
  MainActivity.kt
```
