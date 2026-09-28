# Book your token

A small Android app that reminds PSG Tech hostel students to book tomorrow's food tokens and lets them book in a couple of taps — without opening the portal in a browser.

> **Unofficial.** Not affiliated with or endorsed by PSG College of Technology. It signs in to the hostel portal with *your own* account, exactly like the website does.

<p align="center">
  <img src="docs/screenshots/select.png" width="260" alt="Tomorrow's tokens, with one item selected">
  <img src="docs/screenshots/confirm.png" width="260" alt="Confirming a booking">
</p>

## Features

- **Daily reminder** at a time you choose (default 4:00 PM — bookings close around 5:30 PM the day before). Shows as a pop-up and follows your ringer: sound in ring mode, vibration in vibrate mode, silent in silent mode.
- **Tomorrow's menu at a glance** — every item on offer for tomorrow, with price, available meals and anything you've already booked.
- **Quick booking** — tap items, pick the meal and quantity (capped at the portal's limit), review the total, confirm.
- **Clear results** — each item shows the portal's own response ("Token Booked", "Token apply time has expired", …).
- **Skip when already booked** — optionally no reminder on days you've already booked.

## Safety rules the app follows

- **Nothing is ever booked without you tapping _Book now_.** No auto-booking, no booking from the notification.
- **Credentials stay on your phone**, in `EncryptedSharedPreferences`. The password is only ever sent in the portal's own login request — never logged or sent anywhere else.
- **Gentle on the portal**: one background check per day, bookings sent one at a time, no polling.
- **No blind retries**: if a booking request times out, the app re-reads your bookings to see whether it registered before telling you anything.

## How it talks to the portal

There's no public API, so the app does what the website does, using the same endpoints on `edviewx.psgtech.ac.in`:

| Step | Request | Purpose |
|---|---|---|
| 1 | `GET /Hostel` | Get session cookies |
| 2 | `POST /Hostel/Login/Authenticate` | Sign in (roll number **must be uppercase**) |
| 3 | `GET /Hostel/Student/StudentView` | The booking page — parsed with Jsoup for items, dates and meals |
| 4 | `POST /Hostel/Student/StudentGetToken` | Tokens you've already booked (JSON) |
| 5 | `POST /Hostel/Student/newStudentTokenApply` | Book one item |

Two things learned the hard way:

- **The login expires after 10 minutes** and can't be refreshed, so the app signs in fresh for every operation instead of keeping a session around.
- **Booking only works in a session that has already loaded steps 3 and 4.** The portal appears to set up per-session state (your balance, mess id) when those pages load; skipping them makes every booking fail with "The remaining balance is required to be paid." even when your balance is fine.

Item IDs and quantity limits aren't in the page HTML — they come from the site's JavaScript and are kept in [`TokenPageParser.kt`](app/src/main/java/com/example/bookyourtoken/data/TokenPageParser.kt). If the portal changes, that parser is the first place to look; the app shows a "portal may have changed" error with a link to book in the browser instead of an empty list.

## Building

Requirements: a recent Android Studio with Android SDK Platform 37 installed (the project uses AGP 9.4 / Gradle 9.6; minSdk 26).

```bash
git clone <your-fork-url>
cd book-your-token
./gradlew assembleDebug          # APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest      # parser, formatting and selection tests
```

Or open the folder in Android Studio and press **Run**.

On first launch, sign in with your hostel portal roll number and password, pick a reminder time, and allow notifications.

## Project structure

```
app/src/main/java/com/example/bookyourtoken/
├── data/
│   ├── HostelClient.kt        OkHttp client, one session per operation
│   ├── TokenPageParser.kt     Jsoup parsing of the booking page (pure, unit tested)
│   ├── CredentialStore.kt     EncryptedSharedPreferences wrapper
│   ├── AppPreferences.kt      Reminder time and toggles
│   ├── DateUtils.kt           "Tomorrow" in Asia/Kolkata
│   └── models/                TokenItem, BookedToken, BookResult, oresult messages
├── ui/
│   ├── setup/                 Sign-in screen
│   ├── tokens/                Main screen, selection logic, ViewModel
│   ├── booking/               Booking progress dialog
│   ├── settings/              Settings screen
│   ├── common/                Shared components and formatting
│   └── theme/                 Colours and typography
└── work/
    ├── ReminderWorker.kt      Daily check → notification
    ├── ReminderScheduler.kt   Self-rescheduling one-shot WorkManager job
    ├── NotificationHelper.kt  High-importance channel (pop-up + vibration)
    └── BootReceiver.kt        Re-schedules the reminder after a reboot
```

Built with Kotlin, Jetpack Compose (Material 3), OkHttp, Jsoup, WorkManager and AndroidX Security.
