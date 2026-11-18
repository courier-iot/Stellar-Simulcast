# Stellar Simulcast

An anime release tracker for Android with a neon space look. All show data comes live from AniList's public API. The only things stored on the phone are show IDs (watchlist, hidden shows, episode progress) and settings.

## Tabs
- **Schedule:** the next 7 days, day by day, with live countdowns
- **Just aired:** episodes from the last 48 hours
- **Browse:** Trending, Binge-ready (recently finished), and last/this/next season, plus a random pick
- **Search:** find any anime
- **Watchlist:** starred shows with progress badges and alerts

Show pages have a countdown, break notices, a finish-date estimate, add-to-calendar, a trailer, streaming links, an episode list, cast, related shows and source material, a score trend, sharing (link or QR), and hide.

## Manga
An Anime | Manga switch in the header. Manga mode has Latest, Browse, Search and Library tabs, with an in-app reader for MangaDex chapters (page or scroll mode, right-to-left, zoom, data saver). Reading tracking can be turned off in Settings. Manga data and chapters come live from MangaDex, and every chapter credits its translation group.

## Also in the app
Your own lists (Watching, Plan to watch, On hold, Dropped, Completed) and 1–10 scores, recommendations, studio pages, light novel tracking, manga tag filters, optional offline chapters, two home screen widgets, notification buttons (Mark watched, Read now), app icon shortcuts, a stats page, phone-to-phone transfer, a crash log, and an update checker for this repo's releases.

## Releases
Push to `main` and GitHub Actions builds a signed APK and publishes a release tagged and titled `vX.Y.Z`.
The version comes from `val appVersion` in `app/build.gradle.kts`.

Signing uses four repository secrets (Settings → Secrets and variables → Actions):
`SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.
