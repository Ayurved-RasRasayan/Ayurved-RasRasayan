# Forex News Alarm

An Android app that alerts you **9 minutes before every high-impact (red folder) economic news event**.

## Features

- Scrapes **Forex Factory** and **Investing.com** for red-folder / high-impact events.
- No currency filter  every high-impact event gets an alert.
- Converts all event times to **Asia/Kathmandu (Nepal Time, UTC+5:45)**.
- Schedules **exact alarms** using Android's `AlarmManager` with `setExactAndAllowWhileIdle`.
- Re-schedules automatically after device reboot.
- Daily background refresh via WorkManager.

## How to Build

1. Fork or clone this repository.
2. Push to GitHub  GitHub Actions automatically builds the APK.
3. Go to the **Actions** tab, download the APK artifact.
4. Install the APK on your Android device.

## Permissions Required

- `SCHEDULE_EXACT_ALARM`  for precise 9-minute alerts (Android 12+).
- `POST_NOTIFICATIONS`  to show alerts (Android 13+).
- `RECEIVE_BOOT_COMPLETED`  to re-schedule alarms after reboot.
- `INTERNET`  to fetch calendar data.

## Notes

- Forex Factory and Investing.com do not have public APIs. This app uses HTML scraping.
- The scrapers are isolated in `CalendarScraper.kt` so you can update selectors if the site HTML changes.
- Nepal does not observe DST, but the app uses the IANA `Asia/Kathmandu` zone ID for correctness.
