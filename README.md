# Local Music

An audio-only, dark Android music player for on-device libraries, OpenSubsonic/Navidrome, and a private Cloudflare R2 library exposed through the included read-only Worker.

## MVP

- MediaStore songs with title, artist and album search; album and artist index views.
- Catalog queries run off the UI thread and return pages of at most 200 rows. The UI requests 100 at a time. Artwork is not decoded or cached in memory.
- One Media3/ExoPlayer queue, owned by `MediaSessionService`, with notification, lock-screen, headset and Bluetooth controls.
- Play next, append, inspect/reorder/remove queue items, previous/next, seek, shuffle and repeat controls.
- Local favorites and named playlists, a sleep timer, and a button that opens the Android system equalizer for the active audio session when installed.
- A clear first-run music permission screen with denial recovery and a shortcut to Android app settings.
- Local CSV/M3U playlist import for Spotify, Apple Music, Soundiiz, TuneMyMusic, and similar exports. Tracks are matched locally; encrypted service audio is not copied.
- Private cloud settings keep the Worker token in Android encrypted preferences. R2 credentials are never shipped in the APK.
- Playback queue, position, shuffle and repeat persist across process/service recreation; restored playback starts paused.
- No video, network permission, ads, analytics, tracking or third-party catalog scraping.
- Android target SDK 35, minimum SDK 26.

Cloud deployment files live in `cloudflare/`. The R2 bucket stays private, the Worker provides catalog/search and range streaming, and the app stores a read-only token with platform-backed encryption.

## Build

Use the Gradle wrapper and Android SDK 35. The project uses Android Gradle Plugin 8.7.3 and AndroidX Media3 1.11.1. No GPL source was copied.

## Known MVP limits

Album/artist indexes currently show at most 200 entries, while song/search results page through the library. Selecting an album or artist narrows the song list by its displayed name; duplicate names can therefore mix results. Local playlists store media IDs, with up to 200 tracks restored into a playback queue. There is no artwork rendering, server adapter, offline-download manager, or automated MediaStore change observer yet. Spotify-style Jam remains a possible future local shared-queue feature; the MVP has no always-running server or accounts.
