# Local Music

An audio-only, dark Android music player for on-device libraries, OpenSubsonic/Navidrome, and a private Backblaze B2 library.

## MVP

- MediaStore songs with title, artist and album search; album and artist index views.
- Catalog queries run off the UI thread and return pages of at most 200 rows. The UI requests 100 at a time. Artwork is not decoded or cached in memory.
- One Media3/ExoPlayer queue, owned by `MediaSessionService`, with notification, lock-screen, headset and Bluetooth controls.
- Play next, append, inspect/reorder/remove queue items, previous/next, seek, shuffle and repeat controls.
- Local favorites and named playlists, a sleep timer, and a button that opens the Android system equalizer for the active audio session when installed.
- A clear first-run music permission screen with denial recovery and a shortcut to Android app settings.
- Local CSV/M3U playlist import for Spotify, Apple Music, Soundiiz, TuneMyMusic, and similar exports. Tracks are matched locally; encrypted service audio is not copied.
- Backblaze settings keep a bucket-restricted, read-only application key in Android encrypted preferences. Credentials are never shipped in the APK or committed to the repository.
- Backblaze files stream directly to the phone through short-lived download authorization. No laptop or central app server needs to stay online.
- Playback queue, position, shuffle and repeat persist across process/service recreation; restored playback starts paused.
- No video, network permission, ads, analytics, tracking or third-party catalog scraping.
- Android target SDK 35, minimum SDK 26.

Keep the B2 bucket private. Create an application key restricted to the music bucket with `listFiles`, `readFiles`, and `shareFiles`, then enter the key ID, application key, and bucket name in the app. Organizing objects as `Artist/Album/Track.ext` gives the best library labels. Files named `Artist - Track.ext` are also recognized.

## Build

Use the Gradle wrapper and Android SDK 35. The project uses Android Gradle Plugin 8.7.3 and AndroidX Media3 1.5.1. No GPL source was copied.

## Known MVP limits

Album/artist indexes currently show at most 200 entries, while song/search results page through the library. Selecting an album or artist narrows the song list by its displayed name; duplicate names can therefore mix results. Local playlists store media IDs, with up to 200 tracks restored into a playback queue. Backblaze labels currently come from object paths and filenames rather than embedded audio tags. There is no artwork rendering or offline-download manager yet. Spotify-style Jam remains a possible future shared-queue feature.
