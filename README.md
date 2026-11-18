# Stellar Simulcast

An anime release tracker for Android with a neon space look. All show data comes live from AniList's public API, and nothing about shows is saved on the phone. The only thing stored is the list of shows you star.

## Screens
- **Schedule:** every episode airing in the next 7 days, day by day, with live countdowns
- **Just aired:** episodes released in the last 48 hours
- **Season:** last, this and next season's shows, by popularity
- **Search:** find any anime
- **Watchlist:** your starred shows, sorted by which airs next, with an episode alerts switch
- **Details:** next episode countdown, upcoming air dates, official streaming links, synopsis

## Releases
Push to `main` and GitHub Actions builds a signed APK and publishes a release tagged and titled `vX.Y.Z`.
The version comes from `val appVersion` in `app/build.gradle.kts`, so bump it for each update.
