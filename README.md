# Stellar Simulcast

An anime release tracker for Android with a neon space look. All show data comes live from AniList's public API. The only things stored on the phone are show IDs (watchlist, hidden shows, episode progress) and settings.

## Tabs
- **Schedule:** the next 7 days, day by day, with live countdowns
- **Just aired:** episodes from the last 48 hours
- **Browse:** Trending, Binge-ready (recently finished), and last/this/next season, plus a random pick
- **Search:** find any anime
- **Watchlist:** starred shows with progress badges and alerts

Show pages have a countdown, break notices, a finish-date estimate, add-to-calendar, a trailer, streaming links, an episode list, cast, related shows and source material, a score trend, sharing (link or QR), and hide.

## Releases
Push to `main` and GitHub Actions builds a signed APK and publishes a release tagged and titled `vX.Y.Z`.
The version comes from `val appVersion` in `app/build.gradle.kts`.

Signing uses four repository secrets (Settings → Secrets and variables → Actions):
`SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.
