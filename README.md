# Local Music

An audio-only, dark Android music player for direct on-device streaming, local libraries, OpenSubsonic/Navidrome, and a private Backblaze B2 library.

## MVP

- MediaStore songs with title, artist and album search; album and artist index views.
- Catalog queries run off the UI thread and return pages of at most 200 rows. Artwork uses bounded memory and disk caches.
- One Media3/ExoPlayer queue, owned by `MediaSessionService`, with notification, lock-screen, headset and Bluetooth controls.
- Play next, append, inspect/reorder/remove queue items, previous/next, seek, shuffle and repeat controls.
- Local favorites and named playlists, a sleep timer, and a button that opens the Android system equalizer for the active audio session when installed.
- A clear first-run music permission screen with denial recovery and a shortcut to Android app settings.
- Local CSV/M3U playlist import for Spotify, Apple Music, Soundiiz, TuneMyMusic, and similar exports. Tracks are matched locally; encrypted service audio is not copied.
- Backblaze settings keep a bucket-restricted, read-only application key in Android encrypted preferences. Credentials are never shipped in the APK or committed to the repository.
- Backblaze files stream directly to the phone through short-lived download authorization. No laptop or central app server needs to stay online.
- A temporary Jam room can be started from the app. Guests join from the no-login web page, search the host's shared catalog metadata, and add tracks to the host's queue. Audio still streams only to the host phone.
- Playback queue, position, shuffle and repeat persist across process/service recreation; restored playback starts paused.
- A bounded 512 MB playback cache reuses recently streamed audio for faster replay, seeking, and lower data use.
- On-demand lyrics, recently played history, and a daily personal mix are cached and generated on the phone.
- No video playback, ads, analytics, tracking, or LocalMusic account.
- Android target SDK 35, minimum SDK 26.

Keep the B2 bucket private. Create an application key restricted to the music bucket with `listFiles`, `readFiles`, and `shareFiles`, then enter the key ID, application key, and bucket name in the public app. The private `personal` build can instead read these values from the ignored `local.private.properties` file. Organizing objects as `Artist/Album/Track.ext` gives the best library labels. Files named `Artist - Track.ext` are also recognized.

Jam uses a static GitHub Pages guest interface and a small Cloudflare Worker backed by a SQLite Durable Object. Rooms expire automatically, contain catalog metadata and queue state only, and never receive music files or Backblaze credentials.

## Build

Use the Gradle wrapper and Android SDK 35. The project uses Android Gradle Plugin 8.7.3, AndroidX Media3 1.5.1, and NewPipe Extractor for on-device catalogue search and stream resolution.

## Online streaming

Online search and playback are resolved directly on the phone through [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor). LocalMusic does not proxy or host the audio. Search favours official audio and Topic uploads, filters clean/radio edits and obvious video/live variants unless requested, and prepares a shuffled related-song queue after playback begins.

## Licence

LocalMusic is distributed under GPL-3.0-or-later because it links NewPipe Extractor. See `LICENSE`. Android, Media3, NewPipe, YouTube, SoundCloud, Backblaze, Navidrome, Spotify, Apple Music, and other names belong to their respective owners. LocalMusic is not affiliated with those services.

## Known limits

Album/artist indexes currently show at most 200 entries, while song/search results page through the library. Selecting an album or artist narrows the song list by its displayed name; duplicate names can therefore mix results. Local playlists store media IDs, with up to 200 tracks restored into a playback queue. Backblaze labels currently come from object paths and filenames rather than embedded audio tags. There is no explicit offline-download manager yet; the playback cache is temporary and automatically evicted. Jam guests can request tracks, but only the Android host controls playback and its phone must remain connected while the room is active.
