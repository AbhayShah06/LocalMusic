# LocalMusic

A music player for Android. Stream, play your own files, or bring a Navidrome / Backblaze library.

- Live queue, lyrics, favorites and playlists
- Background downloads with retry and offline playback
- Playlist imports from links, exports or pasted song names
- Recently Played, personal mixes and a shared Jam queue

[Get the latest APK](https://github.com/AbhayShah06/LocalMusic/releases/latest)

## Build

JDK 17 + Android SDK 35. Run `./gradlew assemblePublicBuildDebug`.

Work in progress. Streaming depends on upstream services; artist/album browsing is still basic. Downloads are removed when you uninstall. Keep personal credentials and signing keys out of Git.

GPL-3.0-or-later. Uses [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor); not affiliated with the streaming services. Use content you have permission to access and download.
